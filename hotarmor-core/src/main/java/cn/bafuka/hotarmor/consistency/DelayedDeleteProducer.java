package cn.bafuka.hotarmor.consistency;

import cn.bafuka.hotarmor.core.HotArmorContext;

/**
 * 延迟双删生产者接口
 * 负责在数据更新时，发送延迟消息以保证最终一致性
 */
public interface DelayedDeleteProducer {

    /**
     * 发送延迟删除消息
     *
     * @param context   上下文信息
     * @param delayTimeMs 延迟时间（毫秒）
     */
    void sendDelayedDelete(HotArmorContext context, long delayTimeMs);

    /**
     * 立即删除（第一次删除）
     *
     * @param context 上下文信息
     */
    void deleteImmediately(HotArmorContext context);
}
