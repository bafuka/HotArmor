package cn.bafuka.hotarmor.consistency;

import cn.bafuka.hotarmor.core.HotArmorContext;

/**
 * 热点晋升监听器接口
 * 用于处理其他节点发送的热点晋升广播
 */
public interface PromotionListener {

    /**
     * 处理热点晋升通知
     *
     * @param context 上下文信息
     * @param value   缓存值（可选，可能为null）
     */
    void onPromote(HotArmorContext context, Object value);
}
