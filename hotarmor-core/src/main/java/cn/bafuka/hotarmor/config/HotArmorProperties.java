package cn.bafuka.hotarmor.config;

import cn.bafuka.hotarmor.model.HotArmorRule;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * HotArmor 配置属性
 * 从 application.yml 读取配置
 */
@Data
@ConfigurationProperties(prefix = "hotarmor")
public class HotArmorProperties {

    /**
     * 是否启用 HotArmor
     */
    private boolean enabled = true;

    /**
     * 规则列表
     */
    private List<HotArmorRule> rules = new ArrayList<>();

    /**
     * RocketMQ 延迟删除 Topic
     */
    private String delayedDeleteTopic = "hotarmor-delayed-delete";

    /**
     * Redis 广播频道
     */
    private String broadcastChannel = "hotarmor:invalidate";
}
