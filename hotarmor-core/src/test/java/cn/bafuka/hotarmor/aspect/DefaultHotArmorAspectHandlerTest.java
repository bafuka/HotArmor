package cn.bafuka.hotarmor.aspect;

import cn.bafuka.hotarmor.aspect.impl.DefaultHotArmorAspectHandler;
import cn.bafuka.hotarmor.consistency.ConsistencyManager;
import cn.bafuka.hotarmor.core.HotArmorContext;
import cn.bafuka.hotarmor.dataplane.L1CacheEngine;
import cn.bafuka.hotarmor.dataplane.L2NoiseFilter;
import cn.bafuka.hotarmor.dataplane.L3HotspotDetector;
import cn.bafuka.hotarmor.dataplane.L4SafeLoader;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.function.Function;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * DefaultHotArmorAspectHandler 单元测试
 * 测试核心的四级漏斗业务逻辑
 */
public class DefaultHotArmorAspectHandlerTest {

    private DefaultHotArmorAspectHandler aspectHandler;

    @Mock
    private L1CacheEngine<Object> l1CacheEngine;

    @Mock
    private L2NoiseFilter l2NoiseFilter;

    @Mock
    private L3HotspotDetector l3HotspotDetector;

    @Mock
    private L4SafeLoader<Object> l4SafeLoader;

