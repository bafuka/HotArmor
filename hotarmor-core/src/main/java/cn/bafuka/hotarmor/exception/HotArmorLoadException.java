package cn.bafuka.hotarmor.exception;

import cn.bafuka.hotarmor.core.HotArmorContext;

/**
 * HotArmor 加载异常
 * 当从数据库或外部数据源加载数据失败时抛出
 *
 * @author HotArmor Team
 * @since 1.0
 */
public class HotArmorLoadException extends RuntimeException {

    /**
     * 上下文信息
     */
    private final HotArmorContext context;

    /**
     * 失败原因
     */
    private final LoadFailureReason reason;

    public HotArmorLoadException(String message, Throwable cause,
                                 HotArmorContext context,
                                 LoadFailureReason reason) {
        super(message, cause);
        this.context = context;
        this.reason = reason;
    }

    public HotArmorContext getContext() {
        return context;
    }

    public LoadFailureReason getReason() {
        return reason;
    }

    /**
     * 加载失败原因枚举
     */
    public enum LoadFailureReason {
        /**
         * 数据库错误
         */
        DATABASE_ERROR("数据库错误"),

        /**
         * 超时错误
         */
        TIMEOUT("超时"),

        /**
         * 获取锁失败
         */
        LOCK_FAILURE("获取锁失败"),

        /**
         * Redis 错误
         */
        REDIS_ERROR("Redis 错误"),

        /**
         * 未知错误
         */
        UNKNOWN("未知错误");

        private final String description;

        LoadFailureReason(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    @Override
    public String toString() {
        return "HotArmorLoadException{" +
                "resource=" + (context != null ? context.getResource() : "null") +
                ", key=" + (context != null ? context.getKey() : "null") +
                ", reason=" + reason +
                ", message=" + getMessage() +
                '}';
    }
}
