package cn.bafuka.hotarmor.aspect.impl;

import cn.bafuka.hotarmor.aspect.HotArmorAspectHandler;
import cn.bafuka.hotarmor.consistency.ConsistencyManager;
import cn.bafuka.hotarmor.core.HotArmorContext;
import cn.bafuka.hotarmor.dataplane.L1CacheEngine;
import cn.bafuka.hotarmor.dataplane.L2NoiseFilter;
import cn.bafuka.hotarmor.dataplane.L3HotspotDetector;
import cn.bafuka.hotarmor.dataplane.L4SafeLoader;
import cn.bafuka.hotarmor.exception.HotArmorLoadException;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;

import java.util.function.Function;

/**
 * HotArmor 切面处理器默认实现
 * 执行三级漏斗逻辑
 */
@Slf4j
public class DefaultHotArmorAspectHandler implements HotArmorAspectHandler {

    /**
     * L1 缓存引擎
     */
    private final L1CacheEngine<Object> l1CacheEngine;

    /**
     * L2 噪音过滤器
     */
    private final L2NoiseFilter l2NoiseFilter;

    /**
     * L3 热点探测器
     */
    private final L3HotspotDetector l3HotspotDetector;

    /**
     * L4 安全回源器
     */
    private final L4SafeLoader<Object> l4SafeLoader;

    /**
     * 一致性管理器
     */
    private final ConsistencyManager consistencyManager;

    public DefaultHotArmorAspectHandler(L1CacheEngine<Object> l1CacheEngine,
                                       L2NoiseFilter l2NoiseFilter,
                                       L3HotspotDetector l3HotspotDetector,
                                       L4SafeLoader<Object> l4SafeLoader,
                                       ConsistencyManager consistencyManager) {
        this.l1CacheEngine = l1CacheEngine;
        this.l2NoiseFilter = l2NoiseFilter;
        this.l3HotspotDetector = l3HotspotDetector;
        this.l4SafeLoader = l4SafeLoader;
        this.consistencyManager = consistencyManager;
    }

    @Override
    public Object handleCache(ProceedingJoinPoint joinPoint, HotArmorContext context) throws Throwable {
        if (context == null || context.getResource() == null) {
            // 无效的上下文，直接执行原方法
            return joinPoint.proceed();
        }

        log.debug("处理缓存: resource={}, key={}", context.getResource(), context.getKey());

        // === 三级漏斗处理 ===

        // L1: 检查本地缓存
        Object value = l1CacheEngine.get(context);
        if (value != null) {
            log.debug("L1 缓存命中，直接返回: resource={}, key={}",
                    context.getResource(), context.getKey());
            return value;
        }

        // L2: 噪音过滤
        boolean shouldPassToL3 = l2NoiseFilter.shouldPass(context);
        if (!shouldPassToL3) {
            // 冷数据，直接安全回源
            log.debug("L2 过滤（冷数据），从 L4: resource={}, key={}",
                    context.getResource(), context.getKey());
            return loadFromSource(joinPoint, context, false);
        }

        // L3: 热点判定
        boolean isHotspot = l3HotspotDetector.isHotspot(context);
        if (isHotspot) {
            // 热点！晋升到 L1
            log.info("L3 检测到热点，提升到 L1: resource={}, key={}",
                    context.getResource(), context.getKey());
            return loadFromSource(joinPoint, context, true);
        } else {
            // 不是热点，正常回源（不晋升）
            log.debug("L3 Pass（不是hotspot），从 L4: resource={}, key={}",
                    context.getResource(), context.getKey());
            return loadFromSource(joinPoint, context, false);
        }
    }

    @Override
    public Object handleEvict(ProceedingJoinPoint joinPoint, HotArmorContext context,
                             boolean beforeInvocation, boolean delayedDelete, boolean broadcast) throws Throwable {
        if (context == null || context.getResource() == null) {
            // 无效的上下文，直接执行原方法
            return joinPoint.proceed();
        }

        log.info("处理驱逐: resource={}, key={}, beforeInvocation={}, delayedDelete={}, broadcast={}",
                context.getResource(), context.getKey(), beforeInvocation, delayedDelete, broadcast);

        try {
            if (beforeInvocation) {
                // 方法执行前删除
                handleEviction(context, delayedDelete, broadcast);
                return joinPoint.proceed();
            } else {
                // 方法执行后删除
                Object result = joinPoint.proceed();
                handleEviction(context, delayedDelete, broadcast);
                return result;
            }
        } catch (Throwable e) {
            log.error("未能处理驱逐: resource={}, key={}",
                    context.getResource(), context.getKey(), e);
            throw e;
        }
    }

