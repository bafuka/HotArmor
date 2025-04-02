package cn.bafuka.hotarmor.spi.impl;

import cn.bafuka.hotarmor.config.HotArmorProperties;
import cn.bafuka.hotarmor.model.HotArmorRule;
import cn.bafuka.hotarmor.spi.ConfigSource;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 本地 YAML 配置源实现
 * 从 Spring Boot 配置文件读取规则
 */
@Slf4j
public class LocalYamlConfigSource implements ConfigSource {

    /**
     * HotArmor 配置属性
     */
    private final HotArmorProperties properties;

    /**
     * 配置监听器
     */
    private Consumer<List<HotArmorRule>> listener;

    public LocalYamlConfigSource(HotArmorProperties properties) {
        this.properties = properties;
    }

    @Override
    public void subscribe(Consumer<List<HotArmorRule>> listener) {
        this.listener = listener;

        // 本地配置源只在启动时加载一次
        log.info("加载 rules from local YAML configuration");

        List<HotArmorRule> rules = getCurrentConfig();
        if (listener != null && !rules.isEmpty()) {
            listener.accept(rules);
        }

        log.info("已加载 {} rules from local YAML", rules.size());
    }

    @Override
    public List<HotArmorRule> getCurrentConfig() {
        if (properties == null || properties.getRules() == null) {
            return new ArrayList<>();
        }

        return new ArrayList<>(properties.getRules());
    }

    @Override
    public void shutdown() {
        log.info("关闭 LocalYamlConfigSource");
        listener = null;
    }

    @Override
    public String getType() {
        return "local";
    }
}
