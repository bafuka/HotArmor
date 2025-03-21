package cn.bafuka.hotarmor.control.impl;

import cn.bafuka.hotarmor.consistency.impl.DefaultConsistencyManager;
import cn.bafuka.hotarmor.control.RuleChangeListener;
import cn.bafuka.hotarmor.control.RuleManager;
import cn.bafuka.hotarmor.dataplane.impl.CaffeineL1CacheEngine;
import cn.bafuka.hotarmor.dataplane.impl.CaffeineL2NoiseFilter;
import cn.bafuka.hotarmor.dataplane.impl.RedissonL4SafeLoader;
import cn.bafuka.hotarmor.dataplane.impl.SentinelL3HotspotDetector;
import cn.bafuka.hotarmor.model.HotArmorRule;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 规则管理器默认实现
 * 负责规则的加载、更新、比对和生效
 */
@Slf4j
public class DefaultRuleManager implements RuleManager {

    /**
     * 规则缓存
     * Key: resource 名称
     * Value: 规则对象
     */
    private final Map<String, HotArmorRule> ruleMap = new ConcurrentHashMap<>();

    /**
     * L1 缓存引擎
     */
    private final CaffeineL1CacheEngine<?> l1CacheEngine;

    /**
     * L2 噪音过滤器
     */
    private final CaffeineL2NoiseFilter l2NoiseFilter;

    /**
     * L3 热点探测器
     */
    private final SentinelL3HotspotDetector l3HotspotDetector;

    /**
     * L4 安全回源器
     */
    private final RedissonL4SafeLoader<?> l4SafeLoader;

    /**
     * 一致性管理器
     */
    private final DefaultConsistencyManager consistencyManager;

    /**
     * 规则变更监听器列表
     */
    private final List<RuleChangeListener> listeners = new ArrayList<>();

    /**
     * 是否已初始化
     */
    private volatile boolean initialized = false;

    public DefaultRuleManager(CaffeineL1CacheEngine<?> l1CacheEngine,
                             CaffeineL2NoiseFilter l2NoiseFilter,
                             SentinelL3HotspotDetector l3HotspotDetector,
                             RedissonL4SafeLoader<?> l4SafeLoader,
                             DefaultConsistencyManager consistencyManager) {
        this.l1CacheEngine = l1CacheEngine;
        this.l2NoiseFilter = l2NoiseFilter;
        this.l3HotspotDetector = l3HotspotDetector;
        this.l4SafeLoader = l4SafeLoader;
        this.consistencyManager = consistencyManager;
    }

    @Override
    public void initialize() {
        if (initialized) {
            log.debug("RuleManager already initialized, skipping");
            return;
        }

        log.info("初始化 RuleManager...");
        initialized = true;
        log.info("RuleManager initialized successfully");
    }

    @Override
    public synchronized void loadRules(List<HotArmorRule> rules) {
        if (rules == null || rules.isEmpty()) {
            log.warn("No rules to load");
            return;
        }

        log.info("开始加载 {} 条规则...", rules.size());

        int successCount = 0;
        int failureCount = 0;

        for (HotArmorRule rule : rules) {
            try {
                // 验证规则
                validateRule(rule);

                // 应用规则
                applyRule(rule);

                // 缓存规则
                ruleMap.put(rule.getResource(), rule);

                // 触发监听器
                notifyRuleAdded(rule);

                successCount++;
                log.info("规则加载成功: resource={}", rule.getResource());

            } catch (IllegalArgumentException e) {
                failureCount++;
                log.error("规则验证失败，跳过: resource={}, error={}",
                        rule != null ? rule.getResource() : "null",
                        e.getMessage());
            } catch (Exception e) {
                failureCount++;
                log.error("规则加载失败，跳过: resource={}",
                        rule != null ? rule.getResource() : "null", e);
            }
        }

        log.info("规则加载完成: 成功={}, 失败={}", successCount, failureCount);
    }

    @Override
    public synchronized void updateRules(List<HotArmorRule> rules) {
        if (rules == null || rules.isEmpty()) {
            log.warn("No rules to update");
            return;
        }

        log.info("开始更新 {} 条规则...", rules.size());

        int successCount = 0;
        int failureCount = 0;

        for (HotArmorRule newRule : rules) {
            try {
                // 验证规则
                validateRule(newRule);

                String resource = newRule.getResource();
                HotArmorRule oldRule = ruleMap.get(resource);

                if (oldRule == null) {
                    // 新增规则
                    applyRule(newRule);
                    ruleMap.put(resource, newRule);
                    notifyRuleAdded(newRule);
                } else {
                    // 更新规则 - 需要比对差异
                    updateRule(oldRule, newRule);
                    ruleMap.put(resource, newRule);
                    notifyRuleUpdated(oldRule, newRule);
                }

                successCount++;
                log.info("规则更新成功: resource={}", newRule.getResource());

            } catch (IllegalArgumentException e) {
                failureCount++;
                log.error("规则验证失败，跳过: resource={}, error={}",
                        newRule != null ? newRule.getResource() : "null",
                        e.getMessage());
            } catch (Exception e) {
                failureCount++;
                log.error("规则更新失败，跳过: resource={}",
                        newRule != null ? newRule.getResource() : "null", e);
            }
        }

        log.info("规则更新完成: 成功={}, 失败={}", successCount, failureCount);
    }

