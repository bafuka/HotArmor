package cn.bafuka.hotarmor.consistency.impl;

import cn.bafuka.hotarmor.consistency.DelayedDeleteConsumer;
import cn.bafuka.hotarmor.core.HotArmorContext;
import cn.bafuka.hotarmor.dataplane.L4SafeLoader;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 延迟删除消费者实现
 * 监听 RocketMQ 延迟消息，执行第二次删除
 */
@Slf4j
public class RocketMQDelayedDeleteConsumer implements DelayedDeleteConsumer {

    /**
     * L4 安全回源器（用于删除 Redis）
     */
    private final L4SafeLoader<?> l4SafeLoader;

    /**
     * 是否已启动
     */
    private volatile boolean started = false;

    public RocketMQDelayedDeleteConsumer(L4SafeLoader<?> l4SafeLoader) {
        this.l4SafeLoader = l4SafeLoader;
    }

    @Override
    public void start() {
        if (started) {
            log.warn("延迟删除消费者已经启动");
            return;
        }

        log.info("启动延迟删除消费者...");
        started = true;
        // 实际的消费者启动由 Spring Boot 自动处理
    }

    @Override
    public void handleDelete(HotArmorContext context) {
        if (context == null || context.getResource() == null) {
            log.warn("延迟删除的上下文无效");
            return;
        }

        try {
            log.info("处理延迟删除（第二次删除）: resource={}, key={}",
                    context.getResource(), context.getKey());

            // 执行第二次删除 Redis
            l4SafeLoader.deleteFromRedis(context);

            log.info("成功处理延迟删除: resource={}, key={}",
                    context.getResource(), context.getKey());

        } catch (Exception e) {
            log.error("处理延迟删除失败: resource={}, key={}",
                    context.getResource(), context.getKey(), e);
            throw e; // 抛出异常，让 RocketMQ 重试
        }
    }

    @Override
    public void shutdown() {
        if (!started) {
            return;
        }

        log.info("关闭延迟删除消费者...");
        started = false;
    }

    /**
     * RocketMQ 消息监听器（内部类）
     * 需要在 Spring Boot 自动配置中注册
     */
    public static class MessageListener implements RocketMQListener<String> {

        private final RocketMQDelayedDeleteConsumer consumer;

        public MessageListener(RocketMQDelayedDeleteConsumer consumer) {
            this.consumer = consumer;
        }

        @Override
        public void onMessage(String message) {
            try {
                log.debug("接收到延迟删除消息: {}", message);

                // 解析消息
                HotArmorContext context = JSON.parseObject(message, HotArmorContext.class);

                // 处理删除
                consumer.handleDelete(context);

            } catch (Exception e) {
                log.error("处理延迟删除消息失败: {}", message, e);
                throw e;
            }
        }
    }
}
