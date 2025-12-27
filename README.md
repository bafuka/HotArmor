<div align="center">

[English](README.md) | [简体中文](README-ZH.md)

</div>

---

<div align="center">

# HotArmor 🛡️

**Intelligent Hotspot Data Protection Framework · Solving High-Concurrency Cache Penetration**

*One annotation, automatic promotion of hotspot data to local cache*

[![Java](https://img.shields.io/badge/Java-1.8+-orange.svg)](https://www.oracle.com/java/technologies/javase-downloads.html)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.3.12-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Sentinel](https://img.shields.io/badge/Sentinel-1.8.6-blue.svg)](https://sentinelguard.io/)
[![License](https://img.shields.io/badge/license-Apache%202-blue.svg)](LICENSE)

</div>

---

## 💡 What Problem Does It Solve?

**HotArmor** is a hotspot data protection middleware designed for high-concurrency scenarios. In flash sales, trending events, and similar scenarios, a small number of hotspot data (such as popular products or trending topics) can cause serious performance issues:

| Problem | Typical Scenario | Technical Impact | HotArmor Solution |
|---------|-----------------|------------------|-------------------|
| ⚡ **Cache Breakdown** | Moment when hot key expires | Thousands of requests simultaneously bypass cache and hit database, overwhelming DB | L4 distributed lock + Double-Check ensures single-point source loading |
| 🔥 **Hotspot Overload** | Celebrity products frequently accessed | Redis connection pool exhausted, bandwidth saturated, slow response | L1-L3 intelligently identifies hotspots and promotes to local cache (microsecond level) |
| 🔄 **Distributed Cache Consistency** | Multi-node cluster deployment | Node A updates data, nodes B/C/D have stale local cache causing dirty reads | Pub/Sub invalidation broadcast + hotspot promotion broadcast, full-node synchronization |
| 🗑️ **DB-Cache Consistency** | High-concurrency read-write race conditions | After updating DB and deleting cache, concurrent queries write old data back to Redis | Delayed double-delete strategy eliminates write-after-read race window |

## 🚀 Core Features

```java
// Just one annotation, framework handles hotspot protection automatically
@HotArmorCache(resource = "product:detail", key = "#id")
public Product getProduct(Long id) {
    return productMapper.selectById(id);  // Hotspot data automatically promoted to local cache
}
```

- ✨ **Out-of-the-Box** - Declarative annotation usage, zero-intrusion integration
- 🧠 **Intelligent Recognition** - Based on Sentinel's hotspot parameter flow control, precise hotspot identification
- 🔄 **Four-Level Protection** - L1 Local Cache → L2 Noise Filter → L3 Hotspot Detection → L4 Safe Source Loading
- 📡 **Eventual Consistency** - Delayed double-delete + Redis Pub/Sub broadcast ensures cluster cache synchronization
- ⚡ **Ultimate Performance** - Hotspot data from Redis millisecond-level → Caffeine microsecond-level response

---

## 🏗️ Architecture Design

### System Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Application Layer                            │
│                                                                     │
│    @HotArmorCache(resource="...", key="...")  ← Query cache         │
│    @HotArmorEvict(resource="...", key="...")  ← Invalidate cache    │
└──────────────────────────┬──────────────────────────────────────────┘
                           │
                           │ AOP Interception
                           ↓
┌─────────────────────────────────────────────────────────────────────┐
│                      HotArmor Core Engine                            │
│                                                                     │
│  ┌───────────────────────────────────────────────────────────────┐ │
│  │                    Aspect Layer                                │ │
│  │  • HotArmorAspect - AOP interceptor                            │ │
│  │  • SpEL expression parser - Parse key/condition                │ │
│  │  • HotArmorContext - Request context object                    │ │
│  └─────────────────────────┬─────────────────────────────────────┘ │
│                            ↓                                        │
│  ┌───────────────────────────────────────────────────────────────┐ │
│  │                 Handler Layer                                  │ │
│  │  • DefaultHotArmorAspectHandler                                │ │
│  │    - handleCache() : Read flow four-level funnel               │ │
│  │    - handleEvict() : Write flow cache invalidation             │ │
│  └─────────────────────────┬─────────────────────────────────────┘ │
│                            ↓                                        │
│  ┌───────────────────────────────────────────────────────────────┐ │
│  │                  Data Plane                                    │ │
│  │                                                               │ │
│  │  ┌─────────────────────────────────────────────────────────┐ │ │
│  │  │ L1: Local Cache Layer (CaffeineL1CacheEngine)            │ │ │
│  │  │  • Caffeine high-performance local cache                 │ │ │
│  │  │  • Microsecond-level response (~1μs)                     │ │ │
│  │  │  • Independent capacity and expiration configuration     │ │ │
│  │  └─────────────────────────────────────────────────────────┘ │ │
│  │                            ↓ Miss                             │ │
│  │  ┌─────────────────────────────────────────────────────────┐ │ │
│  │  │ L2: Noise Filter Layer (CaffeineL2NoiseFilter)           │ │ │
│  │  │  • Lightweight access counter                            │ │ │
│  │  │  • Filter cold data (access frequency < threshold)       │ │ │
│  │  │  • Protect Sentinel resources                            │ │ │
│  │  └─────────────────────────────────────────────────────────┘ │ │
│  │                            ↓ Pass                             │ │
│  │  ┌─────────────────────────────────────────────────────────┐ │ │
│  │  │ L3: Hotspot Detection Layer (SentinelL3HotspotDetector)  │ │ │
│  │  │  • Sentinel parameter flow control                       │ │ │
│  │  │  • Hotspot determination (QPS > threshold)               │ │ │
│  │  │  • Automatic hotspot promotion trigger                   │ │ │
│  │  └─────────────────────────────────────────────────────────┘ │ │
│  │                            ↓ Block (Hotspot!)                 │ │
│  │  ┌─────────────────────────────────────────────────────────┐ │ │
│  │  │ L4: Safe Source Loading Layer (RedissonL4SafeLoader)    │ │ │
│  │  │  • Redisson distributed lock                             │ │ │
│  │  │  • Double-Check mechanism                                │ │ │
│  │  │  • Retry + exponential backoff                           │ │ │
│  │  │  • Prevent cache breakdown                               │ │ │
│  │  └─────────────────────────────────────────────────────────┘ │ │
│  └───────────────────────────────────────────────────────────────┘ │
│                                                                     │
│  ┌───────────────────────────────────────────────────────────────┐ │
│  │                 Control Plane                                  │ │
│  │                                                               │ │
│  │  • DefaultRuleManager - Rule manager                          │ │
│  │    - loadRules() : Load rule configuration                    │ │
│  │    - updateRules() : Dynamic rule updates                     │ │
│  │    - validateRule() : Rule validation                         │ │
│  │  • HotArmorRule - Rule model (L1-L4 configuration)            │ │
│  └───────────────────────────────────────────────────────────────┘ │
│                                                                     │
│  ┌───────────────────────────────────────────────────────────────┐ │
│  │                Consistency Engine                              │ │
│  │                                                               │ │
│  │  • DefaultConsistencyManager - Consistency manager            │ │
│  │    - handleUpdate() : Handle cache updates                    │ │
│  │    - handlePromotion() : Handle hotspot promotion broadcast   │ │
│  │    - invalidateCache() : Immediate cache deletion             │ │
│  │  • RedisBroadcastNotifier - Redis Pub/Sub broadcast           │ │
│  │    - Cache invalidation broadcast                             │ │
│  │    - Hotspot promotion broadcast (NEW)                        │ │
│  │  • DelayedDeleteProducer - Delayed double-delete (RocketMQ)   │ │
│  └───────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────┘
                           │
          ┌────────────────┼────────────────┐
          │                │                │
          ↓                ↓                ↓
┌──────────────────┐ ┌────────────┐ ┌─────────────────┐
│  Redis           │ │  RocketMQ  │ │  Database       │
│  • L2 cache      │ │  • Delayed │ │  • MySQL        │
│  • Pub/Sub       │ │    messages│ │  • PostgreSQL   │
│  • Distributed   │ │  (optional)│ │  etc.           │
│    lock          │ └────────────┘ └─────────────────┘
└──────────────────┘

───────────────────────────────────────────────────────────────────────
Data Flow Description:

Read Flow:
  User request → AOP interception → L1 local cache → L2 noise filter → L3 hotspot detection
  → L4 safe source loading → Redis/DB → Promote to L1 (hotspot) → Send promotion broadcast
  → Other nodes receive broadcast and promote to L1 → Return

Write Flow:
  User request → AOP interception → Update DB → Delete L1+Redis → Send invalidation broadcast
  → Other nodes clear L1 → Delayed double-delete (optional) → Complete
───────────────────────────────────────────────────────────────────────
```

### Core Processes

#### Read Flow (Four-Level Funnel)

```
                          User Request
                             ↓
        ┌─────────────────────────────────────────┐
        │     @HotArmorCache annotated method     │
        └────────────┬────────────────────────────┘
                     ↓
        ┌─────────────────────────────────────────┐
        │    HotArmorAspect.aroundCache()         │
        │  • Parse SpEL key expression             │
        │  • Parse condition                       │
        │  • Build HotArmorContext                 │
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
        │     Check local cache                   │
        └────────────┬───────────────────────────┘
                     │
         ┌───────────┴────────────┐
         │                        │
      Cache Hit              Cache Miss
         │                        │
         ↓                        ↓
    Return data ✅    ┌─────────────────────────┐
    (~1μs)            │ L2: L2NoiseFilter       │
                      │    .shouldPass()        │
                      │  • Check access freq    │
                      └──────────┬──────────────┘
                                 │
                     ┌───────────┴────────────┐
                     │                        │
              Access < threshold      Access >= threshold
               (Cold data)              (Hot data)
                     │                        │
                     ↓                        ↓
            Direct source load       ┌────────────────────┐
          (No Sentinel cost)         │ L3: SentinelL3     │
                     │                │   .isHotspot()     │
                     │                │  • Sentinel detect │
                     │                └──────┬─────────────┘
                     │                       │
                     │           ┌───────────┴────────────┐
                     │           │                        │
                     │      QPS < threshold           QPS >= threshold
                     │      (Not hotspot)             (Hotspot!)
                     │           │                        │
                     └───────────┼────────────────────────┘
                                 ↓
                     ┌───────────────────────────────────┐
                     │ L4: RedissonL4SafeLoader.load()   │
                     │         Safe source loading        │
                     └────────────┬──────────────────────┘
                                  ↓
                     ┌────────────────────────────────────┐
                     │  1. getFromRedis()                 │
                     │     Check Redis cache first        │
                     └────────────┬───────────────────────┘
                                  │
                      ┌───────────┴────────────┐
                      │                        │
                  Redis Hit              Redis Miss
                      │                        │
                      ↓                        ↓
                 Return data ✅     ┌────────────────────┐
                                    │  2. Acquire lock   │
                                    │  redissonClient    │
                                    │    .getLock()      │
                                    └──────┬─────────────┘
                                           │
                               ┌───────────┴────────────┐
                               │                        │
                          Lock acquired            Lock failed
                               │                        │
                               ↓                        ↓
                  ┌──────────────────────┐    ┌──────────────────┐
                  │ 3. Double-Check      │    │ Retry mechanism  │
                  │    Check Redis again │    │ • Wait + retry   │
                  └──────┬───────────────┘    │ • Exponential    │
                         │                    │   backoff        │
                    ┌────┴─────┐              │ • Fallback to DB │
                    │          │               └────────┬─────────┘
                  Hit      Miss                         │
                    │          │                        │
                    │          ↓                        │
                    │   ┌──────────────┐               │
                    │   │ 4. Query DB  │               │
                    │   │  joinPoint   │               │
                    │   │  .proceed()  │               │
                    │   └──────┬───────┘               │
                    │          │                        │
                    │          ↓                        │
                    │   ┌──────────────┐               │
                    │   │ 5. Write to  │               │
                    │   │    Redis     │               │
                    │   └──────┬───────┘               │
                    │          │                        │
                    └──────────┴────────────────────────┘
                               ↓
                      ┌──────────────────┐
                      │  Is hotspot?     │
                      └────────┬─────────┘
                               │
                   ┌───────────┴────────────┐
                   │                        │
                Hotspot                 Not hotspot
                   │                        │
                   ↓                        ↓
        ┌──────────────────┐          Return data ✅
        │ 6. Promote to L1 │
        │  L1CacheEngine   │
        │    .put()        │
        └────────┬─────────┘
                 ↓
        ┌──────────────────────────────────────┐
        │ 7. Send hotspot promotion broadcast   │
        │  ConsistencyManager.handlePromotion() │
        │  • Check if broadcast enabled         │
        │  • Send via Redis Pub/Sub             │
        └────────┬─────────────────────────────┘
                 ↓
        ┌──────────────────────────────────────┐
        │  BroadcastNotifier.publishPromotion() │
        │  • Publish PROMOTE type message       │
        │  • Include context and value          │
        └────────┬─────────────────────────────┘
                 │
                 ├────────────┐
                 │            │
                 ↓            ↓
         ┌───────────┐  ┌───────────┐
         │  Node B   │  │  Node C   │
         │  Receives │  │  Receives │
         └─────┬─────┘  └─────┬─────┘
               │              │
               ↓              ↓
        ┌────────────────────────────────────┐
        │  PromotionListener.onPromote()     │
        │  • Receive hotspot promotion       │
        │  • Promote data to local L1        │
        └────────────────────────────────────┘
                 ↓
        ┌────────────────────┐
        │ Other nodes L1     │
        │ prewarmed,         │
        │ subsequent hits    │
        └────────────────────┘
                 │
                 ↓
            Return data ✅
```

#### Write Flow (Cache Invalidation)

```
                          User Request (Update operation)
                                 ↓
        ┌──────────────────────────────────────────┐
        │     @HotArmorEvict annotated method      │
        └─────────────┬────────────────────────────┘
                      ↓
        ┌──────────────────────────────────────────┐
        │    HotArmorAspect.aroundEvict()          │
        │  • Parse SpEL key expression              │
        │  • Build HotArmorContext                  │
        └─────────────┬────────────────────────────┘
                      ↓
        ┌──────────────────────────────────────────┐
        │  DefaultHotArmorAspectHandler            │
        │       .handleEvict()                     │
        └─────────────┬────────────────────────────┘
                      │
                      ├─ beforeInvocation = false (default)
                      │
                      ↓
        ┌──────────────────────────────────────────┐
        │  1. Execute original method               │
        │     joinPoint.proceed()                  │
        │  • Update database                        │
        └─────────────┬────────────────────────────┘
                      ↓
        ┌──────────────────────────────────────────┐
        │  2. DefaultConsistencyManager            │
        │        .handleUpdate()                   │
        │     Consistency handling                  │
        └─────────────┬────────────────────────────┘
                      ↓
                      │
        ┌─────────────▼────────────────────────────┐
        │  2.1 invalidateCache()                   │
        │      Immediate cache delete (first del)   │
        └─────────────┬────────────────────────────┘
                      │
          ┌───────────┴────────────┐
          │                        │
          ↓                        ↓
┌──────────────────┐    ┌──────────────────────┐
│ L1CacheEngine    │    │ L4SafeLoader         │
│ .invalidate()    │    │ .deleteFromRedis()   │
│ Delete local     │    │ Delete Redis cache   │
└──────────────────┘    └──────────────────────┘
          │                        │
          └───────────┬────────────┘
                      ↓
        ┌──────────────────────────────────────────┐
        │  2.2 Send invalidation broadcast          │
        │      enableBroadcast = true              │
        └─────────────┬────────────────────────────┘
                      ↓
        ┌──────────────────────────────────────────┐
        │  RedisBroadcastNotifier.publish()        │
        │  • Send via Redis Pub/Sub                │
        │  • Message type: INVALIDATE               │
        │  • Channel: hotarmor:invalidate          │
        │  • Message: {resource, key}              │
        └─────────────┬────────────────────────────┘
                      │
                      ├────────────┐
                      │            │
                      ↓            ↓
              ┌───────────┐  ┌───────────┐
              │  Node A   │  │  Node B   │
              │  Receives │  │  Receives │
              └─────┬─────┘  └─────┬─────┘
                    │              │
                    ↓              ↓
        ┌────────────────────────────────────┐
        │  InvalidationListener              │
        │    .onInvalidate()                 │
        │  • Clear local L1 cache            │
        └────────────────────────────────────┘
                      │
                      ↓
        ┌──────────────────────────────────────────┐
        │  2.3 Delayed double-delete (optional)     │
        │      enableDelayedDoubleDelete = true    │
        └─────────────┬────────────────────────────┘
                      │
          ┌───────────┴────────────┐
          │                        │
    With RocketMQ            Without RocketMQ
          │                        │
          ↓                        ↓
┌───────────────────┐    ┌────────────────────┐
│ RocketMQ mode     │    │ Local scheduler    │
│ • sendDelayedMsg  │    │ • ScheduledExecutor│
│ • Delay 5 sec     │    │ • Delay 5 sec      │
└────────┬──────────┘    └─────────┬──────────┘
         │                         │
         └──────────┬──────────────┘
                    ↓
        ┌───────────────────────────────┐
        │  Execute after delay          │
        │  • Delete L1 cache again      │
        │  • Delete Redis cache again   │
        │  (Avoid dirty read)           │
        └───────────┬───────────────────┘
                    ↓
               Cache invalidation complete ✅


    ─────────────────────────────────────────
    Why delayed double-delete?

    Timeline:
    T0: Thread A updates DB, deletes cache
    T1: Thread B queries, cache miss
    T2: Thread B queries DB (may read old data)
    T3: Thread A DB update completes
    T4: Thread B writes old data to cache ❌
    T5: Delayed delete clears cache again ✅
    ─────────────────────────────────────────
```

### Module Structure

```
HotArmor/
├── hotarmor-core/                    # Core framework
│   └── src/main/java/
│       └── cn/bafuka/hotarmor/
│           ├── annotation/           # Annotation definitions
│           │   ├── HotArmorCache.java
│           │   └── HotArmorEvict.java
│           ├── aspect/               # AOP aspects
│           │   ├── HotArmorAspect.java
│           │   ├── HotArmorAspectHandler.java
│           │   └── impl/
│           │       └── DefaultHotArmorAspectHandler.java
│           ├── dataplane/            # Data plane (L1-L4)
│           │   ├── L1CacheEngine.java
│           │   ├── L2NoiseFilter.java
│           │   ├── L3HotspotDetector.java
│           │   ├── L4SafeLoader.java
│           │   └── impl/
│           │       ├── CaffeineL1CacheEngine.java
│           │       ├── CaffeineL2NoiseFilter.java
│           │       ├── SentinelL3HotspotDetector.java
│           │       └── RedissonL4SafeLoader.java
│           ├── consistency/          # Consistency engine
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
│           ├── control/              # Control plane
│           │   ├── RuleManager.java
│           │   ├── RuleChangeListener.java
│           │   └── impl/
│           │       └── DefaultRuleManager.java
│           ├── model/                # Configuration models
│           │   └── HotArmorRule.java
│           ├── core/                 # Core components
│           │   ├── CacheEngine.java
│           │   ├── HotArmorContext.java
│           │   └── CacheStats.java
│           ├── config/               # Configuration classes
│           │   └── HotArmorProperties.java
│           ├── exception/            # Exception definitions
│           │   └── HotArmorLoadException.java
│           ├── spel/                 # SpEL parsing
│           │   ├── SpelExpressionParser.java
│           │   └── DefaultSpelExpressionParser.java
│           ├── spi/                  # SPI extension points
│           │   ├── ConfigSource.java
│           │   └── impl/
│           │       ├── LocalYamlConfigSource.java
│           │       └── NacosConfigSource.java
│           └── autoconfigure/        # Auto-configuration
│               └── HotArmorAutoConfiguration.java
│
└── hotarmor-example/                 # Example application
    └── src/main/java/
        └── cn/bafuka/hotarmor/example/
            ├── controller/           # REST API
            ├── service/              # Business services
            ├── mapper/               # MyBatis Mapper
            └── entity/               # Entity classes
```

## ✨ Features

### 📌 Declarative Annotations

```java
@Service
public class ProductService {

    // Query method - automatically enable hotspot protection
    @HotArmorCache(resource = "product:detail", key = "#productId")
    public Product getProductById(Long productId) {
        return productMapper.selectById(productId);
    }

    // Update method - automatically invalidate cache
    @HotArmorEvict(resource = "product:detail", key = "#product.id")
    public void updateProduct(Product product) {
        productMapper.updateById(product);
    }
}
```

### 🔧 Flexible Configuration

Supports fine-grained rule configuration, each resource can be independently configured with protection strategies (see complete configuration example in "Configuration" section below).

### 🔐 Eventual Consistency Guarantee

- **Delayed Double-Delete**: Delete cache when updating data → Delayed delete (optional, requires RocketMQ)
- **Invalidation Broadcast**: Based on Redis Pub/Sub, notifies all nodes to clear local cache
- **Promotion Broadcast**: When hotspot detected, notifies all nodes to prewarm L1 cache (NEW)
- **Distributed Lock**: Based on Redisson, prevents cache breakdown

---

## 🚀 Quick Start

### Requirements

- **JDK**: 1.8+
- **Maven**: 3.6+
- **Redis**: 3.0+
- **RocketMQ**: 4.x+ (Optional, required for delayed double-delete feature)

### 1. Build Project

```bash
git clone https://github.com/bafuka/HotArmor.git
cd HotArmor
mvn clean install
```

### 2. Start Redis

```bash
# Use Docker for quick start
docker run -d -p 6379:6379 --name redis redis:latest

# Or use local Redis
redis-server
```

### 3. Run Example Application

```bash
cd hotarmor-example
mvn spring-boot:run
```

After application starts, visit: http://localhost:8080

### 4. Test APIs

```bash
# Query product (trigger hotspot protection)
curl http://localhost:8080/api/products/1

# Stress test (simulate hotspot access)
curl "http://localhost:8080/api/products/benchmark/1?times=100"

# Update product (trigger cache invalidation)
curl -X PUT http://localhost:8080/api/products/1 \
  -H "Content-Type: application/json" \
  -d '{"name":"New Product Name","price":199.99}'

# View H2 console
open http://localhost:8080/h2-console
```

### 5. Run Test Scripts

```bash
# Quick test (30 seconds)
./scripts/test-simple.sh

# Full test (all scenarios)
./scripts/test-hotarmor.sh

# Performance benchmark
./scripts/test-performance.sh

# Real-time monitoring
./scripts/monitor.sh
```

### 6. View Logs

```bash
# View all logs
tail -f logs/hotarmor.log

# View framework logs (includes cache hits, hotspot promotions, etc.)
tail -f logs/hotarmor-framework.log

# Search hotspot promotion records
grep "热点晋升" logs/hotarmor-framework.log
```

---

## 📚 Tech Stack

| Component | Version | Purpose |
|-----------|---------|---------|
| **Java** | 1.8+ | Programming language |
| **Spring Boot** | 2.3.12 | Application framework |
| **Caffeine** | 2.9.3 | L1 local cache (high-performance) |
| **Sentinel** | 1.8.6 | L3 hotspot detection (parameter flow control) |
| **Redisson** | 3.16.8 | L4 Redis client + distributed lock |
| **Redis** | 3.0+ | L2 cache + Pub/Sub |
| **RocketMQ** | 2.2.3 | Delayed double-delete message queue (optional) |
| **MyBatis Plus** | 3.4.3 | ORM framework |
| **H2 Database** | - | Example database (in-memory mode) |

---

## 📖 Documentation

For complete documentation, see [docs/](docs/) directory:

### Design Documents
- **[Architecture Design](docs/ARCHITECTURE.md)** - Detailed architecture design and technology selection
- **[Technical Solution](docs/Technical-solution.MD)** - Core technical solution description

---

## 🔧 Configuration

### Complete Configuration Example

```yaml
hotarmor:
  enabled: true                           # Enable HotArmor
  delayed-delete-topic: hotarmor-delayed-delete
  broadcast-channel: hotarmor:invalidate

  rules:
    - resource: user:detail               # Resource name (unique identifier)

      # L1 local cache configuration
      l1Config:
        enabled: true                     # Enable
        maximumSize: 10000                # Max capacity
        expireAfterWrite: 60              # Expiration time
        timeUnit: SECONDS                 # Time unit

      # L2 noise filter configuration
      l2Config:
        enabled: true                     # Enable
        windowSeconds: 10                 # Time window (seconds)
        threshold: 5                      # Access count threshold

      # L3 hotspot detector configuration
      l3Config:
        enabled: true                     # Enable
        qpsThreshold: 100.0               # QPS threshold
        durationInSec: 1                  # Statistics duration (seconds)

      # L4 safe source loader configuration
      l4Config:
        redisKeyPrefix: "hotarmor:user:"  # Redis key prefix
        redisTtlSeconds: 300              # Redis TTL (seconds)
        lockWaitTimeMs: 3000              # Lock wait time (milliseconds)
        lockLeaseTimeMs: 5000             # Lock lease time (milliseconds)

      # Consistency configuration
      consistencyConfig:
        enableDelayedDoubleDelete: false  # Delayed double-delete (requires RocketMQ)
        delayTimeMs: 5000                 # Delay time (milliseconds)
        enableBroadcast: true             # Cache invalidation and hotspot promotion broadcast
        broadcastChannel: "hotarmor:invalidate"
```

### Annotation Usage

#### @HotArmorCache

Used for query methods to enable hotspot protection:

```java
@HotArmorCache(
    resource = "user:detail",      // Resource name (required)
    key = "#userId",               // Cache key expression (supports SpEL)
    condition = "#userId > 0",     // Condition expression (optional)
    enabled = true                 // Enable (optional)
)
public User getUserById(Long userId) {
    return userMapper.selectById(userId);
}
```

**Supported SpEL expressions**:
- `#userId` - Parameter name
- `#p0`, `#p1` - Parameter index
- `#user.id` - Object property
- `#userId + ':' + #type` - Complex expression

#### @HotArmorEvict

Used for update/delete methods to automatically invalidate cache:

```java
@HotArmorEvict(
    resource = "user:detail",      // Resource name (required)
    key = "#user.id"               // Cache key expression (supports SpEL)
)
public void updateUser(User user) {
    userMapper.updateById(user);
}
```

---

## 🔍 Monitoring and Diagnostics

### View Logs

```bash
# Real-time view all logs
tail -f logs/hotarmor.log

# View framework detailed logs (recommended)
tail -f logs/hotarmor-framework.log

# View error logs
tail -f logs/hotarmor-error.log
```

### Key Log Examples

```log
# Hotspot promotion
2025-12-15 20:00:01 [http-nio-8080-exec-1] INFO  cn.bafuka.hotarmor.dataplane.impl.SentinelL3HotspotDetector -
Hotspot promotion triggered: resource=product:detail, key=1, qps=150.5

# L1 cache hit
2025-12-15 20:00:01 [http-nio-8080-exec-2] DEBUG cn.bafuka.hotarmor.dataplane.impl.CaffeineL1CacheEngine -
L1 cache hit: resource=product:detail, key=1

# Cache invalidation
2025-12-15 20:00:05 [http-nio-8080-exec-3] INFO  cn.bafuka.hotarmor.consistency.BroadcastNotifier -
Send cache invalidation broadcast: channel=hotarmor:invalidate, resource=product:detail, key=1
```

---

## 🛣️ Roadmap

- [x] **v0.1** - Core architecture design
- [x] **v0.5** - Data plane implementation (L1-L4)
- [x] **v1.0** - Basic protection capabilities (annotation + YAML configuration)
- [x] **v1.5** - Consistency engine (broadcast notification + delayed double-delete)
- [ ] **v2.0** - Configuration center integration (Nacos)

---

## 🤝 Contributing

Issues and Pull Requests are welcome!

### Development Guide

```bash
# Clone project
git clone https://github.com/bafuka/HotArmor.git

# Build project
cd HotArmor
mvn clean install

# Run tests
cd hotarmor-example
mvn spring-boot:run

# Run test scripts
./scripts/test-hotarmor.sh
```

### Code Standards

- Follow Alibaba Java Coding Guidelines
- Unit test coverage > 70%
- Run `mvn clean install` before commit

---
