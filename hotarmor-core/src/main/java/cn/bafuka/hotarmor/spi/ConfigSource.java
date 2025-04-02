package cn.bafuka.hotarmor.spi;

import cn.bafuka.hotarmor.model.HotArmorRule;

import java.util.List;
import java.util.function.Consumer;

/**
 * 配置源 SPI 接口
 * 用于对接不同的配置中心（Nacos、Apollo、Etcd 等）
 *
 * 实现类需要在 META-INF/services 目录下注册
 */
public interface ConfigSource {

    /**
     * 订阅配置变更
     *
     * @param listener 配置变更监听器，接收新的规则列表
     */
    void subscribe(Consumer<List<HotArmorRule>> listener);

    /**
     * 获取当前配置（同步方式）
     *
     * @return 当前的规则列表
     */
    List<HotArmorRule> getCurrentConfig();

    /**
     * 停止订阅
     */
    void shutdown();

    /**
     * 配置源类型标识
     *
     * @return 类型名称（如 "nacos", "apollo", "local"）
     */
    String getType();
}