    @Override
    public HotArmorRule getRule(String resource) {
        return ruleMap.get(resource);
    }

    @Override
    public List<HotArmorRule> getAllRules() {
        return new ArrayList<>(ruleMap.values());
    }

    @Override
    public synchronized void removeRule(String resource) {
        HotArmorRule rule = ruleMap.remove(resource);
        if (rule == null) {
            log.warn("Rule not found: {}", resource);
            return;
        }

        log.info("移除 rule: {}", resource);

        // 移除各层配置
        l1CacheEngine.invalidateResource(resource);
        l2NoiseFilter.reset(resource);
        l3HotspotDetector.removeRule(resource);

        // 触发监听器
        notifyRuleRemoved(rule);

        log.info("Rule removed: {}", resource);
    }

    @Override
    public synchronized void clearAll() {
        log.info("Clearing all rules...");

        for (String resource : ruleMap.keySet()) {
            removeRule(resource);
        }

        ruleMap.clear();
        log.info("All rules cleared");
    }

    @Override
    public void shutdown() {
        if (!initialized) {
            return;
        }

        log.info("关闭 RuleManager...");
        clearAll();
        initialized = false;
        log.info("RuleManager shutdown successfully");
    }

    /**
     * 添加规则变更监听器
     *
     * @param listener 监听器
     */
    public void addListener(RuleChangeListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    /**
     * 验证规则配置
     *
     * @param rule 规则
     * @throws IllegalArgumentException 如果规则无效
     */
    private void validateRule(HotArmorRule rule) {
        if (rule == null) {
            throw new IllegalArgumentException("Rule cannot be null");
        }

        // 验证 resource
        if (rule.getResource() == null || rule.getResource().trim().isEmpty()) {
            throw new IllegalArgumentException("Resource cannot be null or empty");
        }

        if (rule.getResource().length() > 100) {
            throw new IllegalArgumentException("Resource name too long (max 100 chars): " + rule.getResource().length());
        }

        // 验证 L1 配置
        if (rule.getL1Config() != null && rule.getL1Config().isEnabled()) {
            validateL1Config(rule.getResource(), rule.getL1Config());
        }

        // 验证 L2 配置
        if (rule.getL2Config() != null && rule.getL2Config().isEnabled()) {
            validateL2Config(rule.getResource(), rule.getL2Config());
        }

        // 验证 L3 配置
        if (rule.getL3Config() != null && rule.getL3Config().isEnabled()) {
            validateL3Config(rule.getResource(), rule.getL3Config());
        }

        // 验证 L4 配置
        if (rule.getL4Config() != null) {
            validateL4Config(rule.getResource(), rule.getL4Config());
        }

        // 验证一致性配置
        if (rule.getConsistencyConfig() != null) {
            validateConsistencyConfig(rule.getResource(), rule.getConsistencyConfig());
        }

        log.debug("规则验证通过: resource={}", rule.getResource());
    }

    /**
     * 验证 L1 缓存配置
     */
    private void validateL1Config(String resource, HotArmorRule.L1CacheConfig l1) {
        if (l1.getMaximumSize() <= 0) {
            throw new IllegalArgumentException(
                    String.format("L1 maximumSize must be positive for resource %s, got: %d",
                            resource, l1.getMaximumSize()));
        }

        if (l1.getMaximumSize() > 1_000_000) {
            log.warn("L1 maximumSize is very large ({}) for resource {}, may cause OOM",
                    l1.getMaximumSize(), resource);
        }

        if (l1.getExpireAfterWrite() <= 0) {
            throw new IllegalArgumentException(
                    String.format("L1 expireAfterWrite must be positive for resource %s, got: %d",
                            resource, l1.getExpireAfterWrite()));
        }

        if (l1.getTimeUnit() == null) {
            throw new IllegalArgumentException(
                    String.format("L1 timeUnit cannot be null for resource %s", resource));
        }
    }

    /**
     * 验证 L2 过滤配置
     */
    private void validateL2Config(String resource, HotArmorRule.L2FilterConfig l2) {
        if (l2.getWindowSeconds() <= 0) {
            throw new IllegalArgumentException(
                    String.format("L2 windowSeconds must be positive for resource %s, got: %d",
                            resource, l2.getWindowSeconds()));
        }

        if (l2.getThreshold() <= 0) {
            throw new IllegalArgumentException(
                    String.format("L2 threshold must be positive for resource %s, got: %d",
                            resource, l2.getThreshold()));
        }

        if (l2.getWindowSeconds() > 3600) {
            log.warn("L2 windowSeconds is very large ({}s) for resource {}, may cause memory issues",
                    l2.getWindowSeconds(), resource);
        }
    }

    /**
     * 验证 L3 热点配置
     */
    private void validateL3Config(String resource, HotArmorRule.L3HotspotConfig l3) {
        if (l3.getQpsThreshold() <= 0) {
            throw new IllegalArgumentException(
                    String.format("L3 qpsThreshold must be positive for resource %s, got: %.2f",
                            resource, l3.getQpsThreshold()));
        }

        if (l3.getDurationInSec() <= 0) {
            throw new IllegalArgumentException(
                    String.format("L3 durationInSec must be positive for resource %s, got: %d",
                            resource, l3.getDurationInSec()));
        }

        if (l3.getQpsThreshold() < 1.0) {
            log.warn("L3 qpsThreshold is very low ({}) for resource {}, may cause frequent hotspot detection",
                    l3.getQpsThreshold(), resource);
        }
    }

    /**
     * 验证 L4 加载配置
     */
    private void validateL4Config(String resource, HotArmorRule.L4LoaderConfig l4) {
        if (l4.getRedisKeyPrefix() == null || l4.getRedisKeyPrefix().isEmpty()) {
            throw new IllegalArgumentException(
                    String.format("L4 redisKeyPrefix cannot be null or empty for resource %s", resource));
        }

        if (l4.getRedisTtlSeconds() <= 0) {
            throw new IllegalArgumentException(
                    String.format("L4 redisTtlSeconds must be positive for resource %s, got: %d",
                            resource, l4.getRedisTtlSeconds()));
        }

        if (l4.getLockWaitTimeMs() <= 0) {
            throw new IllegalArgumentException(
                    String.format("L4 lockWaitTimeMs must be positive for resource %s, got: %d",
                            resource, l4.getLockWaitTimeMs()));
        }

        if (l4.getLockLeaseTimeMs() <= 0) {
            throw new IllegalArgumentException(
                    String.format("L4 lockLeaseTimeMs must be positive for resource %s, got: %d",
                            resource, l4.getLockLeaseTimeMs()));
        }

        if (l4.getLockLeaseTimeMs() < l4.getLockWaitTimeMs()) {
            log.warn("L4 lockLeaseTimeMs ({}) < lockWaitTimeMs ({}) for resource {}, may cause lock issues",
                    l4.getLockLeaseTimeMs(), l4.getLockWaitTimeMs(), resource);
        }
    }

    /**
     * 验证一致性配置
     */
    private void validateConsistencyConfig(String resource, HotArmorRule.ConsistencyConfig consistency) {
        if (consistency.isEnableDelayedDoubleDelete() && consistency.getDelayTimeMs() <= 0) {
            throw new IllegalArgumentException(
                    String.format("Consistency delayTimeMs must be positive when delayed delete is enabled for resource %s, got: %d",
                            resource, consistency.getDelayTimeMs()));
        }

        if (consistency.getBroadcastChannel() == null || consistency.getBroadcastChannel().isEmpty()) {
            throw new IllegalArgumentException(
                    String.format("Consistency broadcastChannel cannot be null or empty for resource %s", resource));
        }
    }

    /**
     * 应用规则（首次加载）
     *
     * @param rule 规则
     */
    private void applyRule(HotArmorRule rule) {
        String resource = rule.getResource();
        log.info("Applying rule: {}", resource);

        // 应用 L1 配置
        if (rule.getL1Config() != null && rule.getL1Config().isEnabled()) {
            l1CacheEngine.getOrCreateCache(resource, rule.getL1Config());
        }

        // 应用 L2 配置
        if (rule.getL2Config() != null && rule.getL2Config().isEnabled()) {
            l2NoiseFilter.getOrCreateCounter(resource, rule.getL2Config());
        }

        // 应用 L3 配置
        if (rule.getL3Config() != null && rule.getL3Config().isEnabled()) {
            l3HotspotDetector.updateRule(
                    resource,
                    rule.getL3Config().getQpsThreshold(),
                    rule.getL3Config().getDurationInSec()
            );
        }

        // 应用 L4 配置
        if (rule.getL4Config() != null) {
            l4SafeLoader.registerConfig(resource, rule.getL4Config());
        }

        // 应用一致性配置
        if (rule.getConsistencyConfig() != null) {
            consistencyManager.registerConfig(resource, rule.getConsistencyConfig());
        }
    }

    /**
     * 更新规则（比对差异）
     *
     * @param oldRule 旧规则
     * @param newRule 新规则
     */
    private void updateRule(HotArmorRule oldRule, HotArmorRule newRule) {
        String resource = newRule.getResource();
        log.info("更新 rule: {}", resource);

        // 比对 L1 配置
        if (needRebuildL1(oldRule.getL1Config(), newRule.getL1Config())) {
            log.info("重建 L1 cache for resource: {}", resource);
            l1CacheEngine.rebuildCache(resource, newRule.getL1Config());
        }

        // 比对 L2 配置
        if (needRebuildL2(oldRule.getL2Config(), newRule.getL2Config())) {
            log.info("重建 L2 counter for resource: {}", resource);
            l2NoiseFilter.rebuildCounter(resource, newRule.getL2Config());
        }

        // 比对 L3 配置（热更新）
        if (needUpdateL3(oldRule.getL3Config(), newRule.getL3Config())) {
            log.info("更新 L3 Sentinel rule for resource: {}", resource);
            l3HotspotDetector.updateRule(
                    resource,
                    newRule.getL3Config().getQpsThreshold(),
                    newRule.getL3Config().getDurationInSec()
            );
        }

        // 更新 L4 配置
        if (newRule.getL4Config() != null) {
            l4SafeLoader.registerConfig(resource, newRule.getL4Config());
        }

        // 更新一致性配置
        if (newRule.getConsistencyConfig() != null) {
            consistencyManager.registerConfig(resource, newRule.getConsistencyConfig());
        }
    }

    /**
     * 判断是否需要重建 L1 缓存
     *
     * @param oldConfig 旧配置
     * @param newConfig 新配置
     * @return true 需要重建
     */
    private boolean needRebuildL1(HotArmorRule.L1CacheConfig oldConfig, HotArmorRule.L1CacheConfig newConfig) {
        if (oldConfig == null || newConfig == null) {
            return true;
        }

        // 容量或 TTL 变化需要重建
        return oldConfig.getMaximumSize() != newConfig.getMaximumSize()
                || oldConfig.getExpireAfterWrite() != newConfig.getExpireAfterWrite()
                || !oldConfig.getTimeUnit().equals(newConfig.getTimeUnit());
    }

    /**
     * 判断是否需要重建 L2 计数器
     *
     * @param oldConfig 旧配置
     * @param newConfig 新配置
     * @return true 需要重建
     */
    private boolean needRebuildL2(HotArmorRule.L2FilterConfig oldConfig, HotArmorRule.L2FilterConfig newConfig) {
        if (oldConfig == null || newConfig == null) {
            return true;
        }

        // 窗口时间变化需要重建
        return oldConfig.getWindowSeconds() != newConfig.getWindowSeconds();
    }

    /**
     * 判断是否需要更新 L3 规则
     *
     * @param oldConfig 旧配置
     * @param newConfig 新配置
     * @return true 需要更新
     */
    private boolean needUpdateL3(HotArmorRule.L3HotspotConfig oldConfig, HotArmorRule.L3HotspotConfig newConfig) {
        if (oldConfig == null || newConfig == null) {
            return true;
        }

        // QPS 阈值或统计时长变化需要更新
        return oldConfig.getQpsThreshold() != newConfig.getQpsThreshold()
                || oldConfig.getDurationInSec() != newConfig.getDurationInSec();
    }

    /**
     * 通知规则添加
     */
    private void notifyRuleAdded(HotArmorRule rule) {
        for (RuleChangeListener listener : listeners) {
            try {
                listener.onRuleAdded(rule);
            } catch (Exception e) {
                log.error("失败: notify rule added: {}", rule.getResource(), e);
            }
        }
    }

    /**
     * 通知规则更新
     */
    private void notifyRuleUpdated(HotArmorRule oldRule, HotArmorRule newRule) {
        for (RuleChangeListener listener : listeners) {
            try {
                listener.onRuleUpdated(oldRule, newRule);
            } catch (Exception e) {
                log.error("失败: notify rule updated: {}", newRule.getResource(), e);
            }
        }
    }

    /**
     * 通知规则删除
     */
    private void notifyRuleRemoved(HotArmorRule rule) {
        for (RuleChangeListener listener : listeners) {
            try {
                listener.onRuleRemoved(rule);
            } catch (Exception e) {
                log.error("失败: notify rule removed: {}", rule.getResource(), e);
            }
        }
    }
}
