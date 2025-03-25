package cn.bafuka.hotarmor.dataplane;

import cn.bafuka.hotarmor.core.HotArmorContext;

import java.util.function.Function;

/**
 * L4 安全回源器接口
 * 提供防击穿的数据加载能力，支持从 Redis 和 DB 回源
 *
 * @param <V> 数据类型
 */
public interface L4SafeLoader<V> {

    /**
     * 安全加载数据（带防击穿保护）
     * 执行流程：查 Redis -> 查 DB（带分布式锁） -> 回写 Redis
     *
     * @param context    上下文信息
     * @param dbLoader   数据库加载函数
     * @return 加载的数据
     */
    V load(HotArmorContext context, Function<Object, V> dbLoader);

    /**
     * 从 Redis 获取数据
     *
     * @param context 上下文信息
     * @return Redis 中的数据，不存在返回 null
     */
    V getFromRedis(HotArmorContext context);

    /**
     * 将数据写入 Redis
     *
     * @param context 上下文信息
     * @param value   要缓存的值
     */
    void putToRedis(HotArmorContext context, V value);

    /**
     * 删除 Redis 中的数据
     *
     * @param context 上下文信息
     */
    void deleteFromRedis(HotArmorContext context);

    /**
     * 获取 Redis 键名
     *
     * @param context 上下文信息
     * @return Redis 键
     */
    String getRedisKey(HotArmorContext context);
}
