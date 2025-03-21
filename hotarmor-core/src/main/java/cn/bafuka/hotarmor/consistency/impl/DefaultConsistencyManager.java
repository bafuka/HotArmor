package cn.bafuka.hotarmor.consistency.impl;

import cn.bafuka.hotarmor.consistency.BroadcastNotifier;
import cn.bafuka.hotarmor.consistency.ConsistencyManager;
import cn.bafuka.hotarmor.consistency.DelayedDeleteProducer;
import cn.bafuka.hotarmor.consistency.InvalidationListener;
import cn.bafuka.hotarmor.consistency.PromotionListener;
import cn.bafuka.hotarmor.core.HotArmorContext;
import cn.bafuka.hotarmor.dataplane.L1CacheEngine;
import cn.bafuka.hotarmor.dataplane.L4SafeLoader;
import cn.bafuka.hotarmor.model.HotArmorRule;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 一致性管理器默认实现
 * 协调延迟双删和广播通知
 */
@Slf4j
public class DefaultConsistencyManager implements ConsistencyManager {

    /**
     * L1 缓存引擎（用于清理本地缓存）
     */
    private final L1CacheEngine<?> l1CacheEngine;

    /**
     * L4 安全回源器（用于删除 Redis）
     */
    private final L4SafeLoader<?> l4SafeLoader;

    /**
     * 延迟双删生产者
     */
    private final DelayedDeleteProducer delayedDeleteProducer;

    /**
     * 广播通知器
     */
    private final BroadcastNotifier broadcastNotifier;

    /**
     * 一致性配置缓存
     */
    private final Map<String, HotArmorRule.ConsistencyConfig> configMap = new ConcurrentHashMap<>();

    /**
     * 延迟删除调度器（用于没有 RocketMQ 时的后备方案）
     */
    private final ScheduledExecutorService delayedDeleteScheduler =
            Executors.newScheduledThreadPool(2, r -> {
                Thread thread = new Thread(r, "hotarmor-delayed-delete");
                thread.setDaemon(true);
                return thread;
            });

    /**
     * 是否已初始化
     */
    private volatile boolean initialized = false;

    public DefaultConsistencyManager(L1CacheEngine<?> l1CacheEngine,
                                    L4SafeLoader<?> l4SafeLoader,
                                    DelayedDeleteProducer delayedDeleteProducer,
                                    BroadcastNotifier broadcastNotifier) {
        this.l1CacheEngine = l1CacheEngine;
        this.l4SafeLoader = l4SafeLoader;
        this.delayedDeleteProducer = delayedDeleteProducer;
        this.broadcastNotifier = broadcastNotifier;
    }

    @Override
    public void initialize() {
        if (initialized) {
            log.debug("一致性管理器已经初始化，跳过");
            return;
        }

        log.info("初始化一致性管理器...");

        // 订阅广播消息（如果 broadcastNotifier 可用）
        if (broadcastNotifier != null) {
            broadcastNotifier.subscribe(new LocalCacheInvalidationListener());
            broadcastNotifier.subscribePromotion(new LocalCachePromotionListener());
            log.info("已订阅缓存失效和热点晋升广播");
        } else {
            log.info("BroadcastNotifier 未配置，跳过广播订阅");
        }

        initialized = true;
        log.info("一致性管理器初始化成功");
    }

    @Override
    public void handleUpdate(HotArmorContext context) {
        if (context == null || context.getResource() == null) {
            return;
        }

        HotArmorRule.ConsistencyConfig config = configMap.get(context.getResource());
        if (config == null) {
            // 未配置一致性策略，只删除缓存
            invalidateCache(context);
            return;
        }

        log.info("处理更新（一致性保证）: resource={}, key={}",
                context.getResource(), context.getKey());

        // 1. 立即删除缓存（第一次删除）
        if (delayedDeleteProducer != null) {
            delayedDeleteProducer.deleteImmediately(context);
        }
        invalidateCache(context);

        // 2. 发送延迟删除消息（如果启用）
        if (config.isEnableDelayedDoubleDelete()) {
            if (delayedDeleteProducer != null) {
                // 使用 RocketMQ 实现延迟双删
                delayedDeleteProducer.sendDelayedDelete(context, config.getDelayTimeMs());
                log.debug("使用 RocketMQ 延迟双删: delayMs={}", config.getDelayTimeMs());
            } else {
                // 使用本地调度器作为后备方案
                long delayMs = config.getDelayTimeMs();
                delayedDeleteScheduler.schedule(() -> {
                    try {
                        log.info("执行延迟双删（第二次删除）: resource={}, key={}",
                                context.getResource(), context.getKey());
                        invalidateCache(context);
                    } catch (Exception e) {
                        log.error("延迟双删失败: resource={}, key={}",
                                context.getResource(), context.getKey(), e);
                    }
                }, delayMs, TimeUnit.MILLISECONDS);
                log.debug("使用本地调度器延迟双删: delayMs={}", delayMs);
            }
        }

        // 3. 发送广播通知（如果启用）
        if (config.isEnableBroadcast() && broadcastNotifier != null) {
            broadcastNotifier.publish(context);
        } else if (config.isEnableBroadcast() && broadcastNotifier == null) {
            log.warn("广播通知已启用，但 BroadcastNotifier 未配置");
        }

        log.info("更新处理完成: resource={}, key={}, delayedDelete={}, broadcast={}",
                context.getResource(), context.getKey(),
                config.isEnableDelayedDoubleDelete(), config.isEnableBroadcast());
    }

