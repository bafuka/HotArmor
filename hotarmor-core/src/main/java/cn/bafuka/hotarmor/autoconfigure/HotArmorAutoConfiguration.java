package cn.bafuka.hotarmor.autoconfigure;

import cn.bafuka.hotarmor.aspect.HotArmorAspect;
import cn.bafuka.hotarmor.aspect.HotArmorAspectHandler;
import cn.bafuka.hotarmor.aspect.impl.DefaultHotArmorAspectHandler;
import cn.bafuka.hotarmor.config.HotArmorProperties;
import cn.bafuka.hotarmor.consistency.BroadcastNotifier;
import cn.bafuka.hotarmor.consistency.ConsistencyManager;
import cn.bafuka.hotarmor.consistency.DelayedDeleteConsumer;
import cn.bafuka.hotarmor.consistency.DelayedDeleteProducer;
import cn.bafuka.hotarmor.consistency.impl.DefaultConsistencyManager;
import cn.bafuka.hotarmor.consistency.impl.RedisBroadcastNotifier;
import cn.bafuka.hotarmor.consistency.impl.RocketMQDelayedDeleteConsumer;
import cn.bafuka.hotarmor.consistency.impl.RocketMQDelayedDeleteProducer;
import cn.bafuka.hotarmor.control.RuleManager;
import cn.bafuka.hotarmor.control.impl.DefaultRuleManager;
import cn.bafuka.hotarmor.dataplane.L1CacheEngine;
import cn.bafuka.hotarmor.dataplane.L2NoiseFilter;
import cn.bafuka.hotarmor.dataplane.L3HotspotDetector;
import cn.bafuka.hotarmor.dataplane.L4SafeLoader;
import cn.bafuka.hotarmor.dataplane.impl.CaffeineL1CacheEngine;
import cn.bafuka.hotarmor.dataplane.impl.CaffeineL2NoiseFilter;
import cn.bafuka.hotarmor.dataplane.impl.RedissonL4SafeLoader;
import cn.bafuka.hotarmor.dataplane.impl.SentinelL3HotspotDetector;
import cn.bafuka.hotarmor.spi.ConfigSource;
import cn.bafuka.hotarmor.spi.impl.LocalYamlConfigSource;
import cn.bafuka.hotarmor.spel.DefaultSpelExpressionParser;
import cn.bafuka.hotarmor.spel.SpelExpressionParser;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * HotArmor 自动配置类
 */
@Slf4j
@Configuration
@EnableAspectJAutoProxy
@EnableConfigurationProperties(HotArmorProperties.class)
@ConditionalOnProperty(prefix = "hotarmor", name = "enabled", havingValue = "true", matchIfMissing = true)
public class HotArmorAutoConfiguration {

    public HotArmorAutoConfiguration() {
        log.info("HotArmor auto-configuration initializing...");
    }

    /**
     * SpEL 表达式解析器
     */
    @Bean
    @ConditionalOnMissingBean
    public SpelExpressionParser spelExpressionParser() {
        return new DefaultSpelExpressionParser();
    }

    /**
     * L1 缓存引擎
     */
    @Bean
    @ConditionalOnMissingBean
    public CaffeineL1CacheEngine<Object> caffeineL1CacheEngine() {
        return new CaffeineL1CacheEngine<>();
    }

    /**
     * L2 噪音过滤器
     */
    @Bean
    @ConditionalOnMissingBean
    public CaffeineL2NoiseFilter caffeineL2NoiseFilter() {
        return new CaffeineL2NoiseFilter();
    }

    /**
     * L3 热点探测器
     */
    @Bean
    @ConditionalOnMissingBean
    public SentinelL3HotspotDetector sentinelL3HotspotDetector() {
        SentinelL3HotspotDetector detector = new SentinelL3HotspotDetector();
        detector.initialize();
        return detector;
    }

    /**
     * L4 安全回源器
     */
    @Bean
    @ConditionalOnMissingBean
    public RedissonL4SafeLoader<Object> redissonL4SafeLoader(
            RedissonClient redissonClient,
            RedisTemplate<String, Object> redisTemplate) {
        return new RedissonL4SafeLoader<>(redissonClient, redisTemplate);
    }

    /**
     * 延迟双删生产者（仅当 RocketMQTemplate 存在时创建）
     */
    @Bean
    @ConditionalOnBean(RocketMQTemplate.class)
    @ConditionalOnMissingBean
    public RocketMQDelayedDeleteProducer rocketMQDelayedDeleteProducer(
            RocketMQTemplate rocketMQTemplate,
            HotArmorProperties properties) {
        return new RocketMQDelayedDeleteProducer(rocketMQTemplate, properties.getDelayedDeleteTopic());
    }

