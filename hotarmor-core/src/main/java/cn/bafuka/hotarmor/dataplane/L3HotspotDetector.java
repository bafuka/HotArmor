package cn.bafuka.hotarmor.dataplane;

import cn.bafuka.hotarmor.core.HotArmorContext;

/**
 * L3 热点探测器接口
 * 基于 Sentinel 的热点参数流控，精确判定是否触发热点降级
 */
public interface L3HotspotDetector {

    /**
     * 检查是否触发热点限流
     *
     * @param context 上下文信息
     * @return true 表示触发热点（需要晋升到 L1），false 表示正常通过
     */
    boolean isHotspot(HotArmorContext context);

    /**
     * 更新 Sentinel 规则
     *
     * @param resource     资源名称
     * @param qpsThreshold QPS 阈值
     * @param durationInSec 统计时长
     */
    void updateRule(String resource, double qpsThreshold, int durationInSec);

    /**
     * 移除规则
     *
     * @param resource 资源名称
     */
    void removeRule(String resource);

    /**
     * 获取当前 QPS
     *
     * @param resource 资源名称
     * @return 当前 QPS 值
     */
    double getCurrentQps(String resource);
}
