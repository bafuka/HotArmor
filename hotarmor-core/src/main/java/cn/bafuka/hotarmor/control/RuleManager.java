package cn.bafuka.hotarmor.control;

import cn.bafuka.hotarmor.model.HotArmorRule;

import java.util.List;

/**
 * 规则管理器接口
 * 核心大脑，负责规则的加载、更新、比对和生效
 */
public interface RuleManager {

    /**
     * 初始化规则管理器
     */
    void initialize();

    /**
     * 加载规则（首次加载或全量更新）
     *
     * @param rules 规则列表
     */
    void loadRules(List<HotArmorRule> rules);

    /**
     * 更新规则（增量更新）
     * 会自动比对差异，决定是热更新参数还是重建实例
     *
     * @param rules 新的规则列表
     */
    void updateRules(List<HotArmorRule> rules);

    /**
     * 根据资源名称获取规则
     *
     * @param resource 资源名称
     * @return 规则配置，不存在返回 null
     */
    HotArmorRule getRule(String resource);

    /**
     * 获取所有规则
     *
     * @return 所有规则列表
     */
    List<HotArmorRule> getAllRules();

    /**
     * 删除规则
     *
     * @param resource 资源名称
     */
    void removeRule(String resource);

    /**
     * 清空所有规则
     */
    void clearAll();

    /**
     * 关闭规则管理器
     */
    void shutdown();
}
