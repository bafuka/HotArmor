package cn.bafuka.hotarmor.example.controller;

import cn.bafuka.hotarmor.control.RuleManager;
import cn.bafuka.hotarmor.dataplane.impl.SentinelL3HotspotDetector;
import cn.bafuka.hotarmor.model.HotArmorRule;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRuleManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 诊断控制器
 * 用于查看 HotArmor 的运行状态和配置
 */
@Slf4j
@RestController
@RequestMapping("/api/diagnostic")
public class DiagnosticController {

    @Autowired
    private RuleManager ruleManager;

    @Autowired(required = false)
    private SentinelL3HotspotDetector l3HotspotDetector;

    /**
     * 查看所有规则配置
     */
    @GetMapping("/rules")
    public Map<String, Object> getRules() {
        List<HotArmorRule> rules = ruleManager.getAllRules();

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("total", rules.size());
        result.put("rules", rules);

        return result;
    }

    /**
     * 查看 Sentinel 规则
     */
    @GetMapping("/sentinel-rules")
    public Map<String, Object> getSentinelRules() {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);

        // 获取 Sentinel 全局规则
        List<ParamFlowRule> sentinelRules = ParamFlowRuleManager.getRules();
        result.put("total", sentinelRules.size());

        // 格式化规则信息
        List<Map<String, Object>> ruleDetails = sentinelRules.stream().map(rule -> {
            Map<String, Object> detail = new HashMap<>();
            detail.put("resource", rule.getResource());
            detail.put("paramIdx", rule.getParamIdx());
            detail.put("qpsThreshold", rule.getCount());
            detail.put("durationInSec", rule.getDurationInSec());
            detail.put("grade", rule.getGrade());
            return detail;
        }).collect(Collectors.toList());

        result.put("rules", ruleDetails);

        if (sentinelRules.isEmpty()) {
            result.put("warning", "没有找到 Sentinel 规则，请检查配置是否正确加载");
        }

        return result;
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);

        // 检查规则数量
        int ruleCount = ruleManager.getAllRules().size();
        result.put("totalRules", ruleCount);

        // 检查 Sentinel 规则
        int sentinelRuleCount = ParamFlowRuleManager.getRules().size();
        result.put("sentinelRules", sentinelRuleCount);

        // 健康状态
        boolean healthy = ruleCount > 0 && sentinelRuleCount > 0;
        result.put("healthy", healthy);

        if (!healthy) {
            result.put("message", "警告：规则未正确加载");
        } else {
            result.put("message", "HotArmor 运行正常");
        }

        return result;
    }

    /**
     * 查看具体资源的配置
     */
    @GetMapping("/rule")
    public Map<String, Object> getRule(String resource) {
        Map<String, Object> result = new HashMap<>();

        if (resource == null || resource.isEmpty()) {
            result.put("success", false);
            result.put("message", "resource 参数不能为空");
            return result;
        }

        HotArmorRule rule = ruleManager.getRule(resource);
        if (rule == null) {
            result.put("success", false);
            result.put("message", "未找到资源: " + resource);
            return result;
        }

        result.put("success", true);
        result.put("rule", rule);

        // 查找对应的 Sentinel 规则
        List<ParamFlowRule> sentinelRules = ParamFlowRuleManager.getRules().stream()
                .filter(r -> r.getResource().equals(resource))
                .collect(Collectors.toList());
        result.put("sentinelRule", sentinelRules.isEmpty() ? null : sentinelRules.get(0));

        return result;
    }
}
