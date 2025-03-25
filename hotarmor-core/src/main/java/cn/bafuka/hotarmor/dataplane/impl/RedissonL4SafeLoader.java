package cn.bafuka.hotarmor.dataplane.impl;

import cn.bafuka.hotarmor.core.HotArmorContext;
import cn.bafuka.hotarmor.dataplane.L4SafeLoader;
import cn.bafuka.hotarmor.model.HotArmorRule;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * L4 安全回源器实现
 * 基于 Redisson 分布式锁，防止缓存击穿
 *
 * @param <V> 数据类型
 */
@Slf4j
public class RedissonL4SafeLoader<V> implements L4SafeLoader<V> {

    /**
     * Redisson 客户端
     */
    private final RedissonClient redissonClient;

    /**
     * Redis 模板
     */
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 配置缓存
     */
    private final Map<String, HotArmorRule.L4LoaderConfig> configMap = new ConcurrentHashMap<>();

    public RedissonL4SafeLoader(RedissonClient redissonClient, RedisTemplate<String, Object> redisTemplate) {
        this.redissonClient = redissonClient;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 注册配置
     *
     * @param resource 资源名称
     * @param config   L4 配置
     */
    public void registerConfig(String resource, HotArmorRule.L4LoaderConfig config) {
        configMap.put(resource, config);
    }

    @Override
    public V load(HotArmorContext context, Function<Object, V> dbLoader) {
        if (context == null || context.getResource() == null) {
            return null;
        }

        // 1. 先查 Redis
        V value = getFromRedis(context);
        if (value != null) {
            log.debug("L4 回源命中 Redis: resource={}, key={}", context.getResource(), context.getKey());
            return value;
        }

        // 2. Redis 未命中，使用分布式锁防止击穿
        String lockKey = getLockKey(context);
        RLock lock = redissonClient.getLock(lockKey);

        HotArmorRule.L4LoaderConfig config = configMap.get(context.getResource());
        if (config == null) {
            // 未配置，直接查 DB
            return loadFromDb(context, dbLoader);
        }

        try {
            // 尝试获取锁
            boolean locked = lock.tryLock(config.getLockWaitTimeMs(), config.getLockLeaseTimeMs(), TimeUnit.MILLISECONDS);

            if (locked) {
                try {
                    // 获取到锁，Double-Check
                    value = getFromRedis(context);
                    if (value != null) {
                        log.debug("L4 回源二次检查命中 Redis: resource={}, key={}",
                                context.getResource(), context.getKey());
                        return value;
                    }

                    // 仍然未命中，查询 DB
                    value = loadFromDb(context, dbLoader);

                    // 回写 Redis
                    if (value != null) {
                        putToRedis(context, value);
                    }

                    return value;

                } finally {
                    lock.unlock();
                }
            } else {
                // 未获取到锁，说明有其他线程正在加载数据
                // 使用重试机制等待其他线程完成加载，而不是立即降级查 DB
                log.debug("L4 回源未获取到锁，等待其他线程完成加载: resource={}, key={}",
                        context.getResource(), context.getKey());

                // 重试配置
                int maxRetries = 5;  // 最多重试 5 次
                int retryCount = 0;
                long retryDelayMs = 100;  // 初始延迟 100ms

                while (retryCount < maxRetries) {
                    Thread.sleep(retryDelayMs);

                    // 重新检查 Redis
                    value = getFromRedis(context);
                    if (value != null) {
                        log.debug("重试成功，从 Redis 获取到数据: retry={}, resource={}, key={}",
                                retryCount + 1, context.getResource(), context.getKey());
                        return value;
                    }

                    retryCount++;
                    // 指数退避，但最大不超过 500ms
                    retryDelayMs = Math.min(retryDelayMs * 2, 500);
                }

                // 重试多次后仍未获取到数据，降级查 DB
                log.warn("等待超时，降级查询 DB: resource={}, key={}, retries={}",
                        context.getResource(), context.getKey(), maxRetries);

                // 降级时查询 DB
                value = loadFromDb(context, dbLoader);

                // 降级加载的数据也应该回写 Redis，使用较短的 TTL（避免后续请求继续查 DB）
                if (value != null) {
                    try {
                        // 使用较短的 TTL（60 秒），避免降级数据长期存在
                        String redisKey = getRedisKey(context);
                        redisTemplate.opsForValue().set(redisKey, value, 60, TimeUnit.SECONDS);
                        log.debug("降级数据已回写 Redis: resource={}, key={}, ttl=60s",
                                context.getResource(), context.getKey());
                    } catch (Exception e) {
                        log.error("降级数据回写 Redis 失败: resource={}, key={}",
                                context.getResource(), context.getKey(), e);
                    }
                }

                return value;
            }

        } catch (InterruptedException e) {
            log.error("L4 回源被中断: resource={}, key={}", context.getResource(), context.getKey(), e);
            Thread.currentThread().interrupt();
            return loadFromDb(context, dbLoader);

        } catch (Exception e) {
            log.error("L4 回源异常: resource={}, key={}", context.getResource(), context.getKey(), e);
            return loadFromDb(context, dbLoader);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public V getFromRedis(HotArmorContext context) {
        if (context == null || context.getResource() == null) {
            return null;
        }

        try {
            String redisKey = getRedisKey(context);
            Object value = redisTemplate.opsForValue().get(redisKey);
            return (V) value;
        } catch (Exception e) {
            log.error("从 Redis 获取数据失败: resource={}, key={}",
                    context.getResource(), context.getKey(), e);
            return null;
        }
    }

    @Override
    public void putToRedis(HotArmorContext context, V value) {
        if (context == null || context.getResource() == null || value == null) {
            return;
        }

        try {
            String redisKey = getRedisKey(context);
            HotArmorRule.L4LoaderConfig config = configMap.get(context.getResource());
            int ttl = config != null ? config.getRedisTtlSeconds() : 300;

            redisTemplate.opsForValue().set(redisKey, value, ttl, TimeUnit.SECONDS);
            log.debug("L4 回源写入 Redis: resource={}, key={}, ttl={}s",
                    context.getResource(), context.getKey(), ttl);

        } catch (Exception e) {
            log.error("写入 Redis 失败: resource={}, key={}",
                    context.getResource(), context.getKey(), e);
        }
    }

    @Override
    public void deleteFromRedis(HotArmorContext context) {
        if (context == null || context.getResource() == null) {
            return;
        }

        try {
            String redisKey = getRedisKey(context);
            redisTemplate.delete(redisKey);
            log.debug("L4 回源从 Redis 删除: resource={}, key={}",
                    context.getResource(), context.getKey());

        } catch (Exception e) {
            log.error("从 Redis 删除失败: resource={}, key={}",
                    context.getResource(), context.getKey(), e);
        }
    }

    @Override
    public String getRedisKey(HotArmorContext context) {
        HotArmorRule.L4LoaderConfig config = configMap.get(context.getResource());
        String prefix = config != null ? config.getRedisKeyPrefix() : "hotarmor:";
        return prefix + context.getResource() + ":" + context.getKey();
    }

    /**
     * 获取分布式锁的键
     *
     * @param context 上下文
     * @return 锁键
     */
    private String getLockKey(HotArmorContext context) {
        return "lock:" + getRedisKey(context);
    }

    /**
     * 从数据库加载数据
     *
     * @param context  上下文
     * @param dbLoader 数据库加载函数
     * @return 数据
     */
    private V loadFromDb(HotArmorContext context, Function<Object, V> dbLoader) {
        try {
            log.debug("L4 回源从数据库加载: resource={}, key={}",
                    context.getResource(), context.getKey());

            V value = dbLoader.apply(context.getKey());

            if (value != null) {
                log.debug("L4 回源数据库加载成功: resource={}, key={}",
                        context.getResource(), context.getKey());
            } else {
                log.debug("L4 回源数据库返回空值: resource={}, key={}",
                        context.getResource(), context.getKey());
            }

            return value;

        } catch (Exception e) {
            log.error("L4 回源数据库加载失败: resource={}, key={}",
                    context.getResource(), context.getKey(), e);
            return null;
        }
    }
}
