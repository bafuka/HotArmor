package cn.bafuka.hotarmor.dataplane;

import cn.bafuka.hotarmor.core.HotArmorContext;
import cn.bafuka.hotarmor.dataplane.impl.CaffeineL2NoiseFilter;
import cn.bafuka.hotarmor.model.HotArmorRule;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * CaffeineL2NoiseFilter 单元测试
 */
public class CaffeineL2NoiseFilterTest {

    private CaffeineL2NoiseFilter noiseFilter;

    @Before
    public void setUp() {
        noiseFilter = new CaffeineL2NoiseFilter();
    }

    /**
     * 测试基本的过滤逻辑
     */
    @Test
    public void testBasicFiltering() {
        // 配置阈值为 3
        HotArmorRule.L2FilterConfig config = HotArmorRule.L2FilterConfig.builder()
                .windowSeconds(10)
                .threshold(3)
                .enabled(true)
                .build();

        String resource = "test:resource";
        noiseFilter.getOrCreateCounter(resource, config);

        HotArmorContext context = HotArmorContext.builder()
                .resource(resource)
                .key("key1")
                .build();

        // 前两次访问应该被过滤
        assertFalse("First access should be filtered", noiseFilter.shouldPass(context));
        assertFalse("Second access should be filtered", noiseFilter.shouldPass(context));

        // 第三次访问应该通过
        assertTrue("Third access should pass", noiseFilter.shouldPass(context));

        // 后续访问也应该通过
        assertTrue("Fourth access should pass", noiseFilter.shouldPass(context));
    }

    /**
     * 测试不同 key 的独立计数
     */
    @Test
    public void testIndependentCounting() {
        HotArmorRule.L2FilterConfig config = HotArmorRule.L2FilterConfig.builder()
                .windowSeconds(10)
                .threshold(2)
                .enabled(true)
                .build();

        String resource = "test:resource";
        noiseFilter.getOrCreateCounter(resource, config);

        HotArmorContext context1 = HotArmorContext.builder()
                .resource(resource)
                .key("key1")
                .build();

        HotArmorContext context2 = HotArmorContext.builder()
                .resource(resource)
                .key("key2")
                .build();

        // key1 的第一次访问
        assertFalse(noiseFilter.shouldPass(context1));
        // key2 的第一次访问（应该独立计数）
        assertFalse(noiseFilter.shouldPass(context2));

        // key1 的第二次访问
        assertTrue(noiseFilter.shouldPass(context1));
        // key2 的第二次访问
        assertTrue(noiseFilter.shouldPass(context2));
    }

    /**
     * 测试阈值为 1 的情况（所有请求都通过）
     */
    @Test
    public void testThresholdOne() {
        HotArmorRule.L2FilterConfig config = HotArmorRule.L2FilterConfig.builder()
                .windowSeconds(10)
                .threshold(1)
                .enabled(true)
                .build();

        String resource = "test:resource";
        noiseFilter.getOrCreateCounter(resource, config);

        HotArmorContext context = HotArmorContext.builder()
                .resource(resource)
                .key("key1")
                .build();

        // 第一次访问就应该通过
        assertTrue("Should pass on first access when threshold is 1", noiseFilter.shouldPass(context));
    }

    /**
     * 测试未启用时的行为
     */
    @Test
    public void testDisabledFilter() {
        HotArmorRule.L2FilterConfig config = HotArmorRule.L2FilterConfig.builder()
                .windowSeconds(10)
                .threshold(5)
                .enabled(false)  // 未启用
                .build();

        String resource = "test:resource";
        noiseFilter.getOrCreateCounter(resource, config);

        HotArmorContext context = HotArmorContext.builder()
                .resource(resource)
                .key("key1")
                .build();

        // 未启用时应该直接通过
        assertTrue("Should pass when filter is disabled", noiseFilter.shouldPass(context));
    }

    /**
     * 测试未配置资源时的行为
     */
    @Test
    public void testUnconfiguredResource() {
        HotArmorContext context = HotArmorContext.builder()
                .resource("unconfigured:resource")
                .key("key1")
                .build();

        // 未配置的资源应该直接通过
        assertTrue("Should pass for unconfigured resource", noiseFilter.shouldPass(context));
    }

    /**
     * 测试时间窗口过期
     */
    @Test
    public void testTimeWindowExpiration() throws InterruptedException {
        // 配置 1 秒时间窗口
        HotArmorRule.L2FilterConfig config = HotArmorRule.L2FilterConfig.builder()
                .windowSeconds(1)
                .threshold(3)
                .enabled(true)
                .build();

        String resource = "test:resource";
        noiseFilter.getOrCreateCounter(resource, config);

        HotArmorContext context = HotArmorContext.builder()
                .resource(resource)
                .key("key1")
                .build();

        // 第一次访问
        assertFalse(noiseFilter.shouldPass(context));
        assertEquals(1, noiseFilter.getCount(context));

        // 等待时间窗口过期
        Thread.sleep(1500);

        // 计数应该被重置
        long count = noiseFilter.getCount(context);
        assertEquals("Count should reset after window expiration", 0, count);
    }

    /**
     * 测试重置功能
     */
    @Test
    public void testReset() {
        HotArmorRule.L2FilterConfig config = HotArmorRule.L2FilterConfig.builder()
                .windowSeconds(10)
                .threshold(3)
                .enabled(true)
                .build();

        String resource = "test:resource";
        noiseFilter.getOrCreateCounter(resource, config);

        HotArmorContext context = HotArmorContext.builder()
                .resource(resource)
                .key("key1")
                .build();

        // 访问几次
        noiseFilter.shouldPass(context);
        noiseFilter.shouldPass(context);
        assertEquals(2, noiseFilter.getCount(context));

        // 重置
        noiseFilter.reset(resource);

        // 计数应该被清零
        assertEquals(0, noiseFilter.getCount(context));
    }

