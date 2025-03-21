package cn.bafuka.hotarmor.consistency.impl;

import cn.bafuka.hotarmor.consistency.DelayedDeleteProducer;
import cn.bafuka.hotarmor.core.HotArmorContext;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.MessageBuilder;

/**
 * 延迟双删生产者实现
 * 基于 RocketMQ 的延迟消息
 */
@Slf4j
public class RocketMQDelayedDeleteProducer implements DelayedDeleteProducer {

    /**
     * RocketMQ 模板
     */
    private final RocketMQTemplate rocketMQTemplate;

    /**
     * 延迟删除的 Topic
     */
    private final String topic;

    public RocketMQDelayedDeleteProducer(RocketMQTemplate rocketMQTemplate, String topic) {
        this.rocketMQTemplate = rocketMQTemplate;
        this.topic = topic;
    }

    @Override
    public void sendDelayedDelete(HotArmorContext context, long delayTimeMs) {
        if (context == null || context.getResource() == null) {
            return;
        }

        try {
            // 将 context 转换为消息体
            String message = JSON.toJSONString(context);

            // 计算 RocketMQ 延迟级别
            // RocketMQ 延迟级别: 1s 5s 10s 30s 1m 2m 3m 4m 5m 6m 7m 8m 9m 10m 20m 30m 1h 2h
            int delayLevel = calculateDelayLevel(delayTimeMs);

            // 发送延迟消息
            rocketMQTemplate.syncSend(topic, MessageBuilder.withPayload(message).build(), 3000, delayLevel);

            log.info("已发送延迟删除消息: resource={}, key={}, delayMs={}, delayLevel={}",
                    context.getResource(), context.getKey(), delayTimeMs, delayLevel);

        } catch (Exception e) {
            log.error("发送延迟删除消息失败: resource={}, key={}",
                    context.getResource(), context.getKey(), e);
        }
    }

    @Override
    public void deleteImmediately(HotArmorContext context) {
        if (context == null || context.getResource() == null) {
            return;
        }

        log.info("立即删除（第一次删除）: resource={}, key={}",
                context.getResource(), context.getKey());

        // 立即删除操作会在 ConsistencyManager 中通过 L4SafeLoader 完成
        // 这里只做日志记录
    }

    /**
     * 计算 RocketMQ 延迟级别
     * 延迟级别对应关系：
     * 1=1s, 2=5s, 3=10s, 4=30s, 5=1m, 6=2m, 7=3m, 8=4m, 9=5m, 10=6m, 11=7m, 12=8m, 13=9m, 14=10m, 15=20m, 16=30m, 17=1h, 18=2h
     *
     * @param delayTimeMs 延迟时间（毫秒）
     * @return 延迟级别
     */
    private int calculateDelayLevel(long delayTimeMs) {
        long seconds = delayTimeMs / 1000;

        if (seconds <= 1) return 1;      // 1s
        if (seconds <= 5) return 2;      // 5s
        if (seconds <= 10) return 3;     // 10s
        if (seconds <= 30) return 4;     // 30s
        if (seconds <= 60) return 5;     // 1m
        if (seconds <= 120) return 6;    // 2m
        if (seconds <= 180) return 7;    // 3m
        if (seconds <= 240) return 8;    // 4m
        if (seconds <= 300) return 9;    // 5m
        if (seconds <= 360) return 10;   // 6m
        if (seconds <= 420) return 11;   // 7m
        if (seconds <= 480) return 12;   // 8m
        if (seconds <= 540) return 13;   // 9m
        if (seconds <= 600) return 14;   // 10m
        if (seconds <= 1200) return 15;  // 20m
        if (seconds <= 1800) return 16;  // 30m
        if (seconds <= 3600) return 17;  // 1h
        return 18;                        // 2h
    }
}
