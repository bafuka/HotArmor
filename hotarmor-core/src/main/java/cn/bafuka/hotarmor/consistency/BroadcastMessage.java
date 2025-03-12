package cn.bafuka.hotarmor.consistency;

import cn.bafuka.hotarmor.core.HotArmorContext;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 广播消息包装类
 * 用于区分不同类型的广播操作
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BroadcastMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 消息类型
     */
    private MessageType type;

    /**
     * 上下文信息
     */
    private HotArmorContext context;

    /**
     * 可选的附加数据（如热点晋升时的缓存值）
     */
    private Object value;

    /**
     * 消息类型枚举
     */
    public enum MessageType {
        /**
         * 缓存失效通知
         */
        INVALIDATE,

        /**
         * 热点晋升通知
         */
        PROMOTE
    }

    /**
     * 创建失效消息
     */
    public static BroadcastMessage invalidate(HotArmorContext context) {
        return new BroadcastMessage(MessageType.INVALIDATE, context, null);
    }

    /**
     * 创建晋升消息
     */
    public static BroadcastMessage promote(HotArmorContext context, Object value) {
        return new BroadcastMessage(MessageType.PROMOTE, context, value);
    }
}
