# 热点晋升广播功能

## 功能概述

在分布式环境下，当一个节点检测到热点数据并晋升到L1缓存时，会通过Redis Pub/Sub广播通知其他节点，让它们也将该热点数据预热到L1缓存中。这样可以避免其他节点的冷启动问题，提升整体集群的性能。

## 工作原理

```
节点A检测到热点
    ↓
晋升到本地L1缓存
    ↓
发送热点晋升广播 (Redis Pub/Sub)
    ↓
    ├─→ 节点B 收到广播 → 晋升到L1
    ├─→ 节点C 收到广播 → 晋升到L1
    └─→ 节点D 收到广播 → 晋升到L1
```

## 核心实现

### 1. 新增类和接口

#### BroadcastMessage
- 路径: `cn.bafuka.hotarmor.consistency.BroadcastMessage`
- 作用: 包装广播消息，区分"失效"和"晋升"两种类型
- 关键字段:
  - `MessageType type`: INVALIDATE（失效）或 PROMOTE（晋升）
  - `HotArmorContext context`: 上下文信息
  - `Object value`: 缓存值（可选）

#### PromotionListener
- 路径: `cn.bafuka.hotarmor.consistency.PromotionListener`
- 作用: 处理热点晋升广播的监听器接口
- 方法: `void onPromote(HotArmorContext context, Object value)`

### 2. 扩展现有接口

#### BroadcastNotifier
新增方法：
```java
// 发布热点晋升消息
void publishPromotion(HotArmorContext context, Object value);

// 订阅热点晋升消息
void subscribePromotion(PromotionListener listener);
```

#### ConsistencyManager
新增方法：
```java
// 处理热点晋升（发送晋升广播）
void handlePromotion(HotArmorContext context, Object value);
```

### 3. 实现类更新

#### RedisBroadcastNotifier
- 支持发送和接收两种类型的广播消息
- 向后兼容旧的HotArmorContext消息格式
- 根据消息类型分发到对应的监听器

#### DefaultConsistencyManager
新增功能：
- `handlePromotion()`: 发送热点晋升广播
- `LocalCachePromotionListener`: 处理收到的热点晋升广播
  - 优先使用广播中携带的缓存值
  - 如果没有值，则从Redis加载

#### DefaultHotArmorAspectHandler
修改热点晋升逻辑：
```java
if (promoteToL1 && value != null) {
    l1CacheEngine.put(context, value);
    log.info("晋升为 L1 cache: resource={}, key={}", ...);

    // 🔥 新增：发送热点晋升广播
    consistencyManager.handlePromotion(context, value);
}
```

### 4. 配置项

在 `HotArmorRule.ConsistencyConfig` 中新增配置：
```java
/**
 * 是否启用热点晋升广播
 * 当一个节点检测到热点时，通知其他节点也晋升到L1
 */
@Builder.Default
private boolean enablePromotionBroadcast = true;
```

## 配置示例

```yaml
hotarmor:
  rules:
    - resource: product:detail
      consistencyConfig:
        enableBroadcast: true              # 启用缓存失效广播
        enablePromotionBroadcast: true     # 启用热点晋升广播（默认开启）
        broadcastChannel: "hotarmor:invalidate"
```

## 工作流程

### 发送端（检测到热点的节点）

1. L3热点探测器检测到热点
2. 调用 `l1CacheEngine.put()` 晋升到L1
3. 调用 `consistencyManager.handlePromotion()` 发送广播
4. 通过 `broadcastNotifier.publishPromotion()` 发送到Redis Pub/Sub

### 接收端（其他节点）

1. `LocalCachePromotionListener.onPromote()` 收到广播
2. 检查广播中是否携带缓存值
   - 有值：直接使用，晋升到L1
   - 无值：从Redis加载，晋升到L1
3. 记录日志

## 优势

1. **性能提升**: 避免其他节点的冷启动，首次访问即可命中L1
2. **自动化**: 无需手动干预，自动完成集群范围的热点预热
3. **可配置**: 通过 `enablePromotionBroadcast` 灵活控制
4. **兼容性**: 向后兼容旧的消息格式

## 注意事项

1. **序列化**: 缓存值必须可序列化，建议使用简单类型或实现Serializable
2. **网络开销**: 携带值会增加网络传输量，可以根据实际情况选择是否携带
3. **一致性**: 如果广播失败，其他节点首次访问时会略慢，但不影响正确性
4. **配置要求**: 需要配置Redis Pub/Sub和BroadcastNotifier
5. **订阅顺序**: `subscribe()` 和 `subscribePromotion()` 可以以任意顺序调用，内部会自动重新注册监听器

## 监控

通过日志监控热点晋升广播：

```bash
# 查看发送的晋升广播
grep "已发送热点晋升广播" logs/hotarmor-framework.log

# 查看收到的晋升广播
grep "收到热点晋升广播" logs/hotarmor-framework.log

# 查看晋升成功的记录
grep "热点数据已晋升到 L1" logs/hotarmor-framework.log
```

## 测试建议

1. 启动多个应用实例
2. 在节点A上访问某个key，触发热点晋升
3. 在节点B上访问同一个key，观察是否命中L1缓存
4. 检查日志确认广播已发送和接收

## 相关文件

- `BroadcastMessage.java`: 广播消息包装类
- `PromotionListener.java`: 热点晋升监听器接口
- `BroadcastNotifier.java`: 广播通知器接口（扩展）
- `ConsistencyManager.java`: 一致性管理器接口（扩展）
- `RedisBroadcastNotifier.java`: 广播通知器实现（更新）
- `DefaultConsistencyManager.java`: 一致性管理器实现（更新）
- `DefaultHotArmorAspectHandler.java`: 切面处理器（更新）
- `HotArmorRule.java`: 配置模型（扩展）
