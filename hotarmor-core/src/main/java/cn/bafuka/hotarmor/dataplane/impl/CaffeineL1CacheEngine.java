package cn.bafuka.hotarmor.dataplane.impl;

import cn.bafuka.hotarmor.core.HotArmorContext;
import cn.bafuka.hotarmor.dataplane.L1CacheEngine;
import cn.bafuka.hotarmor.model.HotArmorRule;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * L1 本地缓存引擎实现
 * 基于 Caffeine 的高性能本地缓存
 *
 * @param <V> 缓存值类型
 */
@Slf4j
public class CaffeineL1CacheEngine<V> implements L1CacheEngine<V> {

    /**
     * 多资源缓存容器
     * Key: resource 名称
     * Value: 对应的 Caffeine Cache 实例
     */
    private final Map<String, Cache<Object, V>> cacheMap = new ConcurrentHashMap<>();

    /**
     * 获取或创建指定资源的缓存实例
     *
     * @param resource 资源名称
     * @param config   L1 配置
     * @return Caffeine Cache 实例
     */
    public Cache<Object, V> getOrCreateCache(String resource, HotArmorRule.L1CacheConfig config) {
        return cacheMap.computeIfAbsent(resource, k -> buildCache(config));
    }

    /**
     * 根据配置构建 Caffeine Cache
     *
     * @param config L1 配置
     * @return Caffeine Cache 实例
     */
    private Cache<Object, V> buildCache(HotArmorRule.L1CacheConfig config) {
        Caffeine<Object, Object> builder = Caffeine.newBuilder()
                .maximumSize(config.getMaximumSize())
                .expireAfterWrite(config.getExpireAfterWrite(), config.getTimeUnit())
                .recordStats(); // 启用统计信息

        log.info("构建 L1 缓存，配置: maximumSize={}, expireAfterWrite={} {}",
                config.getMaximumSize(), config.getExpireAfterWrite(), config.getTimeUnit());

        return builder.build();
    }

    /**
     * 重建指定资源的缓存实例（用于配置热更新）
     *
     * @param resource 资源名称
     * @param config   新的 L1 配置
     */
    public void rebuildCache(String resource, HotArmorRule.L1CacheConfig config) {
        log.info("重建 L1 缓存: resource={}", resource);
        Cache<Object, V> oldCache = cacheMap.remove(resource);
        if (oldCache != null) {
            oldCache.invalidateAll();
            oldCache.cleanUp();
        }
        cacheMap.put(resource, buildCache(config));
    }

    @Override
    public V get(HotArmorContext context) {
        if (context == null || context.getResource() == null) {
            return null;
        }

        Cache<Object, V> cache = cacheMap.get(context.getResource());
        if (cache == null) {
            return null;
        }

        V value = cache.getIfPresent(context.getKey());
        if (value != null) {
            log.debug("L1 缓存命中: resource={}, key={}", context.getResource(), context.getKey());
        } else {
            log.debug("L1 缓存未命中: resource={}, key={}", context.getResource(), context.getKey());
        }
        return value;
    }

    @Override
    public void put(HotArmorContext context, V value) {
        if (context == null || context.getResource() == null || value == null) {
            return;
        }

        Cache<Object, V> cache = cacheMap.get(context.getResource());
        if (cache == null) {
            log.warn("L1 缓存未找到: resource={}", context.getResource());
            return;
        }

        cache.put(context.getKey(), value);
        log.debug("L1 缓存写入: resource={}, key={}", context.getResource(), context.getKey());
    }

    @Override
    public void invalidate(HotArmorContext context) {
        if (context == null || context.getResource() == null) {
            return;
        }

        Cache<Object, V> cache = cacheMap.get(context.getResource());
        if (cache == null) {
            return;
        }

        cache.invalidate(context.getKey());
        log.debug("L1 缓存失效: resource={}, key={}", context.getResource(), context.getKey());
    }

    @Override
    public void invalidateResource(String resource) {
        Cache<Object, V> cache = cacheMap.get(resource);
        if (cache == null) {
            return;
        }

        cache.invalidateAll();
        log.info("L1 缓存全部失效: resource={}", resource);
    }

    @Override
    public String getStats(String resource) {
        Cache<Object, V> cache = cacheMap.get(resource);
        if (cache == null) {
            return "缓存未找到: resource=" + resource;
        }

        com.github.benmanes.caffeine.cache.stats.CacheStats stats = cache.stats();
        return String.format(
                "L1 Cache Stats [%s]: hitRate=%.2f%%, hitCount=%d, missCount=%d, evictionCount=%d",
                resource,
                stats.hitRate() * 100,
                stats.hitCount(),
                stats.missCount(),
                stats.evictionCount()
        );
    }

    /**
     * 获取所有缓存实例（用于监控）
     *
     * @return 缓存实例映射
     */
    public Map<String, Cache<Object, V>> getAllCaches() {
        return cacheMap;
    }
}
