package cn.bafuka.hotarmor.consistency;

import cn.bafuka.hotarmor.core.HotArmorContext;

/**
 * 延迟删除消费者接口
 * 负责消费延迟消息，执行第二次删除
 */
public interface DelayedDeleteConsumer {

    /**
     * 启动消费者
     */
    void start();

    /**
     * 处理延迟删除消息
     *
     * @param context 上下文信息
     */
    void handleDelete(HotArmorContext context);

    /**
     * 停止消费者
     */
    void shutdown();
}
