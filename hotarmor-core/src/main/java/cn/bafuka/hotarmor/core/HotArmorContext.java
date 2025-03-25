package cn.bafuka.hotarmor.core;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * HotArmor 上下文信息
 * 用于在执行链路中传递请求上下文
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotArmorContext {

    /**
     * 资源名称（用于隔离不同的缓存域）
     */
    private String resource;

    /**
     * 缓存键
     */
    private Object key;

    /**
     * 方法参数
     */
    private Object[] args;

    /**
     * 目标方法类
     */
    private Class<?> targetClass;

    /**
     * 目标方法名
     */
    private String methodName;
}