    /**
     * 测试获取计数
     */
    @Test
    public void testGetCount() {
        HotArmorRule.L2FilterConfig config = HotArmorRule.L2FilterConfig.builder()
                .windowSeconds(10)
                .threshold(3)
                .enabled(true)
                .build();

        String resource = "test:resource";
        noiseFilter.getOrCreateCounter(resource, config);

        HotArmorContext context = HotArmorContext.builder()
                .resource(resource)
                .key("key1")
                .build();

        // 初始计数应该为 0
        assertEquals(0, noiseFilter.getCount(context));

        // 访问后计数递增
        noiseFilter.shouldPass(context);
        assertEquals(1, noiseFilter.getCount(context));

        noiseFilter.shouldPass(context);
        assertEquals(2, noiseFilter.getCount(context));
    }

    /**
     * 测试计数器重建
     */
    @Test
    public void testRebuildCounter() {
        HotArmorRule.L2FilterConfig config1 = HotArmorRule.L2FilterConfig.builder()
                .windowSeconds(10)
                .threshold(3)
                .enabled(true)
                .build();

        String resource = "test:resource";
        noiseFilter.getOrCreateCounter(resource, config1);

        HotArmorContext context = HotArmorContext.builder()
                .resource(resource)
                .key("key1")
                .build();

        // 访问几次
        noiseFilter.shouldPass(context);
        noiseFilter.shouldPass(context);
        assertEquals(2, noiseFilter.getCount(context));

        // 重建计数器（使用新配置）
        HotArmorRule.L2FilterConfig config2 = HotArmorRule.L2FilterConfig.builder()
                .windowSeconds(5)
                .threshold(2)
                .enabled(true)
                .build();

        noiseFilter.rebuildCounter(resource, config2);

        // 计数应该被重置
        assertEquals(0, noiseFilter.getCount(context));

        // 使用新配置
        assertFalse(noiseFilter.shouldPass(context)); // 1
        assertTrue(noiseFilter.shouldPass(context));  // 2 - 应该通过（新阈值为 2）
    }

    /**
     * 测试 null context 处理
     */
    @Test
    public void testNullContext() {
        HotArmorRule.L2FilterConfig config = HotArmorRule.L2FilterConfig.builder()
                .windowSeconds(10)
                .threshold(3)
                .enabled(true)
                .build();

        String resource = "test:resource";
        noiseFilter.getOrCreateCounter(resource, config);

        // null context 应该返回 false
        assertFalse(noiseFilter.shouldPass(null));
        assertEquals(0, noiseFilter.getCount(null));
    }

    /**
     * 测试 null resource 处理
     */
    @Test
    public void testNullResource() {
        HotArmorContext context = HotArmorContext.builder()
                .resource(null)
                .key("key1")
                .build();

        // null resource 应该返回 false
        assertFalse(noiseFilter.shouldPass(context));
        assertEquals(0, noiseFilter.getCount(context));
    }

    /**
     * 测试多资源隔离
     */
    @Test
    public void testMultipleResources() {
        HotArmorRule.L2FilterConfig config = HotArmorRule.L2FilterConfig.builder()
                .windowSeconds(10)
                .threshold(2)
                .enabled(true)
                .build();

        String resource1 = "test:resource1";
        String resource2 = "test:resource2";
        noiseFilter.getOrCreateCounter(resource1, config);
        noiseFilter.getOrCreateCounter(resource2, config);

        // 在不同资源中使用相同的 key
        HotArmorContext context1 = HotArmorContext.builder()
                .resource(resource1)
                .key("sameKey")
                .build();
        HotArmorContext context2 = HotArmorContext.builder()
                .resource(resource2)
                .key("sameKey")
                .build();

        // 访问资源1
        assertFalse(noiseFilter.shouldPass(context1));
        assertEquals(1, noiseFilter.getCount(context1));

        // 访问资源2（应该独立计数）
        assertFalse(noiseFilter.shouldPass(context2));
        assertEquals(1, noiseFilter.getCount(context2));

        // 验证隔离性
        assertEquals(1, noiseFilter.getCount(context1));
        assertEquals(1, noiseFilter.getCount(context2));
    }

    /**
     * 测试高并发场景（原子性）
     */
    @Test
    public void testConcurrentAccess() throws InterruptedException {
        HotArmorRule.L2FilterConfig config = HotArmorRule.L2FilterConfig.builder()
                .windowSeconds(10)
                .threshold(100)
                .enabled(true)
                .build();

        String resource = "test:resource";
        noiseFilter.getOrCreateCounter(resource, config);

        HotArmorContext context = HotArmorContext.builder()
                .resource(resource)
                .key("key1")
                .build();

        // 并发访问
        int threadCount = 10;
        int accessPerThread = 10;
        Thread[] threads = new Thread[threadCount];

        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < accessPerThread; j++) {
                    noiseFilter.shouldPass(context);
                }
            });
            threads[i].start();
        }

        // 等待所有线程完成
        for (Thread thread : threads) {
            thread.join();
        }

        // 验证计数准确性（原子操作）
        long finalCount = noiseFilter.getCount(context);
        assertEquals("Count should be exactly " + (threadCount * accessPerThread),
                threadCount * accessPerThread, finalCount);
    }
}
