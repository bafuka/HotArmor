package cn.bafuka.hotarmor.dataplane;

import cn.bafuka.hotarmor.core.HotArmorContext;
import cn.bafuka.hotarmor.dataplane.impl.CaffeineL1CacheEngine;
import cn.bafuka.hotarmor.model.HotArmorRule;
import com.github.benmanes.caffeine.cache.Cache;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * CaffeineL1CacheEngine 单元测试
 */
public class CaffeineL1CacheEngineTest {

    private CaffeineL1CacheEngine<String> cacheEngine;

    @Before
    public void setUp() {
        cacheEngine = new CaffeineL1CacheEngine<>();
    }

    /**
     * 测试基本的 get/put 操作
     */
    @Test
    public void testGetAndPut() {
        // 创建配置
        HotArmorRule.L1CacheConfig config = HotArmorRule.L1CacheConfig.builder()
                .maximumSize(1000)
                .expireAfterWrite(60)
                .timeUnit(TimeUnit.SECONDS)
                .enabled(true)
                .build();

        // 创建缓存实例
        String resource = "test:resource";
        cacheEngine.getOrCreateCache(resource, config);

        // 构建上下文
        HotArmorContext context = HotArmorContext.builder()
                .resource(resource)
                .key("key1")
                .build();

        // 测试 put 和 get
        cacheEngine.put(context, "value1");
        String value = cacheEngine.get(context);

        assertEquals("value1", value);
    }

    /**
     * 测试缓存未命中
     */
    @Test
    public void testGetMiss() {
        HotArmorRule.L1CacheConfig config = HotArmorRule.L1CacheConfig.builder()
                .maximumSize(1000)
                .expireAfterWrite(60)
                .timeUnit(TimeUnit.SECONDS)
                .build();

        String resource = "test:resource";
        cacheEngine.getOrCreateCache(resource, config);

        HotArmorContext context = HotArmorContext.builder()
                .resource(resource)
                .key("nonexistent")
                .build();

        // 未写入数据，应该返回 null
        String value = cacheEngine.get(context);
        assertNull(value);
    }

    /**
     * 测试缓存失效 (invalidate)
     */
    @Test
    public void testInvalidate() {
        HotArmorRule.L1CacheConfig config = HotArmorRule.L1CacheConfig.builder()
                .maximumSize(1000)
                .expireAfterWrite(60)
                .timeUnit(TimeUnit.SECONDS)
                .build();

        String resource = "test:resource";
        cacheEngine.getOrCreateCache(resource, config);

        HotArmorContext context = HotArmorContext.builder()
                .resource(resource)
                .key("key1")
                .build();

        // 写入数据
        cacheEngine.put(context, "value1");
        assertEquals("value1", cacheEngine.get(context));

        // 失效缓存
        cacheEngine.invalidate(context);
        assertNull(cacheEngine.get(context));
    }

    /**
     * 测试资源级别的失效
     */
    @Test
    public void testInvalidateResource() {
        HotArmorRule.L1CacheConfig config = HotArmorRule.L1CacheConfig.builder()
                .maximumSize(1000)
                .expireAfterWrite(60)
                .timeUnit(TimeUnit.SECONDS)
                .build();

        String resource = "test:resource";
        cacheEngine.getOrCreateCache(resource, config);

        // 写入多个键
        HotArmorContext context1 = HotArmorContext.builder()
                .resource(resource)
                .key("key1")
                .build();
        HotArmorContext context2 = HotArmorContext.builder()
                .resource(resource)
                .key("key2")
                .build();

        cacheEngine.put(context1, "value1");
        cacheEngine.put(context2, "value2");

        // 验证数据存在
        assertEquals("value1", cacheEngine.get(context1));
        assertEquals("value2", cacheEngine.get(context2));

        // 失效整个资源
        cacheEngine.invalidateResource(resource);

        // 验证所有数据都被清除
        assertNull(cacheEngine.get(context1));
        assertNull(cacheEngine.get(context2));
    }

    /**
     * 测试多资源隔离
     */
    @Test
    public void testMultipleResources() {
        HotArmorRule.L1CacheConfig config = HotArmorRule.L1CacheConfig.builder()
                .maximumSize(1000)
                .expireAfterWrite(60)
                .timeUnit(TimeUnit.SECONDS)
                .build();

        // 创建两个不同的资源
        String resource1 = "test:resource1";
        String resource2 = "test:resource2";
        cacheEngine.getOrCreateCache(resource1, config);
        cacheEngine.getOrCreateCache(resource2, config);

        // 在不同资源中使用相同的 key
        HotArmorContext context1 = HotArmorContext.builder()
                .resource(resource1)
                .key("sameKey")
                .build();
        HotArmorContext context2 = HotArmorContext.builder()
                .resource(resource2)
                .key("sameKey")
                .build();

        // 写入不同的值
        cacheEngine.put(context1, "value1");
        cacheEngine.put(context2, "value2");

        // 验证隔离性
        assertEquals("value1", cacheEngine.get(context1));
        assertEquals("value2", cacheEngine.get(context2));
    }

