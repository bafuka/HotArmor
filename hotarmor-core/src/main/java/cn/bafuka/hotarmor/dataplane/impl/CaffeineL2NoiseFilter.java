package cn.bafuka.hotarmor.dataplane.impl;

import cn.bafuka.hotarmor.core.HotArmorContext;
import cn.bafuka.hotarmor.dataplane.L2NoiseFilter;
import cn.bafuka.hotarmor.model.HotArmorRule;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * L2 噪音过滤器实现
 * 基于轻量级计数器，过滤冷门长尾流量，保护 Sentinel
 */
@Slf4j
public class CaffeineL2NoiseFilter implements L2NoiseFilter {

    /**
     * 计数器缓存容器
     * Key: resource 名称
     * Value: 对应的计数器缓存
     */
    private final Map<String, Cache<Object, AtomicLong>> counterMap = new ConcurrentHashMap<>();

    /**
     * 配置缓存
     */
    private final Map<String, HotArmorRule.L2FilterConfig> configMap = new ConcurrentHashMap<>();

    /**
     * 获取或创建计数器缓存
     *
     * @param resource 资源名称
     * @param config   L2 配置
     * @return 计数器缓存
     */
    public Cache<Object, AtomicLong> getOrCreateCounter(String resource, HotArmorRule.L2FilterConfig config) {
        configMap.put(resource, config);
        return counterMap.computeIfAbsent(resource, k -> buildCounter(config));
    }

    /**
     * 构建计数器缓存
     *
     * @param config L2 配置
     * @return 计数器缓存
     */
    private Cache<Object, AtomicLong> buildCounter(HotArmorRule.L2FilterConfig config) {
        log.info("构建 L2 计数器缓存，配置: windowSeconds={}, threshold={}",
                config.getWindowSeconds(), config.getThreshold());

        return Caffeine.newBuilder()
                .expireAfterWrite(config.getWindowSeconds(), TimeUnit.SECONDS)
                .maximumSize(100000) // 最多缓存 10 万个键的计数
                .build();
    }

    /**
     * 重建计数器（用于配置热更新）
     *
     * @param resource 资源名称
     * @param config   新的 L2 配置
     */
    public void rebuildCounter(String resource, HotArmorRule.L2FilterConfig config) {
        log.info("重建 L2 计数器: resource={}", resource);
        configMap.put(resource, config);
        Cache<Object, AtomicLong> oldCounter = counterMap.remove(resource);
        if (oldCounter != null) {
            oldCounter.invalidateAll();
            oldCounter.cleanUp();
        }
        counterMap.put(resource, buildCounter(config));
    }

    @Override
    public boolean shouldPass(HotArmorContext context) {
        if (context == null || context.getResource() == null) {
            return false;
        }

        HotArmorRule.L2FilterConfig config = configMap.get(context.getResource());
        if (config == null || !config.isEnabled()) {
            // 未配置或未启用，直接通过
            return true;
        }

        Cache<Object, AtomicLong> counter = counterMap.get(context.getResource());
        if (counter == null) {
            return false;
        }

        // 原子递增计数器
        AtomicLong count = counter.get(context.getKey(), k -> new AtomicLong(0));
        long currentCount = count.incrementAndGet();

        boolean pass = currentCount >= config.getThreshold();

        if (pass) {
            log.debug("L2 过滤器通过: resource={}, key={}, count={}, threshold={}",
                    context.getResource(), context.getKey(), currentCount, config.getThreshold());
        } else {
            log.debug("L2 过滤器拦截（冷数据）: resource={}, key={}, count={}, threshold={}",
                    context.getResource(), context.getKey(), currentCount, config.getThreshold());
        }

        return pass;
    }

    @Override
    public void reset(String resource) {
        Cache<Object, AtomicLong> counter = counterMap.get(resource);
        if (counter != null) {
            counter.invalidateAll();
            log.info("L2 计数器重置: resource={}", resource);
        }
    }

    @Override
    public long getCount(HotArmorContext context) {
        if (context == null || context.getResource() == null) {
            return 0;
        }

        Cache<Object, AtomicLong> counter = counterMap.get(context.getResource());
        if (counter == null) {
            return 0;
        }

        AtomicLong count = counter.getIfPresent(context.getKey());
        return count == null ? 0 : count.get();
    }

    /**
     * 获取所有计数器（用于监控）
     *
     * @return 计数器映射
     */
    public Map<String, Cache<Object, AtomicLong>> getAllCounters() {
        return counterMap;
    }
}
