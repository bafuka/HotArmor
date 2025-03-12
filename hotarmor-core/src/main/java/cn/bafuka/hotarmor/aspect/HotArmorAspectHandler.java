package cn.bafuka.hotarmor.aspect;

import cn.bafuka.hotarmor.core.HotArmorContext;
import org.aspectj.lang.ProceedingJoinPoint;

/**
 * HotArmor 切面处理器接口
 * 负责拦截注解方法，执行缓存逻辑
 */
public interface HotArmorAspectHandler {

    /**
     * 处理 @HotArmorCache 注解的方法调用
     *
     * @param joinPoint 切点
     * @param context   上下文信息
     * @return 方法返回值
     * @throws Throwable 异常
     */
    Object handleCache(ProceedingJoinPoint joinPoint, HotArmorContext context) throws Throwable;

    /**
     * 处理 @HotArmorEvict 注解的方法调用
     *
     * @param joinPoint 切点
     * @param context   上下文信息
     * @param beforeInvocation 是否在方法执行前删除
     * @param delayedDelete    是否延迟删除
     * @param broadcast        是否广播通知
     * @return 方法返回值
     * @throws Throwable 异常
     */
    Object handleEvict(ProceedingJoinPoint joinPoint, HotArmorContext context,
                      boolean beforeInvocation, boolean delayedDelete, boolean broadcast) throws Throwable;
}
