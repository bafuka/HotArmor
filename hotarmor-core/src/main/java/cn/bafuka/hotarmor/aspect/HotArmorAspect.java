package cn.bafuka.hotarmor.aspect;

import cn.bafuka.hotarmor.annotation.HotArmorCache;
import cn.bafuka.hotarmor.annotation.HotArmorEvict;
import cn.bafuka.hotarmor.core.HotArmorContext;
import cn.bafuka.hotarmor.spel.SpelExpressionParser;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

/**
 * HotArmor AOP 切面
 * 拦截 @HotArmorCache 和 @HotArmorEvict 注解
 */
@Slf4j
@Aspect
public class HotArmorAspect {

    /**
     * SpEL 表达式解析器
     */
    private final SpelExpressionParser spelParser;

    /**
     * 切面处理器
     */
    private final HotArmorAspectHandler aspectHandler;

    public HotArmorAspect(SpelExpressionParser spelParser, HotArmorAspectHandler aspectHandler) {
        this.spelParser = spelParser;
        this.aspectHandler = aspectHandler;
    }

    /**
     * 拦截 @HotArmorCache 注解
     */
    @Around("@annotation(hotArmorCache)")
    public Object aroundCache(ProceedingJoinPoint joinPoint, HotArmorCache hotArmorCache) throws Throwable {
        // 检查是否启用
        if (!hotArmorCache.enabled()) {
            return joinPoint.proceed();
        }

        // 解析条件表达式
        if (hotArmorCache.condition() != null && !hotArmorCache.condition().isEmpty()) {
            boolean conditionMet = spelParser.parseCondition(
                    hotArmorCache.condition(),
                    joinPoint,
                    joinPoint.getTarget().getClass(),
                    joinPoint.getSignature().getName()
            );

            if (!conditionMet) {
                log.debug("Condition not met, skipping cache: resource={}", hotArmorCache.resource());
                return joinPoint.proceed();
            }
        }

        // 解析缓存键
        Object key = spelParser.parseKey(
                hotArmorCache.key(),
                joinPoint,
                joinPoint.getTarget().getClass(),
                joinPoint.getSignature().getName()
        );

        if (key == null) {
            log.warn("Failed to parse cache key, skipping: resource={}", hotArmorCache.resource());
            return joinPoint.proceed();
        }

        // 构建上下文
        HotArmorContext context = HotArmorContext.builder()
                .resource(hotArmorCache.resource())
                .key(key)
                .args(joinPoint.getArgs())
                .targetClass(joinPoint.getTarget().getClass())
                .methodName(joinPoint.getSignature().getName())
                .build();

        // 委托给处理器
        return aspectHandler.handleCache(joinPoint, context);
    }

    /**
     * 拦截 @HotArmorEvict 注解
     */
    @Around("@annotation(hotArmorEvict)")
    public Object aroundEvict(ProceedingJoinPoint joinPoint, HotArmorEvict hotArmorEvict) throws Throwable {
        // 解析缓存键
        Object key = spelParser.parseKey(
                hotArmorEvict.key(),
                joinPoint,
                joinPoint.getTarget().getClass(),
                joinPoint.getSignature().getName()
        );

        if (key == null) {
            log.warn("Failed to parse cache key for evict, skipping: resource={}", hotArmorEvict.resource());
            return joinPoint.proceed();
        }

        // 构建上下文
        HotArmorContext context = HotArmorContext.builder()
                .resource(hotArmorEvict.resource())
                .key(key)
                .args(joinPoint.getArgs())
                .targetClass(joinPoint.getTarget().getClass())
                .methodName(joinPoint.getSignature().getName())
                .build();

        // 委托给处理器
        return aspectHandler.handleEvict(
                joinPoint,
                context,
                hotArmorEvict.beforeInvocation(),
                hotArmorEvict.delayedDelete(),
                hotArmorEvict.broadcast()
        );
    }
}