    /**
     * 测试缓存过期
     */
    @Test
    public void testExpiration() throws InterruptedException {
        // 配置 1 秒过期
        HotArmorRule.L1CacheConfig config = HotArmorRule.L1CacheConfig.builder()
                .maximumSize(1000)
                .expireAfterWrite(1)
                .timeUnit(TimeUnit.SECONDS)
                .build();

        String resource = "test:resource";
        cacheEngine.getOrCreateCache(resource, config);

        HotArmorContext context = HotArmorContext.builder()
                .resource(resource)
                .key("key1")
                .build();

        // 写入数据
        cacheEngine.put(context, "value1");
        assertEquals("value1", cacheEngine.get(context));

        // 等待过期
        Thread.sleep(1500);

        // 验证数据已过期
        assertNull(cacheEngine.get(context));
    }

    /**
     * 测试缓存大小限制
     */
    @Test
    public void testMaximumSize() {
        // 配置最大容量为 2
        HotArmorRule.L1CacheConfig config = HotArmorRule.L1CacheConfig.builder()
                .maximumSize(2)
                .expireAfterWrite(60)
                .timeUnit(TimeUnit.SECONDS)
                .build();

        String resource = "test:resource";
        cacheEngine.getOrCreateCache(resource, config);

        // 写入 3 个元素
        for (int i = 1; i <= 3; i++) {
            HotArmorContext context = HotArmorContext.builder()
                    .resource(resource)
                    .key("key" + i)
                    .build();
            cacheEngine.put(context, "value" + i);
        }

        // 由于 Caffeine 的异步驱逐，需要触发清理
        Cache<Object, String> cache = cacheEngine.getAllCaches().get(resource);
        cache.cleanUp();

        // 验证缓存大小不超过限制
        long size = cache.estimatedSize();
        assertTrue("Cache size should not exceed maximum", size <= 2);
    }

    /**
     * 测试缓存重建
     */
    @Test
    public void testRebuildCache() {
        HotArmorRule.L1CacheConfig config1 = HotArmorRule.L1CacheConfig.builder()
                .maximumSize(1000)
                .expireAfterWrite(60)
                .timeUnit(TimeUnit.SECONDS)
                .build();

        String resource = "test:resource";
        cacheEngine.getOrCreateCache(resource, config1);

        HotArmorContext context = HotArmorContext.builder()
                .resource(resource)
                .key("key1")
                .build();

        // 写入数据
        cacheEngine.put(context, "value1");
        assertEquals("value1", cacheEngine.get(context));

        // 重建缓存（使用新配置）
        HotArmorRule.L1CacheConfig config2 = HotArmorRule.L1CacheConfig.builder()
                .maximumSize(500)
                .expireAfterWrite(30)
                .timeUnit(TimeUnit.SECONDS)
                .build();

        cacheEngine.rebuildCache(resource, config2);

        // 验证旧数据被清除
        assertNull(cacheEngine.get(context));
    }

    /**
     * 测试空上下文处理
     */
    @Test
    public void testNullContext() {
        HotArmorRule.L1CacheConfig config = HotArmorRule.L1CacheConfig.builder()
                .maximumSize(1000)
                .expireAfterWrite(60)
                .timeUnit(TimeUnit.SECONDS)
                .build();

        String resource = "test:resource";
        cacheEngine.getOrCreateCache(resource, config);

        // 测试 null context
        assertNull(cacheEngine.get(null));

        // put 操作应该安全处理 null
        cacheEngine.put(null, "value");

        // invalidate 操作应该安全处理 null
        cacheEngine.invalidate(null);
    }

    /**
     * 测试空 resource 处理
     */
    @Test
    public void testNullResource() {
        HotArmorContext context = HotArmorContext.builder()
                .resource(null)
                .key("key1")
                .build();

        // 应该安全处理 null resource
        assertNull(cacheEngine.get(context));
        cacheEngine.put(context, "value");
        cacheEngine.invalidate(context);
    }

    /**
     * 测试空值处理
     */
    @Test
    public void testNullValue() {
        HotArmorRule.L1CacheConfig config = HotArmorRule.L1CacheConfig.builder()
                .maximumSize(1000)
                .expireAfterWrite(60)
                .timeUnit(TimeUnit.SECONDS)
                .build();

        String resource = "test:resource";
        cacheEngine.getOrCreateCache(resource, config);

        HotArmorContext context = HotArmorContext.builder()
                .resource(resource)
                .key("key1")
                .build();

        // put null 值应该被忽略
        cacheEngine.put(context, null);
        assertNull(cacheEngine.get(context));
    }

    /**
     * 测试统计信息
     */
    @Test
    public void testStats() {
        HotArmorRule.L1CacheConfig config = HotArmorRule.L1CacheConfig.builder()
                .maximumSize(1000)
                .expireAfterWrite(60)
                .timeUnit(TimeUnit.SECONDS)
                .build();

        String resource = "test:resource";
        cacheEngine.getOrCreateCache(resource, config);

        HotArmorContext context = HotArmorContext.builder()
                .resource(resource)
                .key("key1")
                .build();

        // 执行一些操作
        cacheEngine.put(context, "value1");
        cacheEngine.get(context);  // hit

        HotArmorContext context2 = HotArmorContext.builder()
                .resource(resource)
                .key("key2")
                .build();
        cacheEngine.get(context2);  // miss

        // 获取统计信息
        String stats = cacheEngine.getStats(resource);
        assertNotNull(stats);
        assertTrue(stats.contains("L1 Cache Stats"));
        assertTrue(stats.contains(resource));
    }

    /**
     * 测试不存在的资源统计
     */
    @Test
    public void testStatsForNonexistentResource() {
        String stats = cacheEngine.getStats("nonexistent");
        assertNotNull(stats);
        assertTrue(stats.contains("缓存未找到"));
    }
}
