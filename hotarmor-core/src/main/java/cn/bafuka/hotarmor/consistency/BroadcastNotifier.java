package cn.bafuka.hotarmor.consistency;

import cn.bafuka.hotarmor.core.HotArmorContext;

/**
 * 广播通知器接口
 * 基于 Redis Pub/Sub，通知所有集群节点清理本地缓存或晋升热点
 */
public interface BroadcastNotifier {

    /**
     * 发布缓存失效消息
     *
     * @param context 上下文信息
     */
    void publish(HotArmorContext context);

    /**
     * 发布热点晋升消息
     * 通知其他节点将指定key晋升到L1缓存
     *
     * @param context 上下文信息
     * @param value   缓存值（可选，如果为null则其他节点需要自行加载）
     */
    void publishPromotion(HotArmorContext context, Object value);

    /**
     * 订阅缓存失效消息
     *
     * @param listener 消息监听器
     */
    void subscribe(InvalidationListener listener);

    /**
     * 订阅热点晋升消息
     *
     * @param listener 热点晋升监听器
     */
    void subscribePromotion(PromotionListener listener);

    /**
     * 取消订阅
     */
    void unsubscribe();
}
