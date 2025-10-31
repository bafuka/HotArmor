# HotArmor 架构设计文档

## 📋 目录

- [设计背景](#设计背景)
- [核心理念](#核心理念)
- [系统架构](#系统架构)
- [技术选型](#技术选型)
- [核心流程](#核心流程)
- [模块说明](#模块说明)

---

## 🎯 设计背景

### 业务痛点

在高并发场景下，热点数据问题是一个常见的技术挑战：

**缓存击穿**
- 热点 Key 失效瞬间，大量请求直达数据库
- 数据库 CPU 飙升，可能导致雪崩

**Redis 瓶颈**
- 少数热点 Key 占用大量 Redis 连接
- 网络带宽成为瓶颈（热点 Key 可能占用 80%+ 流量）

**响应时间**
- 即使 Redis 命中，网络 IO 也需要 1-5ms
- 高频访问累积的网络开销显著

**缓存一致性**
- 分布式环境下，各节点本地缓存难以保持一致
- Cache-Aside 模式存在时序问题

### 设计目标

HotArmor 旨在提供：
- **自动化**：热点自动识别、自动晋升、自动失效
- **高性能**：本地缓存微秒级响应，极致性能
- **最终一致**：分布式一致性保证
- **易使用**：注解声明，零代码侵入

---

## 💡 核心理念

### 四级漏斗防护

HotArmor 采用 **四级漏斗** 架构，逐层过滤和优化：

```
               所有请求 (100%)
                    ↓
        ┌────────────────────────┐
        │   L1: Caffeine 本地缓存  │   90%+ 命中
        │    (微秒级响应)          │   ← 极速返回
        └────────────────────────┘
                    ↓ 10% Miss
        ┌────────────────────────┐
        │   L2: 噪音过滤器         │   过滤 80%+ 冷数据
        │    (访问计数器)          │   ← 减少无效探测
        └────────────────────────┘
                    ↓ 20% Pass
        ┌────────────────────────┐
        │   L3: Sentinel 热点探测  │   识别热点
        │    (QPS 阈值判定)        │   ← 精准判定
        └────────────────────────┘
                    ↓ 热点触发
        ┌────────────────────────┐
        │   L4: Redis + 分布式锁   │   防缓存击穿
        │    (安全回源)            │   ← 单点加载
        └────────────────────────┘
                    ↓
             数据库/服务
```

### 设计原则

1. **最小化网络 IO**：热点数据留在本地，避免 Redis 网络开销
2. **分层过滤**：逐层过滤，避免无效计算和探测
3. **自适应**：根据实际访问模式自动调整
4. **一致性优先**：最终一致性保证，避免脏数据

---

## 🏗️ 系统架构

### 整体架构图

```
┌─────────────────────────────────────────────────┐
│                 业务应用层                       │
│     @HotArmorCache    @HotArmorEvict            │
└──────────────────┬──────────────────────────────┘
                   │ AOP 拦截
                   ↓
┌──────────────────────────────────────────────────┐
│            HotArmor 核心引擎                      │
│                                                  │
│  ┌────────────────────────────────────────────┐ │
│  │  SpEL 表达式解析 + 规则管理器              │ │
│  └────────────────────────────────────────────┘ │
│                   ↓                              │
│  ┌────────────────────────────────────────────┐ │
│  │          数据平面 (L1-L4)                  │ │
│  │  L1 (Caffeine) → L2 (Filter)              │ │
│  │  → L3 (Sentinel) → L4 (Redis + Lock)      │ │
│  └────────────────────────────────────────────┘ │
│                   ↓                              │
│  ┌────────────────────────────────────────────┐ │
│  │          一致性引擎                         │ │
│  │  • Redis Pub/Sub (广播)                    │ │
│  │  • RocketMQ (延迟双删)                     │ │
│  └────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────┘
```

---

## 🔧 技术选型

| 组件 | 选择 | 理由 |
|------|------|------|
| **L1 本地缓存** | Caffeine | 性能最优，比 Guava Cache 快 3-5 倍 |
| **L3 热点探测** | Sentinel | 原生支持参数级热点探测 |
| **L4 分布式锁** | Redisson | 开箱即用，自动续约 |
| **消息队列** | RocketMQ | 支持延迟消息 |
| **配置中心** | Nacos | 动态配置，实时生效 |

---

## 🔄 核心流程

### 读取流程

```java
1. 查询 L1 本地缓存
   命中 → 返回 ✅ (微秒级)

2. 未命中 → L2 噪音过滤
   访问频率 < 阈值 → 直接回源 (冷数据)

3. 访问频率 ≥ 阈值 → L3 热点探测
   QPS < 阈值 → 正常回源

4. QPS ≥ 阈值 (热点！) → L4 安全回源
   获取分布式锁
   → 加载数据
   → 晋升到 L1
   → 返回 ✅
```

### 写入流程

```java
1. 更新数据库

2. 删除 L1 本地缓存

3. 删除 Redis 缓存

4. 发送 Pub/Sub 广播
   → 通知所有节点清理 L1

5. (可选) 发送延迟消息
   → 5秒后再次删除 Redis
```

---

## 📦 模块说明

### 核心模块结构

```
hotarmor-core/
├── annotation/          # @HotArmorCache, @HotArmorEvict
├── aspect/              # AOP 切面
├── dataplane/           # L1-L4 引擎实现
│   ├── impl/
│   │   ├── CaffeineL1CacheEngine
│   │   ├── CaffeineL2NoiseFilter
│   │   ├── SentinelL3HotspotDetector
│   │   └── RedissonL4SafeLoader
├── consistency/         # 一致性引擎
│   ├── BroadcastNotifier
│   └── DelayedDeleteProducer
├── control/             # 规则管理器
├── model/               # HotArmorRule 配置模型
├── spel/                # SpEL 表达式解析
└── autoconfigure/       # Spring Boot 自动配置
```

### 示例模块结构

```
hotarmor-example/
├── entity/              # User, Product 实体
├── mapper/              # MyBatis Mapper
├── service/             # 业务服务 (使用 @HotArmorCache)
├── controller/          # REST API
└── resources/
    ├── application.yml  # 配置示例
    └── logback-spring.xml
```

---

## ⚡ 性能优化

### 1. 减少锁竞争

- L1、L2 层预先过滤 90%+ 请求
- 只有热点才进入 L4
- 实际锁竞争 < 1%

### 2. 异步通知

```java
@Async
public void notifyInvalidate(String resource, String key) {
    // 异步发送，不阻塞主流程
}
```

### 3. 内存优化

- W-TinyLFU 算法自动淘汰低频数据
- 配置 maximumSize 和 TTL

---

## 🔌 扩展性设计

### SPI 机制

支持自定义配置源：

```java
public interface ConfigSource {
    List<HotArmorRule> loadRules();
    void addListener(ConfigChangeListener listener);
}

// 实现自定义配置源（Apollo、Etcd 等）
public class ApolloConfigSource implements ConfigSource {
    // ...
}
```

### 自定义实现

所有核心组件都可以自定义：

```java
@Component
public class MyL1CacheEngine implements L1CacheEngine {
    // 自定义实现
}
```

---

## 📊 监控指标

推荐上报的指标：

- `hotarmor.l1.hit_rate` - L1 缓存命中率
- `hotarmor.l3.hotspot_count` - 热点触发次数
- `hotarmor.l4.lock_wait_time` - 锁等待时间
- `hotarmor.consistency.broadcast_count` - 广播次数

---

## 🔐 安全性考虑

### 缓存雪崩防护

- TTL 随机化
- 分布式锁防止击穿

### 缓存穿透防护

- L2 噪音过滤器
- 空值缓存（可选）

### 缓存污染防护

- W-TinyLFU 算法
- maximumSize 限制

---

<div align="center">

**[返回主文档](../README.md)**
</div>
