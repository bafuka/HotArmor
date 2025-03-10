<div align="center">

[English](README.md) | [简体中文](README-ZH.md)

</div>

---

<div align="center">

# HotArmor 🛡️

**智能热点数据防护框架 · 解决高并发缓存击穿难题**

*一行注解，让热点数据自动晋升本地缓存*

[![Java](https://img.shields.io/badge/Java-1.8+-orange.svg)](https://www.oracle.com/java/technologies/javase-downloads.html)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.3.12-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Sentinel](https://img.shields.io/badge/Sentinel-1.8.6-blue.svg)](https://sentinelguard.io/)
[![License](https://img.shields.io/badge/license-Apache%202-blue.svg)](LICENSE)

</div>

---

## 💡 它解决什么问题？

**HotArmor（热铠）** 是一款专为高并发场景设计的热点数据防护中间件。在电商秒杀、热点事件等场景下，少数热点数据（如爆款商品、热门话题）会带来严重的性能问题：

| 问题 | 典型场景 | 技术影响 | HotArmor 方案 |
|------|---------|----------|---------------|
| ⚡ **缓存击穿** | 热点 Key 失效瞬间 | 数千请求同时打穿缓存直达数据库，DB 瞬间被打垮 | L4 分布式锁 + Double-Check，确保单点回源 |
| 🔥 **热点过载** | 明星商品被高频访问 | Redis 连接池耗尽、网络带宽占满、响应变慢 | L1-L3 智能识别热点并晋升到本地缓存（微秒级） |
| 🔄 **分布式缓存一致性** | 集群多节点部署 | 节点 A 更新数据，节点 B/C/D 本地缓存未失效产生脏读 | Pub/Sub 失效广播 + 热点晋升广播，全节点同步 |
| 🗑️ **DB-Cache 一致性** | 高并发读写竞态 | 更新 DB 后删除缓存，并发查询将旧数据回写 Redis | 延迟双删策略，消除写后读竞态窗口期 |

## 🚀 核心特性

```java
// 只需一行注解，框架自动处理热点防护
@HotArmorCache(resource = "product:detail", key = "#id")
public Product getProduct(Long id) {
    return productMapper.selectById(id);  // 热点数据自动晋升本地缓存
}
```

- ✨ **开箱即用** - 注解声明式使用，零侵入接入
- 🧠 **智能识别** - 基于 Sentinel 的热点参数流控，精准识别热点数据
- 🔄 **四级防护** - L1 本地缓存 → L2 噪音过滤 → L3 热点探测 → L4 安全回源
- 📡 **最终一致性** - 延迟双删 + Redis Pub/Sub 广播，保证集群缓存同步
- ⚡ **极致性能** - 热点数据从 Redis 毫秒级 → Caffeine 微秒级响应

---

## 🏗️ 架构设计

### 系统架构

```
┌─────────────────────────────────────────────────────────────────────┐
│                          业务应用层                                   │
│                                                                     │
│    @HotArmorCache(resource="...", key="...")  ← 查询缓存            │
│    @HotArmorEvict(resource="...", key="...")  ← 失效缓存            │
└──────────────────────────┬──────────────────────────────────────────┘
                           │
                           │ AOP 拦截
                           ↓
┌─────────────────────────────────────────────────────────────────────┐
│                      HotArmor 核心引擎                                │
│                                                                     │
│  ┌───────────────────────────────────────────────────────────────┐ │
│  │                    切面层 (Aspect)                             │ │
│  │  • HotArmorAspect - AOP 拦截器                                │ │
│  │  • SpEL 表达式解析器 - 解析 key/condition                      │ │
│  │  • HotArmorContext - 请求上下文对象                            │ │
│  └─────────────────────────┬─────────────────────────────────────┘ │
│                            ↓                                        │
│  ┌───────────────────────────────────────────────────────────────┐ │
│  │                 处理器层 (Handler)                             │ │
│  │  • DefaultHotArmorAspectHandler                               │ │
│  │    - handleCache() : 读流程四级漏斗                            │ │
│  │    - handleEvict() : 写流程缓存失效                            │ │
│  └─────────────────────────┬─────────────────────────────────────┘ │
│                            ↓                                        │
│  ┌───────────────────────────────────────────────────────────────┐ │
│  │                  数据平面 (Data Plane)                         │ │
│  │                                                               │ │
│  │  ┌─────────────────────────────────────────────────────────┐ │ │
│  │  │ L1: 本地缓存层 (CaffeineL1CacheEngine)                    │ │ │
│  │  │  • Caffeine 高性能本地缓存                               │ │ │
│  │  │  • 微秒级响应 (~1μs)                                     │ │ │
│  │  │  • 独立配置容量和过期时间                                 │ │ │
│  │  └─────────────────────────────────────────────────────────┘ │ │
│  │                            ↓ Miss                             │ │
│  │  ┌─────────────────────────────────────────────────────────┐ │ │
│  │  │ L2: 噪音过滤层 (CaffeineL2NoiseFilter)                    │ │ │
│  │  │  • 轻量级访问计数器                                       │ │ │
│  │  │  • 过滤冷数据 (访问频率 < 阈值)                           │ │ │
│  │  │  • 保护 Sentinel 资源                                    │ │ │
│  │  └─────────────────────────────────────────────────────────┘ │ │
│  │                            ↓ Pass                             │ │
│  │  ┌─────────────────────────────────────────────────────────┐ │ │
│  │  │ L3: 热点探测层 (SentinelL3HotspotDetector)               │ │ │
│  │  │  • Sentinel 参数流控                                     │ │ │
│  │  │  • 热点判定 (QPS > 阈值)                                 │ │ │
│  │  │  • 自动触发热点晋升                                       │ │ │
│  │  └─────────────────────────────────────────────────────────┘ │ │
│  │                            ↓ Block (热点！)                   │ │
│  │  ┌─────────────────────────────────────────────────────────┐ │ │
│  │  │ L4: 安全回源层 (RedissonL4SafeLoader)                    │ │ │
│  │  │  • Redisson 分布式锁                                     │ │ │
│  │  │  • Double-Check 机制                                    │ │ │
│  │  │  • 重试 + 指数退避                                       │ │ │
│  │  │  • 防止缓存击穿                                          │ │ │
│  │  └─────────────────────────────────────────────────────────┘ │ │
│  └───────────────────────────────────────────────────────────────┘ │
│                                                                     │
│  ┌───────────────────────────────────────────────────────────────┐ │
│  │                 控制平面 (Control Plane)                       │ │
│  │                                                               │ │
│  │  • DefaultRuleManager - 规则管理器                            │ │
│  │    - loadRules() : 加载规则配置                               │ │
│  │    - updateRules() : 动态更新规则                             │ │
│  │    - validateRule() : 规则验证                                │ │
│  │  • HotArmorRule - 规则模型 (L1-L4 配置)                       │ │
│  └───────────────────────────────────────────────────────────────┘ │
│                                                                     │
│  ┌───────────────────────────────────────────────────────────────┐ │
│  │                一致性引擎 (Consistency Engine)                 │ │
│  │                                                               │ │
│  │  • DefaultConsistencyManager - 一致性管理器                   │ │
│  │    - handleUpdate() : 处理缓存更新                            │ │
│  │    - handlePromotion() : 处理热点晋升广播                     │ │
│  │    - invalidateCache() : 立即删除缓存                         │ │
│  │  • RedisBroadcastNotifier - Redis Pub/Sub 广播               │ │
│  │    - 缓存失效广播                                             │ │
│  │    - 热点晋升广播 (NEW)                                       │ │
│  │  • DelayedDeleteProducer - 延迟双删 (RocketMQ)               │ │
│  └───────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────┘
                           │
          ┌────────────────┼────────────────┐
          │                │                │
          ↓                ↓                ↓
┌──────────────────┐ ┌────────────┐ ┌─────────────────┐
│  Redis           │ │  RocketMQ  │ │  数据库          │
│  • 二级缓存       │ │  • 延迟消息 │ │  • MySQL        │
│  • Pub/Sub 广播  │ │  (可选)     │ │  • PostgreSQL   │
│  • 分布式锁       │ └────────────┘ │  等              │
└──────────────────┘                └─────────────────┘

───────────────────────────────────────────────────────────────────────
数据流向说明：

读流程：
  用户请求 → AOP拦截 → L1本地缓存 → L2噪音过滤 → L3热点探测
  → L4安全回源 → Redis/DB → 晋升L1(热点) → 发送晋升广播
  → 其他节点收到广播并晋升L1 → 返回

写流程：
  用户请求 → AOP拦截 → 更新DB → 删除L1+Redis → 发送失效广播
  → 其他节点清L1 → 延迟双删(可选) → 完成
───────────────────────────────────────────────────────────────────────
```

### 核心流程

#### 读取流程（四级漏斗）

```
                          用户请求
                             ↓
        ┌─────────────────────────────────────────┐
        │     @HotArmorCache 注解方法              │
        └────────────┬────────────────────────────┘
                     ↓
        ┌─────────────────────────────────────────┐
        │    HotArmorAspect.aroundCache()         │
        │  • 解析 SpEL key 表达式                  │
        │  • 解析 condition 条件                   │
        │  • 构建 HotArmorContext                 │
        └────────────┬────────────────────────────┘
                     ↓
        ┌─────────────────────────────────────────┐
        │  DefaultHotArmorAspectHandler           │
        │       .handleCache()                    │
        └────────────┬────────────────────────────┘
                     ↓
                     │
        ┌────────────▼───────────────────────────┐
        │ L1: CaffeineL1CacheEngine.get()        │
        │     检查本地缓存                        │
        └────────────┬───────────────────────────┘
                     │
         ┌───────────┴────────────┐
         │                        │
      缓存命中                  缓存未命中
         │                        │
         ↓                        ↓
    返回数据 ✅        ┌─────────────────────────┐
    (~1μs)            │ L2: L2NoiseFilter       │
                      │    .shouldPass()        │
                      │  • 计数器检查访问频率    │
                      └──────────┬──────────────┘
                                 │
                     ┌───────────┴────────────┐
                     │                        │
              访问频率 < 阈值            访问频率 >= 阈值
               (冷数据)                    (热数据)
                     │                        │
                     ↓                        ↓
            直接回源加载              ┌────────────────────┐
          (不占用Sentinel)            │ L3: SentinelL3     │
                     │                │   .isHotspot()     │
                     │                │  • Sentinel热点检测│
                     │                └──────┬─────────────┘
                     │                       │
                     │           ┌───────────┴────────────┐
                     │           │                        │
                     │      QPS < 阈值                 QPS >= 阈值
                     │      (非热点)                    (热点！)
                     │           │                        │
                     └───────────┼────────────────────────┘
                                 ↓
                     ┌───────────────────────────────────┐
                     │ L4: RedissonL4SafeLoader.load()   │
                     │         安全回源                   │
                     └────────────┬──────────────────────┘
                                  ↓
                     ┌────────────────────────────────────┐
                     │  1. getFromRedis()                 │
                     │     先查 Redis 缓存                 │
                     └────────────┬───────────────────────┘
                                  │
                      ┌───────────┴────────────┐
                      │                        │
                  Redis 命中                Redis 未命中
                      │                        │
                      ↓                        ↓
                 返回数据 ✅         ┌────────────────────┐
                                    │  2. 获取分布式锁    │
                                    │  redissonClient    │
                                    │    .getLock()      │
                                    └──────┬─────────────┘
                                           │
                               ┌───────────┴────────────┐
                               │                        │
                          获取锁成功                 获取锁失败
                               │                        │
                               ↓                        ↓
                  ┌──────────────────────┐    ┌──────────────────┐
                  │ 3. Double-Check      │    │ 重试机制          │
                  │    再次查Redis        │    │ • 等待 + 重试     │
                  └──────┬───────────────┘    │ • 指数退避        │
                         │                    │ • 最终降级查DB    │
                    ┌────┴─────┐              └────────┬─────────┘
                    │          │                       │
                  命中      未命中                      │
                    │          │                       │
                    │          ↓                       │
                    │   ┌──────────────┐              │
                    │   │ 4. 查询数据库 │              │
                    │   │  joinPoint   │              │
                    │   │  .proceed()  │              │
                    │   └──────┬───────┘              │
                    │          │                       │
                    │          ↓                       │
                    │   ┌──────────────┐              │
                    │   │ 5. 回写Redis  │              │
                    │   │ putToRedis() │              │
                    │   └──────┬───────┘              │
                    │          │                       │
                    └──────────┴───────────────────────┘
                               ↓
                      ┌──────────────────┐
                      │  是否为热点？     │
                      └────────┬─────────┘
                               │
                   ┌───────────┴────────────┐
                   │                        │
                是热点                    非热点
                   │                        │
                   ↓                        ↓
        ┌──────────────────┐          直接返回 ✅
        │ 6. 晋升到 L1      │
        │  L1CacheEngine   │
        │    .put()        │
        └────────┬─────────┘
                 ↓
        ┌──────────────────────────────────────┐
        │ 7. 发送热点晋升广播                   │
        │  ConsistencyManager.handlePromotion() │
        │  • 检查是否启用晋升广播                │
        │  • 通过 Redis Pub/Sub 发送广播        │
        └────────┬─────────────────────────────┘
                 ↓
        ┌──────────────────────────────────────┐
        │  BroadcastNotifier.publishPromotion() │
        │  • 发布 PROMOTE 类型消息              │
        │  • 携带 context 和 value              │
        └────────┬─────────────────────────────┘
                 │
                 ├────────────┐
                 │            │
                 ↓            ↓
         ┌───────────┐  ┌───────────┐
         │  节点 B    │  │  节点 C    │
         │  收到广播  │  │  收到广播  │
         └─────┬─────┘  └─────┬─────┘
               │              │
               ↓              ↓
        ┌────────────────────────────────────┐
        │  PromotionListener.onPromote()     │
        │  • 收到热点晋升广播                 │
        │  • 将热点数据晋升到本地 L1          │
        └────────────────────────────────────┘
                 ↓
        ┌────────────────────┐
        │ 其他节点L1已预热    │
        │ 后续访问直接命中    │
        └────────────────────┘
                 │
                 ↓
            返回数据 ✅
```

#### 写入流程（缓存失效）

```
                          用户请求（更新操作）
                                 ↓
        ┌──────────────────────────────────────────┐
        │     @HotArmorEvict 注解方法               │
        └─────────────┬────────────────────────────┘
                      ↓
        ┌──────────────────────────────────────────┐
        │    HotArmorAspect.aroundEvict()          │
        │  • 解析 SpEL key 表达式                   │
        │  • 构建 HotArmorContext                  │
        └─────────────┬────────────────────────────┘
                      ↓
        ┌──────────────────────────────────────────┐
        │  DefaultHotArmorAspectHandler            │
        │       .handleEvict()                     │
        └─────────────┬────────────────────────────┘
                      │
                      ├─ beforeInvocation = false (默认)
                      │
                      ↓
        ┌──────────────────────────────────────────┐
        │  1. 执行原方法                            │
        │     joinPoint.proceed()                  │
        │  • 更新数据库                             │
        └─────────────┬────────────────────────────┘
                      ↓
        ┌──────────────────────────────────────────┐
        │  2. DefaultConsistencyManager            │
        │        .handleUpdate()                   │
        │     一致性处理                            │
        └─────────────┬────────────────────────────┘
                      ↓
                      │
        ┌─────────────▼────────────────────────────┐
        │  2.1 invalidateCache()                   │
        │      立即删除缓存（第一次删除）            │
        └─────────────┬────────────────────────────┘
                      │
          ┌───────────┴────────────┐
          │                        │
          ↓                        ↓
┌──────────────────┐    ┌──────────────────────┐
│ L1CacheEngine    │    │ L4SafeLoader         │
│ .invalidate()    │    │ .deleteFromRedis()   │
│ 删除本地缓存      │    │ 删除 Redis 缓存       │
└──────────────────┘    └──────────────────────┘
          │                        │
          └───────────┬────────────┘
                      ↓
        ┌──────────────────────────────────────────┐
        │  2.2 发送失效广播（并行）                  │
        │      enableBroadcast = true              │
        └─────────────┬────────────────────────────┘
                      ↓
        ┌──────────────────────────────────────────┐
        │  RedisBroadcastNotifier.publish()        │
        │  • 通过 Redis Pub/Sub 发送广播消息        │
        │  • 消息类型: INVALIDATE (失效)            │
        │  • 频道: hotarmor:invalidate             │
        │  • 消息: {resource, key}                 │
        └─────────────┬────────────────────────────┘
                      │
                      ├────────────┐
                      │            │
                      ↓            ↓
              ┌───────────┐  ┌───────────┐
              │  节点 A    │  │  节点 B    │
              │  收到广播  │  │  收到广播  │
              └─────┬─────┘  └─────┬─────┘
                    │              │
                    ↓              ↓
        ┌────────────────────────────────────┐
        │  InvalidationListener              │
        │    .onInvalidate()                 │
        │  • 清理本地 L1 缓存                 │
        └────────────────────────────────────┘
                      │
                      ↓
        ┌──────────────────────────────────────────┐
        │  2.3 延迟双删（可选）                      │
        │      enableDelayedDoubleDelete = true    │
        └─────────────┬────────────────────────────┘
                      │
          ┌───────────┴────────────┐
          │                        │
    有 RocketMQ              无 RocketMQ
          │                        │
          ↓                        ↓
┌───────────────────┐    ┌────────────────────┐
│ RocketMQ 方式      │    │ 本地调度器方式      │
│ • sendDelayedMsg  │    │ • ScheduledExecutor│
│ • 延迟 5 秒        │    │ • 延迟 5 秒         │
└────────┬──────────┘    └─────────┬──────────┘
         │                         │
         └──────────┬──────────────┘
                    ↓
        ┌───────────────────────────────┐
        │  延迟后执行                    │
        │  • 再次删除 L1 缓存            │
        │  • 再次删除 Redis 缓存         │
        │  (避免脏读问题)                │
        └───────────┬───────────────────┘
                    ↓
               缓存失效完成 ✅


    ─────────────────────────────────────────
    为什么需要延迟双删？

    时间线：
    T0: 线程A 更新 DB，删除缓存
    T1: 线程B 查询，缓存未命中
    T2: 线程B 从 DB 查询（可能读到旧数据）
    T3: 线程A DB 更新完成
    T4: 线程B 将旧数据写入缓存 ❌
    T5: 延迟删除再次清理缓存 ✅
    ─────────────────────────────────────────
```

### 模块结构

```
HotArmor/
├── hotarmor-core/                    # 核心框架
│   └── src/main/java/
│       └── cn/bafuka/hotarmor/
│           ├── annotation/           # 注解定义
│           │   ├── HotArmorCache.java
│           │   └── HotArmorEvict.java
│           ├── aspect/               # AOP 切面
│           │   ├── HotArmorAspect.java
│           │   ├── HotArmorAspectHandler.java
│           │   └── impl/
│           │       └── DefaultHotArmorAspectHandler.java
│           ├── dataplane/            # 数据平面（L1-L4）
│           │   ├── L1CacheEngine.java
│           │   ├── L2NoiseFilter.java
│           │   ├── L3HotspotDetector.java
│           │   ├── L4SafeLoader.java
│           │   └── impl/
│           │       ├── CaffeineL1CacheEngine.java
│           │       ├── CaffeineL2NoiseFilter.java
│           │       ├── SentinelL3HotspotDetector.java
│           │       └── RedissonL4SafeLoader.java
│           ├── consistency/          # 一致性引擎
│           │   ├── ConsistencyManager.java
│           │   ├── BroadcastNotifier.java
│           │   ├── BroadcastMessage.java
│           │   ├── InvalidationListener.java
│           │   ├── PromotionListener.java
│           │   ├── DelayedDeleteProducer.java
│           │   ├── DelayedDeleteConsumer.java
│           │   └── impl/
│           │       ├── DefaultConsistencyManager.java
│           │       ├── RedisBroadcastNotifier.java
│           │       ├── RocketMQDelayedDeleteProducer.java
│           │       └── RocketMQDelayedDeleteConsumer.java
│           ├── control/              # 控制平面
│           │   ├── RuleManager.java
│           │   ├── RuleChangeListener.java
│           │   └── impl/
│           │       └── DefaultRuleManager.java
│           ├── model/                # 配置模型
│           │   └── HotArmorRule.java
│           ├── core/                 # 核心组件
│           │   ├── CacheEngine.java
│           │   ├── HotArmorContext.java
│           │   └── CacheStats.java
│           ├── config/               # 配置类
│           │   └── HotArmorProperties.java
│           ├── exception/            # 异常定义
│           │   └── HotArmorLoadException.java
│           ├── spel/                 # SpEL 解析
│           │   ├── SpelExpressionParser.java
│           │   └── DefaultSpelExpressionParser.java
│           ├── spi/                  # SPI 扩展点
│           │   ├── ConfigSource.java
│           │   └── impl/
│           │       ├── LocalYamlConfigSource.java
│           │       └── NacosConfigSource.java
│           └── autoconfigure/        # 自动配置
│               └── HotArmorAutoConfiguration.java
│
└── hotarmor-example/                 # 示例应用
    └── src/main/java/
        └── cn/bafuka/hotarmor/example/
            ├── controller/           # REST API
            ├── service/              # 业务服务
            ├── mapper/               # MyBatis Mapper
            └── entity/               # 实体类
```

## ✨ 功能特性

### 📌 声明式注解

```java
@Service
public class ProductService {

    // 查询方法 - 自动启用热点防护
    @HotArmorCache(resource = "product:detail", key = "#productId")
    public Product getProductById(Long productId) {
        return productMapper.selectById(productId);
    }

    // 更新方法 - 自动失效缓存
    @HotArmorEvict(resource = "product:detail", key = "#product.id")
    public void updateProduct(Product product) {
        productMapper.updateById(product);
    }
}
```

### 🔧 灵活配置

支持细粒度的规则配置，每个资源可独立配置防护策略（完整配置示例见下方"配置说明"章节）。

### 🔐 最终一致性保证

- **延迟双删**：更新数据时删除缓存 → 延迟删除（可选，需要 RocketMQ）
- **失效广播**：基于 Redis Pub/Sub，通知所有节点清理本地缓存
- **晋升广播**：检测到热点时，通知所有节点预热L1缓存（NEW）
- **分布式锁**：基于 Redisson，防止缓存击穿

---

## 🚀 快速开始

### 环境要求

- **JDK**: 1.8+
- **Maven**: 3.6+
- **Redis**: 3.0+
- **RocketMQ**: 4.x+（可选，延迟双删功能需要）

### 1. 构建项目

```bash
git clone https://github.com/bafuka/HotArmor.git
cd HotArmor
mvn clean install
```

### 2. 启动 Redis

```bash
# 使用 Docker 快速启动
docker run -d -p 6379:6379 --name redis redis:latest

# 或使用本地 Redis
redis-server
```

### 3. 运行示例应用

```bash
cd hotarmor-example
mvn spring-boot:run
```

应用启动后访问: http://localhost:8080

### 4. 测试接口

```bash
# 查询商品（触发热点防护）
curl http://localhost:8080/api/products/1

# 压测（模拟热点访问）
curl "http://localhost:8080/api/products/benchmark/1?times=100"

# 更新商品（触发缓存失效）
curl -X PUT http://localhost:8080/api/products/1 \
  -H "Content-Type: application/json" \
  -d '{"name":"新商品名","price":199.99}'

# 查看 H2 控制台
open http://localhost:8080/h2-console
```

### 5. 运行测试脚本

```bash
# 快速测试（30秒）
./scripts/test-simple.sh

# 完整测试（包含所有场景）
./scripts/test-hotarmor.sh

# 性能基准测试
./scripts/test-performance.sh

# 实时监控
./scripts/monitor.sh
```

### 6. 查看日志

```bash
# 查看所有日志
tail -f logs/hotarmor.log

# 查看框架日志（包含缓存命中、热点晋升等）
tail -f logs/hotarmor-framework.log

# 搜索热点晋升记录
grep "热点晋升" logs/hotarmor-framework.log
```

---



---

## 📚 技术栈

| 组件 | 版本 | 用途 |
|------|------|------|
| **Java** | 1.8+ | 编程语言 |
| **Spring Boot** | 2.3.12 | 应用框架 |
| **Caffeine** | 2.9.3 | L1 本地缓存（高性能） |
| **Sentinel** | 1.8.6 | L3 热点探测（参数流控） |
| **Redisson** | 3.16.8 | L4 Redis 客户端 + 分布式锁 |
| **Redis** | 3.0+ | 二级缓存 + Pub/Sub |
| **RocketMQ** | 2.2.3 | 延迟双删消息队列（可选） |
| **MyBatis Plus** | 3.4.3 | ORM 框架 |
| **H2 Database** | - | 示例数据库（内存模式） |

---

## 📖 文档

完整文档请查看 [docs/](docs/) 目录：

### 设计文档
- **[架构设计](docs/ARCHITECTURE.md)** - 详细的架构设计和技术选型
- **[技术方案](docs/Technical-solution.MD)** - 核心技术方案说明

---

## 🔧 配置说明

### 完整配置示例

```yaml
hotarmor:
  enabled: true                           # 是否启用 HotArmor
  delayed-delete-topic: hotarmor-delayed-delete
  broadcast-channel: hotarmor:invalidate

  rules:
    - resource: user:detail               # 资源名称（唯一标识）

      # L1 本地缓存配置
      l1Config:
        enabled: true                     # 是否启用
        maximumSize: 10000                # 最大容量
        expireAfterWrite: 60              # 过期时间
        timeUnit: SECONDS                 # 时间单位

      # L2 噪音过滤器配置
      l2Config:
        enabled: true                     # 是否启用
        windowSeconds: 10                 # 时间窗口（秒）
        threshold: 5                      # 访问次数阈值

      # L3 热点探测器配置
      l3Config:
        enabled: true                     # 是否启用
        qpsThreshold: 100.0               # QPS 阈值
        durationInSec: 1                  # 统计时长（秒）

      # L4 安全回源器配置
      l4Config:
        redisKeyPrefix: "hotarmor:user:"  # Redis key 前缀
        redisTtlSeconds: 300              # Redis TTL（秒）
        lockWaitTimeMs: 3000              # 锁等待时间（毫秒）
        lockLeaseTimeMs: 5000             # 锁租约时间（毫秒）

      # 一致性配置
      consistencyConfig:
        enableDelayedDoubleDelete: false  # 延迟双删（需要 RocketMQ）
        delayTimeMs: 5000                 # 延迟时间（毫秒）
        enableBroadcast: true             # 缓存失效和热点晋升广播
        broadcastChannel: "hotarmor:invalidate"
```

### 注解使用说明

#### @HotArmorCache

用于查询方法，启用热点防护：

```java
@HotArmorCache(
    resource = "user:detail",      // 资源名称（必填）
    key = "#userId",               // 缓存 Key 表达式（支持 SpEL）
    condition = "#userId > 0",     // 条件表达式（可选）
    enabled = true                 // 是否启用（可选）
)
public User getUserById(Long userId) {
    return userMapper.selectById(userId);
}
```

**支持的 SpEL 表达式**：
- `#userId` - 参数名
- `#p0`, `#p1` - 参数索引
- `#user.id` - 对象属性
- `#userId + ':' + #type` - 复杂表达式

#### @HotArmorEvict

用于更新/删除方法，自动失效缓存：

```java
@HotArmorEvict(
    resource = "user:detail",      // 资源名称（必填）
    key = "#user.id"               // 缓存 Key 表达式（支持 SpEL）
)
public void updateUser(User user) {
    userMapper.updateById(user);
}
```

---

## 🔍 监控和诊断

### 查看日志

```bash
# 实时查看所有日志
tail -f logs/hotarmor.log

# 查看框架详细日志（推荐）
tail -f logs/hotarmor-framework.log

# 查看错误日志
tail -f logs/hotarmor-error.log
```

### 关键日志示例

```log
# 热点晋升
2025-12-15 20:00:01 [http-nio-8080-exec-1] INFO  cn.bafuka.hotarmor.dataplane.impl.SentinelL3HotspotDetector -
热点晋升触发: resource=product:detail, key=1, qps=150.5

# L1 缓存命中
2025-12-15 20:00:01 [http-nio-8080-exec-2] DEBUG cn.bafuka.hotarmor.dataplane.impl.CaffeineL1CacheEngine -
L1 缓存命中: resource=product:detail, key=1

# 缓存失效
2025-12-15 20:00:05 [http-nio-8080-exec-3] INFO  cn.bafuka.hotarmor.consistency.BroadcastNotifier -
发送缓存失效广播: channel=hotarmor:invalidate, resource=product:detail, key=1
```

---

## 🛣️ Roadmap

- [x] **v0.1** - 核心架构设计
- [x] **v0.5** - 数据平面实现（L1-L4）
- [x] **v1.0** - 基础防护能力（注解 + YAML 配置）
- [x] **v1.5** - 一致性引擎（广播通知 + 延迟双删）
- [ ] **v2.0** - 配置中心集成（Nacos）

---

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

### 开发指南

```bash
# 克隆项目
git clone https://github.com/bafuka/HotArmor.git

# 构建项目
cd HotArmor
mvn clean install

# 运行测试
cd hotarmor-example
mvn spring-boot:run

# 运行测试脚本
./scripts/test-hotarmor.sh
```

### 代码规范

- 遵循阿里巴巴 Java 开发手册
- 单元测试覆盖率 > 70%
- 提交前运行 `mvn clean install`

---

## ⭐ Star History

[![Star History Chart](https://api.star-history.com/svg?repos=bafuka/HotArmor&type=Date)](https://star-history.com/#bafuka/HotArmor&Date)

