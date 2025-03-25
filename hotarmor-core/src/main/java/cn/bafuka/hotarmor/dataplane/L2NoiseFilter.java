package cn.bafuka.hotarmor.dataplane;

import cn.bafuka.hotarmor.core.HotArmorContext;

/**
 * L2 噪音过滤器接口
 * 基于轻量级计数器，过滤冷门长尾流量，保护 Sentinel
 */
public interface L2NoiseFilter {

    /**
     * 记录一次访问，并判断是否应该继续传递给下游
     *
     * @param context 上下文信息
     * @return true 表示频率达到阈值，应该传递给 L3 Sentinel；
     *         false 表示是冷数据，直接放行到安全回源
     */
    boolean shouldPass(HotArmorContext context);

    /**
     * 重置计数器（用于规则更新）
     *
     * @param resource 资源名称
     */
    void reset(String resource);

    /**
     * 获取当前计数
     *
     * @param context 上下文信息
     * @return 当前时间窗口内的访问次数
     */
    long getCount(HotArmorContext context);
}