    /**
     * 延迟双删消费者（仅当 RocketMQTemplate 存在时创建）
     */
    @Bean
    @ConditionalOnBean(RocketMQTemplate.class)
    @ConditionalOnMissingBean
    public RocketMQDelayedDeleteConsumer rocketMQDelayedDeleteConsumer(
            L4SafeLoader<?> l4SafeLoader) {
        RocketMQDelayedDeleteConsumer consumer = new RocketMQDelayedDeleteConsumer(l4SafeLoader);
        consumer.start();
        return consumer;
    }

    /**
     * 广播通知器
     */
    @Bean
    @ConditionalOnMissingBean
    public RedisBroadcastNotifier redisBroadcastNotifier(
            RedisTemplate<String, Object> redisTemplate,
            RedisMessageListenerContainer listenerContainer,
            HotArmorProperties properties) {
        return new RedisBroadcastNotifier(redisTemplate, listenerContainer, properties.getBroadcastChannel());
    }

    /**
     * 一致性管理器（DelayedDeleteProducer 为可选依赖）
     */
    @Bean
    @ConditionalOnMissingBean
    public DefaultConsistencyManager consistencyManager(
            L1CacheEngine<?> l1CacheEngine,
            L4SafeLoader<?> l4SafeLoader,
            @Autowired(required = false) DelayedDeleteProducer delayedDeleteProducer,
            BroadcastNotifier broadcastNotifier) {
        DefaultConsistencyManager manager = new DefaultConsistencyManager(
                l1CacheEngine,
                l4SafeLoader,
                delayedDeleteProducer,
                broadcastNotifier
        );
        manager.initialize();
        return manager;
    }

    /**
     * 规则管理器
     */
    @Bean
    @ConditionalOnMissingBean
    public DefaultRuleManager ruleManager(
            CaffeineL1CacheEngine<?> l1CacheEngine,
            CaffeineL2NoiseFilter l2NoiseFilter,
            SentinelL3HotspotDetector l3HotspotDetector,
            RedissonL4SafeLoader<?> l4SafeLoader,
            DefaultConsistencyManager consistencyManager) {
        DefaultRuleManager manager = new DefaultRuleManager(
                l1CacheEngine,
                l2NoiseFilter,
                l3HotspotDetector,
                l4SafeLoader,
                consistencyManager
        );
        manager.initialize();
        return manager;
    }

    /**
     * 本地 YAML 配置源
     */
    @Bean
    @ConditionalOnMissingBean
    public LocalYamlConfigSource localYamlConfigSource(HotArmorProperties properties) {
        return new LocalYamlConfigSource(properties);
    }

    /**
     * 配置源初始化器
     */
    @Bean
    public HotArmorConfigInitializer hotArmorConfigInitializer(
            ConfigSource configSource,
            RuleManager ruleManager) {
        HotArmorConfigInitializer initializer = new HotArmorConfigInitializer(configSource, ruleManager);
        initializer.initialize();
        return initializer;
    }

    /**
     * 切面处理器
     */
    @Bean
    @ConditionalOnMissingBean
    public HotArmorAspectHandler hotArmorAspectHandler(
            L1CacheEngine<Object> l1CacheEngine,
            L2NoiseFilter l2NoiseFilter,
            L3HotspotDetector l3HotspotDetector,
            L4SafeLoader<Object> l4SafeLoader,
            ConsistencyManager consistencyManager) {
        return new DefaultHotArmorAspectHandler(
                l1CacheEngine,
                l2NoiseFilter,
                l3HotspotDetector,
                l4SafeLoader,
                consistencyManager
        );
    }

    /**
     * AOP 切面
     */
    @Bean
    @ConditionalOnMissingBean
    public HotArmorAspect hotArmorAspect(
            SpelExpressionParser spelParser,
            HotArmorAspectHandler aspectHandler) {
        return new HotArmorAspect(spelParser, aspectHandler);
    }

    /**
     * 配置源初始化器（内部类）
     */
    @Slf4j
    public static class HotArmorConfigInitializer {

        private final ConfigSource configSource;
        private final RuleManager ruleManager;

        public HotArmorConfigInitializer(ConfigSource configSource, RuleManager ruleManager) {
            this.configSource = configSource;
            this.ruleManager = ruleManager;
        }

        public void initialize() {
            log.info("初始化 HotArmor config from source: {}", configSource.getType());

            // 订阅配置变更
            configSource.subscribe(rules -> {
                log.info("Received config update, updating rules...");
                ruleManager.updateRules(rules);
            });

            log.info("HotArmor initialized successfully");
        }
    }
}
