package cn.bafuka.hotarmor.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * HotArmor 缓存失效注解
 * 标注在更新方法上，触发缓存失效和一致性保证
 *
 * 使用示例：
 * <pre>
 * {@code
 * @HotArmorEvict(resource = "user:detail", key = "#user.id")
 * public void updateUser(User user) {
 *     userMapper.updateById(user);
 * }
 * }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface HotArmorEvict {

    /**
     * 资源名称（必填）
     *
     * @return 资源名称
     */
    String resource();

    /**
     * 缓存键表达式（支持 SpEL）
     *
     * @return SpEL 表达式
     */
    String key();

    /**
     * 是否在方法执行前删除缓存
     * true: 方法执行前删除（默认）
     * false: 方法执行后删除
     *
     * @return 默认 true
     */
    boolean beforeInvocation() default true;

    /**
     * 是否触发延迟双删
     *
     * @return 默认 true
     */
    boolean delayedDelete() default true;

    /**
     * 是否触发广播通知
     *
     * @return 默认 true
     */
    boolean broadcast() default true;
}
