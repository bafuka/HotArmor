package cn.bafuka.hotarmor.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.concurrent.TimeUnit;

/**
 * 热点规则配置
 * 对应控制平面的核心配置模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotArmorRule {

    /**
     * 资源名称（唯一标识）
     */
    private String resource;

    /**
     * L1 本地缓存配置
     */
    private L1CacheConfig l1Config;

    /**
     * L2 噪音过滤器配置
     */
    private L2FilterConfig l2Config;

    /**
     * L3 热点探测器配置（Sentinel）
     */
    private L3HotspotConfig l3Config;

    /**
     * L4 安全回源器配置
     */
    private L4LoaderConfig l4Config;

    /**
     * 一致性配置
     */
    private ConsistencyConfig consistencyConfig;

    /**
     * L1 本地缓存配置
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class L1CacheConfig {
        /**
         * 最大容量
         */
        @Builder.Default
        private int maximumSize = 10000;

        /**
         * 过期时间
         */
        @Builder.Default
        private long expireAfterWrite = 60;

        /**
         * 时间单位
         */
        @Builder.Default
        private TimeUnit timeUnit = TimeUnit.SECONDS;

        /**
         * 是否启用
         */
        @Builder.Default
        private boolean enabled = true;
    }

    /**
     * L2 噪音过滤器配置
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class L2FilterConfig {
        /**
         * 时间窗口（秒）
         */
        @Builder.Default
        private int windowSeconds = 10;

        /**
         * 频率阈值
         */
        @Builder.Default
        private int threshold = 5;

        /**
         * 是否启用
         */
        @Builder.Default
        private boolean enabled = true;
    }

    /**
     * L3 热点探测器配置（基于 Sentinel）
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class L3HotspotConfig {
        /**
         * QPS 阈值
         */
        @Builder.Default
        private double qpsThreshold = 100.0;

        /**
         * 统计时长（秒）
         */
        @Builder.Default
        private int durationInSec = 1;

        /**
         * 是否启用
         */
        @Builder.Default
        private boolean enabled = true;
    }

    /**
     * L4 安全回源器配置
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class L4LoaderConfig {
        /**
         * Redis 键前缀
         */
        @Builder.Default
        private String redisKeyPrefix = "hotarmor:";

        /**
         * Redis 过期时间（秒）
         */
        @Builder.Default
        private int redisTtlSeconds = 300;

        /**
         * 分布式锁等待时间（毫秒）
         */
        @Builder.Default
        private long lockWaitTimeMs = 3000;

        /**
         * 分布式锁租约时间（毫秒）
         */
        @Builder.Default
        private long lockLeaseTimeMs = 5000;
    }

    /**
     * 一致性配置
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConsistencyConfig {
        /**
         * 是否启用延迟双删
         */
        @Builder.Default
        private boolean enableDelayedDoubleDelete = true;

        /**
         * 延迟时间（毫秒）
         */
        @Builder.Default
        private long delayTimeMs = 5000;

        /**
         * 是否启用广播通知
         */
        @Builder.Default
        private boolean enableBroadcast = true;

        /**
         * 广播频道名称
         */
        @Builder.Default
        private String broadcastChannel = "hotarmor:invalidate";

        /**
         * 是否启用热点晋升广播
         * 当一个节点检测到热点时，通知其他节点也晋升到L1
         */
        @Builder.Default
        private boolean enablePromotionBroadcast = true;
    }
}
