package cn.bafuka.hotarmor.spi.impl;

import cn.bafuka.hotarmor.model.HotArmorRule;
import cn.bafuka.hotarmor.spi.ConfigSource;
import com.alibaba.fastjson.JSON;
import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Nacos 配置源适配器实现
 * 支持动态配置热更新
 */
@Slf4j
public class NacosConfigSource implements ConfigSource {

    /**
     * Nacos 服务地址
     */
    private final String serverAddr;

    /**
     * Nacos 命名空间
     */
    private final String namespace;

    /**
     * Nacos DataId
     */
    private final String dataId;

    /**
     * Nacos Group
     */
    private final String group;

    /**
     * Nacos ConfigService
     */
    private ConfigService configService;

    /**
     * 配置监听器
     */
    private Consumer<List<HotArmorRule>> listener;

    /**
     * Nacos 内部监听器
     */
    private NacosListener nacosListener;

    public NacosConfigSource(String serverAddr, String namespace, String dataId, String group) {
        this.serverAddr = serverAddr;
        this.namespace = namespace;
        this.dataId = dataId;
        this.group = group;
    }

    @Override
    public void subscribe(Consumer<List<HotArmorRule>> listener) {
        this.listener = listener;

        try {
            // 创建 Nacos ConfigService
            java.util.Properties properties = new java.util.Properties();
            properties.put("serverAddr", serverAddr);
            if (namespace != null && !namespace.isEmpty()) {
                properties.put("namespace", namespace);
            }

            configService = NacosFactory.createConfigService(properties);

            // 初次加载配置
            String config = configService.getConfig(dataId, group, 5000);
            handleConfigChange(config);

            // 添加监听器
            nacosListener = new NacosListener();
            configService.addListener(dataId, group, nacosListener);

            log.info("Subscribed to Nacos config: serverAddr={}, namespace={}, dataId={}, group={}",
                    serverAddr, namespace, dataId, group);

        } catch (NacosException e) {
            log.error("失败: subscribe to Nacos config", e);
            throw new RuntimeException("Failed to subscribe to Nacos config", e);
        }
    }

    @Override
    public List<HotArmorRule> getCurrentConfig() {
        if (configService == null) {
            return new ArrayList<>();
        }

        try {
            String config = configService.getConfig(dataId, group, 5000);
            return parseConfig(config);
        } catch (NacosException e) {
            log.error("失败: get current config from Nacos", e);
            return new ArrayList<>();
        }
    }

    @Override
    public void shutdown() {
        if (configService != null && nacosListener != null) {
            try {
                configService.removeListener(dataId, group, nacosListener);
                log.info("已关闭 NacosConfigSource");
            } catch (Exception e) {
                log.error("失败: remove Nacos listener", e);
            }
        }
    }

    @Override
    public String getType() {
        return "nacos";
    }

    /**
     * 处理配置变更
     *
     * @param config 配置内容
     */
    private void handleConfigChange(String config) {
        if (config == null || config.trim().isEmpty()) {
            log.warn("Received empty config from Nacos");
            return;
        }

        try {
            List<HotArmorRule> rules = parseConfig(config);
            log.info("Parsed {} rules from Nacos config", rules.size());

            if (listener != null) {
                listener.accept(rules);
            }

        } catch (Exception e) {
            log.error("失败: handle config change: {}", config, e);
        }
    }

    /**
     * 解析配置
     *
     * @param config JSON 配置字符串
     * @return 规则列表
     */
    private List<HotArmorRule> parseConfig(String config) {
        if (config == null || config.trim().isEmpty()) {
            return new ArrayList<>();
        }

        try {
            return JSON.parseArray(config, HotArmorRule.class);
        } catch (Exception e) {
            log.error("失败: parse config: {}", config, e);
            return new ArrayList<>();
        }
    }

    /**
     * Nacos 监听器实现
     */
    private class NacosListener implements Listener {

        @Override
        public void receiveConfigInfo(String configInfo) {
            log.info("Received config change from Nacos, length={}", configInfo != null ? configInfo.length() : 0);
            handleConfigChange(configInfo);
        }

        @Override
        public Executor getExecutor() {
            // 返回 null 使用默认线程池
            return null;
        }
    }
}