    @Override
    public void invalidateCache(HotArmorContext context) {
        if (context == null || context.getResource() == null) {
            return;
        }

        log.info("缓存失效: resource={}, key={}", context.getResource(), context.getKey());

        // 删除 L1 本地缓存
        l1CacheEngine.invalidate(context);

        // 删除 Redis
        l4SafeLoader.deleteFromRedis(context);
    }

    @Override
    public void handlePromotion(HotArmorContext context, Object value) {
        if (context == null || context.getResource() == null) {
            return;
        }

        HotArmorRule.ConsistencyConfig config = configMap.get(context.getResource());
        if (config == null || !config.isEnablePromotionBroadcast()) {
            // 未配置或未启用热点晋升广播
            return;
        }

        log.info("发送热点晋升广播: resource={}, key={}",
                context.getResource(), context.getKey());

        // 发送热点晋升广播（如果启用）
        if (broadcastNotifier != null) {
            broadcastNotifier.publishPromotion(context, value);
        } else {
            log.warn("热点晋升广播已启用，但 BroadcastNotifier 未配置");
        }
    }

    @Override
    public void shutdown() {
        if (!initialized) {
            return;
        }

        log.info("关闭一致性管理器...");

        // 取消订阅（如果 broadcastNotifier 可用）
        if (broadcastNotifier != null) {
            broadcastNotifier.unsubscribe();
            log.info("已取消订阅广播通知");
        }

        // 优雅关闭延迟删除调度器
        shutdownDelayedDeleteScheduler();

        initialized = false;
        log.info("一致性管理器已关闭");
    }

    /**
     * 优雅关闭延迟删除调度器
     */
    private void shutdownDelayedDeleteScheduler() {
        try {
            delayedDeleteScheduler.shutdown(); // 不再接受新任务

            // 增加等待时间，支持配置（未来可以从配置读取）
            long shutdownTimeoutSeconds = 30;
            log.info("等待延迟删除调度器关闭，超时时间: {} 秒", shutdownTimeoutSeconds);

            if (!delayedDeleteScheduler.awaitTermination(shutdownTimeoutSeconds, TimeUnit.SECONDS)) {
                log.warn("延迟删除调度器未在 {} 秒内完成，强制关闭", shutdownTimeoutSeconds);

                // 记录被丢弃的任务
                java.util.List<Runnable> pendingTasks = delayedDeleteScheduler.shutdownNow();
                if (pendingTasks != null && !pendingTasks.isEmpty()) {
                    log.warn("丢弃了 {} 个待执行的延迟删除任务", pendingTasks.size());

                    // 记录详细的未执行任务信息
                    for (int i = 0; i < Math.min(pendingTasks.size(), 10); i++) {
                        log.debug("未执行的任务 #{}: {}", i + 1, pendingTasks.get(i));
                    }
                    if (pendingTasks.size() > 10) {
                        log.debug("还有 {} 个任务未记录...", pendingTasks.size() - 10);
                    }
                }

                log.info("延迟删除调度器已强制关闭");
            } else {
                log.info("延迟删除调度器已正常关闭");
            }
        } catch (InterruptedException e) {
            log.error("关闭被中断", e);
            java.util.List<Runnable> pendingTasks = delayedDeleteScheduler.shutdownNow();
            if (pendingTasks != null && !pendingTasks.isEmpty()) {
                log.warn("强制关闭，丢弃了 {} 个任务", pendingTasks.size());
            }
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 注册一致性配置
     *
     * @param resource 资源名称
     * @param config   一致性配置
     */
    public void registerConfig(String resource, HotArmorRule.ConsistencyConfig config) {
        configMap.put(resource, config);
        log.info("已注册一致性配置: resource={}", resource);
    }

    /**
     * 本地缓存失效监听器
     * 当收到广播消息时，清理 L1 本地缓存
     */
    private class LocalCacheInvalidationListener implements InvalidationListener {

        @Override
        public void onInvalidate(HotArmorContext context) {
            if (context == null || context.getResource() == null) {
                return;
            }

            log.info("收到缓存失效广播，清理 L1 缓存: resource={}, key={}",
                    context.getResource(), context.getKey());

            // 清理 L1 本地缓存
            l1CacheEngine.invalidate(context);

            log.debug("L1 缓存已清理: resource={}, key={}",
                    context.getResource(), context.getKey());
        }
    }

    /**
     * 本地缓存晋升监听器
     * 当收到热点晋升广播时，将热点数据晋升到 L1 本地缓存
     */
    private class LocalCachePromotionListener implements PromotionListener {

        @Override
        @SuppressWarnings("unchecked")
        public void onPromote(HotArmorContext context, Object value) {
            if (context == null || context.getResource() == null) {
                return;
            }

            log.info("收到热点晋升广播，晋升到 L1 缓存: resource={}, key={}",
                    context.getResource(), context.getKey());

            // 如果广播中携带了缓存值，直接使用
            if (value != null) {
                ((L1CacheEngine<Object>) l1CacheEngine).put(context, value);
                log.info("热点数据已晋升到 L1（使用广播值）: resource={}, key={}",
                        context.getResource(), context.getKey());
            } else {
                // 如果没有携带值，从 Redis 加载后晋升
                Object cachedValue = l4SafeLoader.getFromRedis(context);
                if (cachedValue != null) {
                    ((L1CacheEngine<Object>) l1CacheEngine).put(context, cachedValue);
                    log.info("热点数据已晋升到 L1（从Redis加载）: resource={}, key={}",
                            context.getResource(), context.getKey());
                } else {
                    log.warn("热点晋升失败，Redis中无数据: resource={}, key={}",
                            context.getResource(), context.getKey());
                }
            }
        }
    }
}
