package cn.bafuka.hotarmor.spel;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;

/**
 * SpEL 表达式解析器默认实现
 * 基于 Spring Expression Language
 */
@Slf4j
public class DefaultSpelExpressionParser implements SpelExpressionParser {

    /**
     * SpEL 表达式解析器
     */
    private final ExpressionParser parser = new org.springframework.expression.spel.standard.SpelExpressionParser();

    /**
     * 参数名发现器
     */
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    @Override
    public Object parseKey(String expression, ProceedingJoinPoint joinPoint,
                          Class<?> targetClass, String methodName) {
        if (!StringUtils.hasText(expression)) {
            return null;
        }

        try {
            // 获取方法和参数
            Method method = getMethod(joinPoint, targetClass, methodName);
            Object[] args = joinPoint.getArgs();

            // 创建求值上下文
            EvaluationContext context = createEvaluationContext(method, args);

            // 解析表达式
            Expression exp = parser.parseExpression(expression);
            return exp.getValue(context);
        } catch (Exception e) {
            log.error("失败: parse SpEL key expression: {}", expression, e);
            return null;
        }
    }

    @Override
    public boolean parseCondition(String expression, ProceedingJoinPoint joinPoint,
                                 Class<?> targetClass, String methodName) {
        // 空表达式视为条件成立
        if (!StringUtils.hasText(expression)) {
            return true;
        }

        try {
            // 获取方法和参数
            Method method = getMethod(joinPoint, targetClass, methodName);
            Object[] args = joinPoint.getArgs();

            // 创建求值上下文
            EvaluationContext context = createEvaluationContext(method, args);

            // 解析表达式
            Expression exp = parser.parseExpression(expression);
            Boolean result = exp.getValue(context, Boolean.class);
            return result != null && result;
        } catch (Exception e) {
            log.error("失败: parse SpEL condition expression: {}", expression, e);
            return false;
        }
    }

    /**
     * 获取目标方法
     *
     * @param joinPoint   切点
     * @param targetClass 目标类
     * @param methodName  方法名
     * @return 方法对象
     */
    private Method getMethod(ProceedingJoinPoint joinPoint, Class<?> targetClass, String methodName) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        return signature.getMethod();
    }

    /**
     * 创建 SpEL 求值上下文
     *
     * 安全说明：使用 SimpleEvaluationContext 替代 StandardEvaluationContext
     * SimpleEvaluationContext 只允许访问属性和调用简单方法，不能执行任意代码，
     * 有效防止 SpEL 表达式注入攻击
     *
     * @param method 方法
     * @param args   参数
     * @return 求值上下文
     */
    private EvaluationContext createEvaluationContext(Method method, Object[] args) {
        // 使用 SimpleEvaluationContext 替代 StandardEvaluationContext
        // 这样可以防止 SpEL 注入攻击，只允许访问属性和基本操作
        SimpleEvaluationContext context = SimpleEvaluationContext
                .forReadOnlyDataBinding()  // 只读数据绑定模式，只能访问属性
                .build();

        // 设置参数名称
        String[] parameterNames = parameterNameDiscoverer.getParameterNames(method);
        if (parameterNames != null) {
            for (int i = 0; i < parameterNames.length; i++) {
                context.setVariable(parameterNames[i], args[i]);
            }
        }

        // 设置 p0, p1, p2... 参数别名
        for (int i = 0; i < args.length; i++) {
            context.setVariable("p" + i, args[i]);
            context.setVariable("a" + i, args[i]);
        }

        return context;
    }
}
