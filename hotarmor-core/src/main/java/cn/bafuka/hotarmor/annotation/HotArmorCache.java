package cn.bafuka.hotarmor.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * HotArmor 缓存注解
 * 标注在方法上，声明式地启用热点防护
 *
 * 使用示例：
 * <pre>
 * {@code
 * @HotArmorCache(resource = "user:detail", key = "#userId")
 * public User getUserById(Long userId) {
 *     return userMapper.selectById(userId);
 * }
 * }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface HotArmorCache {

    /**
     * 资源名称（必填）
     * 用于标识不同的缓存域，每个资源对应一套独立的规则
     *
     * @return 资源名称
     */
    String resource();

    /**
     * 缓存键表达式（支持 SpEL）
     * 例如：#userId, #user.id, #p0
     *
     * @return SpEL 表达式
     */
    String key();

    /**
     * 条件表达式（可选）
     * 只有满足条件时才启用缓存
     * 例如：#userId > 0
     *
     * @return SpEL 表达式，默认为空表示总是启用
     */
    String condition() default "";

    /**
     * 是否启用（可选）
     * 可用于动态开关
     *
     * @return 默认 true
     */
    boolean enabled() default true;
}