    /**
     * 从数据源加载数据
     *
     * @param joinPoint     切点
     * @param context       上下文
     * @param promoteToL1   是否晋升到 L1
     * @return 数据
     * @throws Throwable 异常
     */
    private Object loadFromSource(ProceedingJoinPoint joinPoint, HotArmorContext context, boolean promoteToL1) throws Throwable {
        // L4: 安全回源（从 Redis 或 DB 加载）
        Function<Object, Object> dbLoader = key -> {
            try {
                long startTime = System.currentTimeMillis();
                // 调用原方法从数据库加载
                Object result = joinPoint.proceed();
                long duration = System.currentTimeMillis() - startTime;

                log.debug("数据库加载成功: resource={}, key={}, duration={}ms",
                        context.getResource(), context.getKey(), duration);

                return result;

            } catch (Throwable e) {
                // 保留原始异常，添加详细上下文
                log.error("数据库加载失败: resource={}, key={}, method={}.{}, error={}",
                        context.getResource(),
                        context.getKey(),
                        context.getTargetClass() != null ? context.getTargetClass().getSimpleName() : "Unknown",
                        context.getMethodName() != null ? context.getMethodName() : "unknown",
                        e.getMessage(),
                        e);

                // 包装成自定义异常，保留原始异常链
                throw new HotArmorLoadException(
                        String.format("Failed to load from DB: resource=%s, key=%s",
                                context.getResource(), context.getKey()),
                        e,
                        context,
                        determineFailureReason(e)
                );
            }
        };

        try {
            Object value = l4SafeLoader.load(context, dbLoader);

            // 如果是热点，晋升到 L1
            if (promoteToL1 && value != null) {
                l1CacheEngine.put(context, value);
                log.info("晋升为 L1 cache: resource={}, key={}", context.getResource(), context.getKey());

                // 发送热点晋升广播，通知其他节点
                consistencyManager.handlePromotion(context, value);
            }

            return value;

        } catch (HotArmorLoadException e) {
            // 根据失败原因做差异化处理
            handleLoadException(e);
            throw e;
        }
    }

    /**
     * 根据异常类型判断失败原因
     *
     * @param e 异常
     * @return 失败原因
     */
    private HotArmorLoadException.LoadFailureReason determineFailureReason(Throwable e) {
        if (e == null) {
            return HotArmorLoadException.LoadFailureReason.UNKNOWN;
        }

        // 检查 SQL 异常
        if (e instanceof java.sql.SQLException) {
            return HotArmorLoadException.LoadFailureReason.DATABASE_ERROR;
        }

        // 检查 Spring DAO 异常
        String className = e.getClass().getName();
        if (className.contains("org.springframework.dao")) {
            return HotArmorLoadException.LoadFailureReason.DATABASE_ERROR;
        }

        // 检查超时异常
        if (e instanceof java.util.concurrent.TimeoutException) {
            return HotArmorLoadException.LoadFailureReason.TIMEOUT;
        }

        // 检查 Redis 异常
        if (e.getMessage() != null &&
                (e.getMessage().contains("Redis") || e.getMessage().contains("redis"))) {
            return HotArmorLoadException.LoadFailureReason.REDIS_ERROR;
        }

        // 检查锁相关异常
        if (className.contains("Lock") ||
                (e.getMessage() != null && e.getMessage().contains("lock"))) {
            return HotArmorLoadException.LoadFailureReason.LOCK_FAILURE;
        }

        return HotArmorLoadException.LoadFailureReason.UNKNOWN;
    }

    /**
     * 处理加载异常
     *
     * @param e 加载异常
     */
    private void handleLoadException(HotArmorLoadException e) {
        switch (e.getReason()) {
            case DATABASE_ERROR:
                // 数据库错误，可能需要降级或熔断
                log.error("数据库错误，考虑熔断: resource={}, key={}, message={}",
                        e.getContext().getResource(),
                        e.getContext().getKey(),
                        e.getMessage());
                break;

            case TIMEOUT:
                // 超时，可能需要调整超时配置
                log.error("加载超时: resource={}, key={}, message={}",
                        e.getContext().getResource(),
                        e.getContext().getKey(),
                        e.getMessage());
                break;

            case REDIS_ERROR:
                // Redis 错误，可以继续查 DB
                log.warn("Redis 错误，已降级到 DB: resource={}, key={}, message={}",
                        e.getContext().getResource(),
                        e.getContext().getKey(),
                        e.getMessage());
                break;

            case LOCK_FAILURE:
                // 锁获取失败
                log.warn("锁获取失败: resource={}, key={}, message={}",
                        e.getContext().getResource(),
                        e.getContext().getKey(),
                        e.getMessage());
                break;

            default:
                log.error("未知错误: resource={}, key={}, message={}",
                        e.getContext().getResource(),
                        e.getContext().getKey(),
                        e.getMessage());
        }
    }

    /**
     * 处理缓存失效
     *
     * @param context       上下文
     * @param delayedDelete 是否延迟双删
     * @param broadcast     是否广播
     */
    private void handleEviction(HotArmorContext context, boolean delayedDelete, boolean broadcast) {
        if (delayedDelete || broadcast) {
            // 使用一致性管理器处理
            consistencyManager.handleUpdate(context);
        } else {
            // 只删除缓存
            consistencyManager.invalidateCache(context);
        }
    }
}
