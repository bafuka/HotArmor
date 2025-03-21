package cn.bafuka.hotarmor.consistency.impl;

import cn.bafuka.hotarmor.consistency.BroadcastMessage;
import cn.bafuka.hotarmor.consistency.BroadcastNotifier;
import cn.bafuka.hotarmor.consistency.InvalidationListener;
import cn.bafuka.hotarmor.consistency.PromotionListener;
import cn.bafuka.hotarmor.core.HotArmorContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.RedisSerializer;

/**
 * 广播通知器实现
 * 基于 Redis Pub/Sub
 */
@Slf4j
public class RedisBroadcastNotifier implements BroadcastNotifier {

    /**
     * Redis 模板
     */
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * Redis 消息监听容器
     */
    private final RedisMessageListenerContainer listenerContainer;

    /**
     * 广播频道
     */
    private final String channel;

    /**
     * 失效监听器
     */
    private InvalidationListener invalidationListener;

    /**
     * 晋升监听器
     */
    private PromotionListener promotionListener;

    /**
     * 内部消息监听器
     */
    private InternalMessageListener internalListener;

    public RedisBroadcastNotifier(RedisTemplate<String, Object> redisTemplate,
                                  RedisMessageListenerContainer listenerContainer,
                                  String channel) {
        this.redisTemplate = redisTemplate;
        this.listenerContainer = listenerContainer;
        this.channel = channel;
    }

    @Override
    public void publish(HotArmorContext context) {
        if (context == null || context.getResource() == null) {
            return;
        }

        try {
            // 发布失效消息
            BroadcastMessage message = BroadcastMessage.invalidate(context);
            redisTemplate.convertAndSend(channel, message);

            log.info("已发送缓存失效广播: resource={}, key={}, channel={}",
                    context.getResource(), context.getKey(), channel);

        } catch (Exception e) {
            log.error("发送缓存失效广播失败: resource={}, key={}",
                    context.getResource(), context.getKey(), e);
        }
    }

    @Override
    public void publishPromotion(HotArmorContext context, Object value) {
        if (context == null || context.getResource() == null) {
            return;
        }

        try {
            // 发布晋升消息
            BroadcastMessage message = BroadcastMessage.promote(context, value);
            redisTemplate.convertAndSend(channel, message);

            log.info("已发送热点晋升广播: resource={}, key={}, channel={}",
                    context.getResource(), context.getKey(), channel);

        } catch (Exception e) {
            log.error("发送热点晋升广播失败: resource={}, key={}",
                    context.getResource(), context.getKey(), e);
        }
    }

    @Override
    public void subscribe(InvalidationListener listener) {
        if (listener == null) {
            log.warn("InvalidationListener 为空，跳过订阅");
            return;
        }

        this.invalidationListener = listener;
        registerListener();

        log.info("已订阅缓存失效广播频道: {}", channel);
    }

    @Override
    public void subscribePromotion(PromotionListener listener) {
        if (listener == null) {
            log.warn("PromotionListener 为空，跳过订阅");
            return;
        }

        this.promotionListener = listener;
        registerListener();

        log.info("已订阅热点晋升广播频道: {}", channel);
    }

    @Override
    public void unsubscribe() {
        if (internalListener != null) {
            listenerContainer.removeMessageListener(internalListener);
            internalListener = null;
            log.info("已取消订阅广播频道: {}", channel);
        }
    }

    /**
     * 注册或重新注册内部监听器
     * 每次设置新的 listener 时都要重新注册，确保监听器持有最新的引用
     */
    private void registerListener() {
        // 如果已存在，先移除旧的
        if (internalListener != null) {
            listenerContainer.removeMessageListener(internalListener);
        }

        // 创建新的监听器（持有最新的 invalidationListener 和 promotionListener）
        this.internalListener = new InternalMessageListener(
                invalidationListener,
                promotionListener,
                redisTemplate
        );

        // 订阅频道
        listenerContainer.addMessageListener(internalListener, new ChannelTopic(channel));

        log.debug("已注册消息监听器: invalidationListener={}, promotionListener={}",
                invalidationListener != null, promotionListener != null);
    }

    /**
     * 内部消息监听器
     */
    private static class InternalMessageListener implements MessageListener {

        private final InvalidationListener invalidationListener;
        private final PromotionListener promotionListener;
        private final RedisSerializer<?> valueSerializer;

        public InternalMessageListener(InvalidationListener invalidationListener,
                                      PromotionListener promotionListener,
                                      RedisTemplate<String, Object> redisTemplate) {
            this.invalidationListener = invalidationListener;
            this.promotionListener = promotionListener;
            this.valueSerializer = redisTemplate.getValueSerializer();
        }

        @Override
        public void onMessage(Message message, byte[] pattern) {
            try {
                // 使用 RedisTemplate 的序列化器反序列化消息
                Object obj = valueSerializer.deserialize(message.getBody());

                if (obj instanceof BroadcastMessage) {
                    BroadcastMessage broadcastMsg = (BroadcastMessage) obj;
                    HotArmorContext context = broadcastMsg.getContext();

                    switch (broadcastMsg.getType()) {
                        case INVALIDATE:
                            handleInvalidate(context);
                            break;
                        case PROMOTE:
                            handlePromote(context, broadcastMsg.getValue());
                            break;
                        default:
                            log.warn("未知的广播消息类型: {}", broadcastMsg.getType());
                    }
                } else if (obj instanceof HotArmorContext) {
                    // 向后兼容旧的消息格式
                    HotArmorContext context = (HotArmorContext) obj;
                    handleInvalidate(context);
                } else {
                    log.warn("接收到非法的广播消息类型: {}", obj);
                }

            } catch (Exception e) {
                log.error("处理广播消息失败", e);
            }
        }

        /**
         * 处理缓存失效消息
         */
        private void handleInvalidate(HotArmorContext context) {
            if (invalidationListener != null) {
                log.debug("接收到缓存失效广播: resource={}, key={}",
                        context.getResource(), context.getKey());
                invalidationListener.onInvalidate(context);
            }
        }

        /**
         * 处理热点晋升消息
         */
        private void handlePromote(HotArmorContext context, Object value) {
            if (promotionListener != null) {
                log.debug("接收到热点晋升广播: resource={}, key={}",
                        context.getResource(), context.getKey());
                promotionListener.onPromote(context, value);
            }
        }
    }
}
