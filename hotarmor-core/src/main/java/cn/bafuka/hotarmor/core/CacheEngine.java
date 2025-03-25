package cn.bafuka.hotarmor.core;

/**
 * 缓存引擎核心接口
 * 定义了缓存的基本操作，支持泛型
 *
 * @param <K> 缓存键类型
 * @param <V> 缓存值类型
 */
public interface CacheEngine<K, V> {

    /**
     * 获取缓存值
     *
     * @param key 缓存键
     * @return 缓存值，不存在返回 null
     */
    V get(K key);

    /**
     * 写入缓存
     *
     * @param key   缓存键
     * @param value 缓存值
     */
    void put(K key, V value);

    /**
     * 删除缓存
     *
     * @param key 缓存键
     */
    void invalidate(K key);

    /**
     * 清空所有缓存
     */
    void invalidateAll();

    /**
     * 获取缓存统计信息
     *
     * @return 统计信息对象
     */
    CacheStats getStats();
}
