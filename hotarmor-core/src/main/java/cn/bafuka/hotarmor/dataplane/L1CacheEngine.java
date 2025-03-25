package cn.bafuka.hotarmor.dataplane;

import cn.bafuka.hotarmor.core.HotArmorContext;

/**
 * L1 本地缓存引擎接口
 * 基于 Caffeine 实现的高性能本地缓存
 *
 * @param <V> 缓存值类型
 */
public interface L1CacheEngine<V> {

    /**
     * 从 L1 缓存中获取数据
     *
     * @param context 上下文信息
     * @return 缓存值，未命中返回 null
     */
    V get(HotArmorContext context);

    /**
     * 将数据写入 L1 缓存
     *
     * @param context 上下文信息
     * @param value   要缓存的值
     */
    void put(HotArmorContext context, V value);

    /**
     * 使指定键的缓存失效
     *
     * @param context 上下文信息
     */
    void invalidate(HotArmorContext context);

    /**
     * 清空当前资源的所有缓存
     *
     * @param resource 资源名称
     */
    void invalidateResource(String resource);

    /**
     * 获取命中率等统计信息
     *
     * @param resource 资源名称
     * @return 统计信息
     */
    String getStats(String resource);
}
