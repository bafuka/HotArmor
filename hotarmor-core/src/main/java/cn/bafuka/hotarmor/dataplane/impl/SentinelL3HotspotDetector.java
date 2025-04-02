package cn.bafuka.hotarmor.dataplane.impl;

import cn.bafuka.hotarmor.core.HotArmorContext;
import cn.bafuka.hotarmor.dataplane.L3HotspotDetector;
import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.EntryType;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRuleManager;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * L3 热点探测器实现
 * 基于 Sentinel 热点参数流控
 */
@Slf4j
public class SentinelL3HotspotDetector implements L3HotspotDetector {

    /**
     * 规则缓存
     * Key: resource 名称
     * Value: 规则对象
     */
    private final Map<String, ParamFlowRule> ruleMap = new ConcurrentHashMap<>();

    @Override
    public boolean isHotspot(HotArmorContext context) {
        if (context == null || context.getResource() == null) {
            return false;
        }

        String resource = context.getResource();
        Entry entry = null;

        try {
            // 使用 Sentinel 进行热点参数检测
            // 参数索引 0 表示使用 context.getKey() 作为热点参数
            entry = SphU.entry(resource, EntryType.OUT, 1, context.getKey());

            // 未被限流，返回 false（不是热点）
            log.debug("L3 探测通过: resource={}, key={}", resource, context.getKey());
            return false;

        } catch (BlockException e) {
            // 被限流，说明触发热点
            log.info("L3 探测触发（热点！）: resource={}, key={}", resource, context.getKey());
            return true;

        } finally {
            if (entry != null) {
                entry.exit(1, context.getKey());
            }
        }
    }

    @Override
    public void updateRule(String resource, double qpsThreshold, int durationInSec) {
        log.info("更新 L3 Sentinel 规则: resource={}, qpsThreshold={}, durationInSec={}",
                resource, qpsThreshold, durationInSec);

        // 创建热点参数规则
        ParamFlowRule rule = new ParamFlowRule(resource)
                .setParamIdx(0)  // 参数索引 0
                .setGrade(RuleConstant.FLOW_GRADE_QPS)  // QPS 限流
                .setCount(qpsThreshold)  // QPS 阈值
                .setDurationInSec(durationInSec); // 统计窗口

        ruleMap.put(resource, rule);

        // 加载规则到 Sentinel
        loadRulesToSentinel();
    }

    @Override
    public void removeRule(String resource) {
        log.info("移除 L3 Sentinel 规则: resource={}", resource);
        ruleMap.remove(resource);
        loadRulesToSentinel();
    }

    @Override
    public double getCurrentQps(String resource) {
        // Sentinel 默认不直接暴露 QPS 查询接口
        // 可以通过 ClusterNode 或 StatisticNode 获取，这里简化处理
        // 实际生产环境可以集成 Sentinel Dashboard 查看
        log.debug("获取当前 QPS: resource={}", resource);
        return 0.0;
    }

    /**
     * 将所有规则加载到 Sentinel
     */
    private void loadRulesToSentinel() {
        List<ParamFlowRule> rules = new ArrayList<>(ruleMap.values());
        ParamFlowRuleManager.loadRules(rules);
        log.info("======================================");
        log.info("已加载 {} 条 Sentinel 参数流控规则:", rules.size());
        for (ParamFlowRule rule : rules) {
            log.info("  - Resource: {}, QPS: {}, Duration: {}s",
                    rule.getResource(),
                    rule.getCount(),
                    rule.getDurationInSec());
        }
        log.info("======================================");
    }

    /**
     * 获取所有规则（用于监控）
     *
     * @return 规则映射
     */
    public Map<String, ParamFlowRule> getAllRules() {
        return Collections.unmodifiableMap(ruleMap);
    }

    /**
     * 初始化方法（可选）
     */
    public void initialize() {
        log.info("初始化 Sentinel L3 热点探测器");
        // 可以在这里进行一些初始化配置
    }
}