    @Mock
    private ConsistencyManager consistencyManager;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        aspectHandler = new DefaultHotArmorAspectHandler(
                l1CacheEngine,
                l2NoiseFilter,
                l3HotspotDetector,
                l4SafeLoader,
                consistencyManager
        );
    }

    /**
     * 测试 L1 缓存命中场景
     */
    @Test
    public void testHandleCache_L1Hit() throws Throwable {
        HotArmorContext context = HotArmorContext.builder()
                .resource("test:resource")
                .key("key1")
                .build();

        // Mock L1 缓存命中
        when(l1CacheEngine.get(context)).thenReturn("cachedValue");

        // 执行
        Object result = aspectHandler.handleCache(joinPoint, context);

        // 验证
        assertEquals("cachedValue", result);
        verify(l1CacheEngine).get(context);
        // L1 命中后不应该调用后续层级
        verify(l2NoiseFilter, never()).shouldPass(any());
        verify(l3HotspotDetector, never()).isHotspot(any());
        verify(l4SafeLoader, never()).load(any(), any());
    }

    /**
     * 测试 L1 未命中 -> L2 过滤（冷数据）
     */
    @Test
    public void testHandleCache_L2Filter() throws Throwable {
        HotArmorContext context = HotArmorContext.builder()
                .resource("test:resource")
                .key("key1")
                .build();

        // Mock L1 未命中
        when(l1CacheEngine.get(context)).thenReturn(null);

        // Mock L2 过滤（返回 false，表示冷数据）
        when(l2NoiseFilter.shouldPass(context)).thenReturn(false);

        // Mock L4 加载
        when(joinPoint.proceed()).thenReturn("dbValue");
        when(l4SafeLoader.load(eq(context), any())).thenAnswer(invocation -> {
            Function<Object, Object> loader = invocation.getArgument(1);
            return loader.apply(context.getKey());
        });

        // 执行
        Object result = aspectHandler.handleCache(joinPoint, context);

        // 验证
        assertEquals("dbValue", result);
        verify(l1CacheEngine).get(context);
        verify(l2NoiseFilter).shouldPass(context);
        verify(l4SafeLoader).load(eq(context), any());
        // L2 过滤后直接回源，不经过 L3
        verify(l3HotspotDetector, never()).isHotspot(any());
        // 冷数据不应该晋升到 L1
        verify(l1CacheEngine, never()).put(any(), any());
    }

    /**
     * 测试 L1 未命中 -> L2 通过 -> L3 热点探测（非热点）
     */
    @Test
    public void testHandleCache_L3NotHotspot() throws Throwable {
        HotArmorContext context = HotArmorContext.builder()
                .resource("test:resource")
                .key("key1")
                .build();

        // Mock L1 未命中
        when(l1CacheEngine.get(context)).thenReturn(null);

        // Mock L2 通过
        when(l2NoiseFilter.shouldPass(context)).thenReturn(true);

        // Mock L3 非热点
        when(l3HotspotDetector.isHotspot(context)).thenReturn(false);

        // Mock L4 加载
        when(joinPoint.proceed()).thenReturn("dbValue");
        when(l4SafeLoader.load(eq(context), any())).thenAnswer(invocation -> {
            Function<Object, Object> loader = invocation.getArgument(1);
            return loader.apply(context.getKey());
        });

        // 执行
        Object result = aspectHandler.handleCache(joinPoint, context);

        // 验证
        assertEquals("dbValue", result);
        verify(l1CacheEngine).get(context);
        verify(l2NoiseFilter).shouldPass(context);
        verify(l3HotspotDetector).isHotspot(context);
        verify(l4SafeLoader).load(eq(context), any());
        // 非热点不应该晋升到 L1
        verify(l1CacheEngine, never()).put(any(), any());
    }

    /**
     * 测试 L1 未命中 -> L2 通过 -> L3 热点探测（热点！）-> 晋升到 L1
     */
    @Test
    public void testHandleCache_L3Hotspot_PromoteToL1() throws Throwable {
        HotArmorContext context = HotArmorContext.builder()
                .resource("test:resource")
                .key("hotKey")
                .build();

        // Mock L1 未命中
        when(l1CacheEngine.get(context)).thenReturn(null);

        // Mock L2 通过
        when(l2NoiseFilter.shouldPass(context)).thenReturn(true);

        // Mock L3 热点
        when(l3HotspotDetector.isHotspot(context)).thenReturn(true);

        // Mock L4 加载
        when(joinPoint.proceed()).thenReturn("hotValue");
        when(l4SafeLoader.load(eq(context), any())).thenAnswer(invocation -> {
            Function<Object, Object> loader = invocation.getArgument(1);
            return loader.apply(context.getKey());
        });

        // 执行
        Object result = aspectHandler.handleCache(joinPoint, context);

        // 验证
        assertEquals("hotValue", result);
        verify(l1CacheEngine).get(context);
        verify(l2NoiseFilter).shouldPass(context);
        verify(l3HotspotDetector).isHotspot(context);
        verify(l4SafeLoader).load(eq(context), any());
        // 热点应该晋升到 L1
        verify(l1CacheEngine).put(context, "hotValue");
    }

    /**
     * 测试 L4 返回 null 时不晋升
     */
    @Test
    public void testHandleCache_L4ReturnsNull_NoPromotion() throws Throwable {
        HotArmorContext context = HotArmorContext.builder()
                .resource("test:resource")
                .key("hotKey")
                .build();

        when(l1CacheEngine.get(context)).thenReturn(null);
        when(l2NoiseFilter.shouldPass(context)).thenReturn(true);
        when(l3HotspotDetector.isHotspot(context)).thenReturn(true);

        // Mock L4 返回 null
        when(joinPoint.proceed()).thenReturn(null);
        when(l4SafeLoader.load(eq(context), any())).thenAnswer(invocation -> {
            Function<Object, Object> loader = invocation.getArgument(1);
            return loader.apply(context.getKey());
        });

        // 执行
        Object result = aspectHandler.handleCache(joinPoint, context);

        // 验证
        assertNull(result);
        // null 值不应该晋升到 L1
        verify(l1CacheEngine, never()).put(any(), any());
    }

    /**
     * 测试 null context
     */
    @Test
    public void testHandleCache_NullContext() throws Throwable {
        when(joinPoint.proceed()).thenReturn("directValue");

        // 执行
        Object result = aspectHandler.handleCache(joinPoint, null);

        // 验证 - 应该直接执行原方法
        assertEquals("directValue", result);
        verify(joinPoint).proceed();
        // 不应该调用任何缓存层
        verify(l1CacheEngine, never()).get(any());
        verify(l2NoiseFilter, never()).shouldPass(any());
    }

    /**
     * 测试 null resource
     */
    @Test
    public void testHandleCache_NullResource() throws Throwable {
        HotArmorContext context = HotArmorContext.builder()
                .resource(null)
                .key("key1")
                .build();

        when(joinPoint.proceed()).thenReturn("directValue");

        // 执行
        Object result = aspectHandler.handleCache(joinPoint, context);

        // 验证 - 应该直接执行原方法
        assertEquals("directValue", result);
        verify(joinPoint).proceed();
        verify(l1CacheEngine, never()).get(any());
    }

    /**
     * 测试缓存失效 - beforeInvocation = true
     */
    @Test
    public void testHandleEvict_BeforeInvocation() throws Throwable {
        HotArmorContext context = HotArmorContext.builder()
                .resource("test:resource")
                .key("key1")
                .build();

        when(joinPoint.proceed()).thenReturn("result");

        // 执行 - beforeInvocation = true
        Object result = aspectHandler.handleEvict(joinPoint, context, true, true, true);

        // 验证
        assertEquals("result", result);
        // 应该在方法执行前删除缓存
        verify(consistencyManager).handleUpdate(context);
        verify(joinPoint).proceed();
    }

    /**
     * 测试缓存失效 - beforeInvocation = false
     */
    @Test
    public void testHandleEvict_AfterInvocation() throws Throwable {
        HotArmorContext context = HotArmorContext.builder()
                .resource("test:resource")
                .key("key1")
                .build();

        when(joinPoint.proceed()).thenReturn("result");

        // 执行 - beforeInvocation = false
        Object result = aspectHandler.handleEvict(joinPoint, context, false, true, true);

        // 验证
        assertEquals("result", result);
        // 应该在方法执行后删除缓存
        verify(joinPoint).proceed();
        verify(consistencyManager).handleUpdate(context);
    }

    /**
     * 测试缓存失效 - 不使用延迟双删和广播
     */
    @Test
    public void testHandleEvict_NoDelayedDeleteNoBroadcast() throws Throwable {
        HotArmorContext context = HotArmorContext.builder()
                .resource("test:resource")
                .key("key1")
                .build();

        when(joinPoint.proceed()).thenReturn("result");

        // 执行 - delayedDelete = false, broadcast = false
        Object result = aspectHandler.handleEvict(joinPoint, context, true, false, false);

        // 验证
        assertEquals("result", result);
        // 应该调用 invalidateCache 而不是 handleUpdate
        verify(consistencyManager).invalidateCache(context);
        verify(consistencyManager, never()).handleUpdate(any());
    }

    /**
     * 测试缓存失效 - null context
     */
    @Test
    public void testHandleEvict_NullContext() throws Throwable {
        when(joinPoint.proceed()).thenReturn("result");

        // 执行
        Object result = aspectHandler.handleEvict(joinPoint, null, true, true, true);

        // 验证 - 应该直接执行原方法
        assertEquals("result", result);
        verify(joinPoint).proceed();
        verify(consistencyManager, never()).handleUpdate(any());
        verify(consistencyManager, never()).invalidateCache(any());
    }

    /**
     * 测试缓存失效 - 方法执行异常
     */
    @Test(expected = RuntimeException.class)
    public void testHandleEvict_MethodThrowsException() throws Throwable {
        HotArmorContext context = HotArmorContext.builder()
                .resource("test:resource")
                .key("key1")
                .build();

        when(joinPoint.proceed()).thenThrow(new RuntimeException("DB error"));

        // 执行 - beforeInvocation = true
        try {
            aspectHandler.handleEvict(joinPoint, context, true, true, true);
        } catch (RuntimeException e) {
            // 验证异常被抛出，且缓存已删除
            verify(consistencyManager).handleUpdate(context);
            throw e;
        }
    }

    /**
     * 测试缓存失效 - 方法执行后异常（beforeInvocation = false）
     */
    @Test(expected = RuntimeException.class)
    public void testHandleEvict_AfterInvocation_MethodThrowsException() throws Throwable {
        HotArmorContext context = HotArmorContext.builder()
                .resource("test:resource")
                .key("key1")
                .build();

        when(joinPoint.proceed()).thenThrow(new RuntimeException("DB error"));

        // 执行 - beforeInvocation = false
        try {
            aspectHandler.handleEvict(joinPoint, context, false, true, true);
        } catch (RuntimeException e) {
            // 验证异常被抛出，且缓存未删除（因为方法执行失败）
            verify(joinPoint).proceed();
            verify(consistencyManager, never()).handleUpdate(any());
            throw e;
        }
    }
}
