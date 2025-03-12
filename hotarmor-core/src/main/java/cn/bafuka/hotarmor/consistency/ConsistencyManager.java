package cn.bafuka.hotarmor.consistency;

import cn.bafuka.hotarmor.core.HotArmorContext;

/**
 * 一致性管理器接口
 * 协调延迟双删和广播通知，保证数据一致性
 */
public interface ConsistencyManager {

    /**
     * 初始化一致性管理器
     */
    void initialize();

    /**
     * 处理数据更新（完整的一致性流程）
     * 包括：立即删除 Redis -> 发送延迟消息 -> 发送广播通知
     *
     * @param context 上下文信息
     */
    void handleUpdate(HotArmorContext context);

    /**
     * 处理热点晋升（发送晋升广播）
     * 当本地节点检测到热点并晋升到L1后，通知其他节点也晋升
     *
     * @param context 上下文信息
     * @param value   缓存值（可选）
     */
    void handlePromotion(HotArmorContext context, Object value);

    /**
     * 只删除 Redis（用于手动触发）
     *
     * @param context 上下文信息
     */
    void invalidateCache(HotArmorContext context);

    /**
     * 关闭一致性管理器
     */
    void shutdown();
}
