package cn.bafuka.hotarmor.spel;

import org.aspectj.lang.ProceedingJoinPoint;

/**
 * SpEL 表达式解析器接口
 * 用于解析注解中的 SpEL 表达式
 */
public interface SpelExpressionParser {

    /**
     * 解析表达式，获取缓存键
     *
     * @param expression SpEL 表达式
     * @param joinPoint  切点
     * @param targetClass 目标类
     * @param methodName  方法名
     * @return 解析后的值
     */
    Object parseKey(String expression, ProceedingJoinPoint joinPoint,
                   Class<?> targetClass, String methodName);

    /**
     * 解析条件表达式
     *
     * @param expression SpEL 表达式
     * @param joinPoint  切点
     * @param targetClass 目标类
     * @param methodName  方法名
     * @return true 表示条件满足，false 表示不满足
     */
    boolean parseCondition(String expression, ProceedingJoinPoint joinPoint,
                          Class<?> targetClass, String methodName);
}
