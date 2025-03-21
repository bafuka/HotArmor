package cn.bafuka.hotarmor.control;

import cn.bafuka.hotarmor.model.HotArmorRule;

/**
 * 规则变更监听器接口
 * 当规则发生变更时，由 RuleManager 触发回调
 */
public interface RuleChangeListener {

    /**
     * 规则添加时触发
     *
     * @param rule 新增的规则
     */
    void onRuleAdded(HotArmorRule rule);

    /**
     * 规则更新时触发
     * 注意：这里可能是参数微调（热更新）或结构变化（需要重建实例）
     *
     * @param oldRule 旧规则
     * @param newRule 新规则
     */
    void onRuleUpdated(HotArmorRule oldRule, HotArmorRule newRule);

    /**
     * 规则删除时触发
     *
     * @param rule 被删除的规则
     */
    void onRuleRemoved(HotArmorRule rule);
}
