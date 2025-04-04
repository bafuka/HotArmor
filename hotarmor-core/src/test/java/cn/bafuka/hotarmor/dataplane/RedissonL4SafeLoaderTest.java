package cn.bafuka.hotarmor.dataplane;

import cn.bafuka.hotarmor.core.HotArmorContext;
import cn.bafuka.hotarmor.dataplane.impl.RedissonL4SafeLoader;
import cn.bafuka.hotarmor.model.HotArmorRule;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * RedissonL4SafeLoader 单元测试
 * 测试分布式锁和安全回源逻辑
 */
public class RedissonL4SafeLoaderTest {

    private RedissonL4SafeLoader<String> l4SafeLoader;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private RLock lock;

    @Mock
    private Function<Object, String> dbLoader;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        l4SafeLoader = new RedissonL4SafeLoader<>(redissonClient, redisTemplate);

        // 默认 mock valueOperations
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    /**
     * 测试 Redis 命中场景
     */
    @Test
    public void testLoad_RedisHit() {
        HotArmorContext context = HotArmorContext.builder()
                .resource("test:resource")
                .key("key1")
                .build();

        // 注册配置
        HotArmorRule.L4LoaderConfig config = HotArmorRule.L4LoaderConfig.builder()
                .redisKeyPrefix("hotarmor:")
                .redisTtlSeconds(300)
                .lockWaitTimeMs(3000)
                .lockLeaseTimeMs(5000)
                .build();
        l4SafeLoader.registerConfig("test:resource", config);

        // Mock Redis 命中
        when(valueOperations.get("hotarmor:test:resource:key1")).thenReturn("cachedValue");

        // 执行
        String result = l4SafeLoader.load(context, dbLoader);

        // 验证
        assertEquals("cachedValue", result);
        verify(valueOperations).get("hotarmor:test:resource:key1");
        // Redis 命中，不应该尝试获取锁
        verify(redissonClient, never()).getLock(anyString());
        // 不应该调用 DB
        verify(dbLoader, never()).apply(any());
    }

    /**
     * 测试 Redis 未命中，获取锁成功，从 DB 加载
     */
    @Test
    public void testLoad_RedisMiss_LockSuccess_LoadFromDB() throws InterruptedException {
        HotArmorContext context = HotArmorContext.builder()
                .resource("test:resource")
                .key("key1")
                .build();

        HotArmorRule.L4LoaderConfig config = HotArmorRule.L4LoaderConfig.builder()
                .redisKeyPrefix("hotarmor:")
                .redisTtlSeconds(300)
                .lockWaitTimeMs(3000)
                .lockLeaseTimeMs(5000)
                .build();
        l4SafeLoader.registerConfig("test:resource", config);

        String lockKey = "lock:hotarmor:test:resource:key1";
        when(redissonClient.getLock(lockKey)).thenReturn(lock);

        // Mock Redis 未命中
        when(valueOperations.get("hotarmor:test:resource:key1")).thenReturn(null);

        // Mock 获取锁成功
        when(lock.tryLock(3000, 5000, TimeUnit.MILLISECONDS)).thenReturn(true);

        // Mock DB 加载
        when(dbLoader.apply("key1")).thenReturn("dbValue");

        // 执行
        String result = l4SafeLoader.load(context, dbLoader);

        // 验证
        assertEquals("dbValue", result);
        verify(lock).tryLock(3000, 5000, TimeUnit.MILLISECONDS);
        verify(dbLoader).apply("key1");
        // 应该回写 Redis
        verify(valueOperations).set("hotarmor:test:resource:key1", "dbValue", 300, TimeUnit.SECONDS);
        // 应该释放锁
        verify(lock).unlock();
    }

    /**
     * 测试 Redis 未命中，获取锁成功，Double-Check 命中
     */
    @Test
    public void testLoad_RedisMiss_LockSuccess_DoubleCheckHit() throws InterruptedException {
        HotArmorContext context = HotArmorContext.builder()
                .resource("test:resource")
                .key("key1")
                .build();

        HotArmorRule.L4LoaderConfig config = HotArmorRule.L4LoaderConfig.builder()
                .redisKeyPrefix("hotarmor:")
                .redisTtlSeconds(300)
                .lockWaitTimeMs(3000)
                .lockLeaseTimeMs(5000)
                .build();
        l4SafeLoader.registerConfig("test:resource", config);

        String lockKey = "lock:hotarmor:test:resource:key1";
        when(redissonClient.getLock(lockKey)).thenReturn(lock);

        // Mock Redis 第一次未命中，第二次（Double-Check）命中
        when(valueOperations.get("hotarmor:test:resource:key1"))
                .thenReturn(null)  // 第一次未命中
                .thenReturn("anotherThreadValue");  // Double-Check 命中

        // Mock 获取锁成功
        when(lock.tryLock(3000, 5000, TimeUnit.MILLISECONDS)).thenReturn(true);

        // 执行
        String result = l4SafeLoader.load(context, dbLoader);

        // 验证
        assertEquals("anotherThreadValue", result);
        verify(lock).tryLock(3000, 5000, TimeUnit.MILLISECONDS);
        // Double-Check 命中，不应该调用 DB
        verify(dbLoader, never()).apply(any());
        // 不应该回写 Redis（因为已经有值了）
        verify(valueOperations, never()).set(anyString(), any(), anyLong(), any());
        // 应该释放锁
        verify(lock).unlock();
    }

    /**
     * 测试 Redis 未命中，获取锁失败，重试后成功
     */
    @Test
    public void testLoad_RedisMiss_LockFail_RetrySuccess() throws InterruptedException {
        HotArmorContext context = HotArmorContext.builder()
                .resource("test:resource")
                .key("key1")
                .build();

        HotArmorRule.L4LoaderConfig config = HotArmorRule.L4LoaderConfig.builder()
                .redisKeyPrefix("hotarmor:")
                .redisTtlSeconds(300)
                .lockWaitTimeMs(3000)
                .lockLeaseTimeMs(5000)
                .build();
        l4SafeLoader.registerConfig("test:resource", config);

        String lockKey = "lock:hotarmor:test:resource:key1";
        when(redissonClient.getLock(lockKey)).thenReturn(lock);

        // Mock Redis 第一次未命中，重试后命中
        when(valueOperations.get("hotarmor:test:resource:key1"))
                .thenReturn(null)    // 第一次
                .thenReturn(null)    // 重试第1次
                .thenReturn("retryValue");  // 重试第2次命中

        // Mock 获取锁失败
        when(lock.tryLock(3000, 5000, TimeUnit.MILLISECONDS)).thenReturn(false);

        // 执行
        String result = l4SafeLoader.load(context, dbLoader);

        // 验证
        assertEquals("retryValue", result);
        verify(lock).tryLock(3000, 5000, TimeUnit.MILLISECONDS);
        // 重试期间获取到值，不应该调用 DB
        verify(dbLoader, never()).apply(any());
    }

    /**
     * 测试 Redis 未命中，获取锁失败，重试超时，降级查 DB
     */
    @Test
    public void testLoad_RedisMiss_LockFail_RetryTimeout_FallbackToDB() throws InterruptedException {
        HotArmorContext context = HotArmorContext.builder()
                .resource("test:resource")
                .key("key1")
                .build();

        HotArmorRule.L4LoaderConfig config = HotArmorRule.L4LoaderConfig.builder()
                .redisKeyPrefix("hotarmor:")
                .redisTtlSeconds(300)
                .lockWaitTimeMs(3000)
                .lockLeaseTimeMs(5000)
                .build();
        l4SafeLoader.registerConfig("test:resource", config);

        String lockKey = "lock:hotarmor:test:resource:key1";
        when(redissonClient.getLock(lockKey)).thenReturn(lock);

        // Mock Redis 一直未命中
        when(valueOperations.get("hotarmor:test:resource:key1")).thenReturn(null);

        // Mock 获取锁失败
        when(lock.tryLock(3000, 5000, TimeUnit.MILLISECONDS)).thenReturn(false);

        // Mock DB 加载
        when(dbLoader.apply("key1")).thenReturn("fallbackValue");

        // 执行
        String result = l4SafeLoader.load(context, dbLoader);

        // 验证
        assertEquals("fallbackValue", result);
        // 重试失败，降级查 DB
        verify(dbLoader).apply("key1");
        // 降级数据应该回写 Redis（TTL 60s）
        verify(valueOperations).set("hotarmor:test:resource:key1", "fallbackValue", 60, TimeUnit.SECONDS);
    }

    /**
     * 测试未配置时的行为
     */
    @Test
    public void testLoad_NoConfig() {
        HotArmorContext context = HotArmorContext.builder()
                .resource("unconfigured:resource")
                .key("key1")
                .build();

        // Mock Redis 未命中
        when(valueOperations.get(anyString())).thenReturn(null);

        // Mock DB 加载
        when(dbLoader.apply("key1")).thenReturn("dbValue");

        // 执行（未注册配置）
        String result = l4SafeLoader.load(context, dbLoader);

        // 验证 - 应该直接查 DB
        assertEquals("dbValue", result);
        verify(dbLoader).apply("key1");
    }

    /**
     * 测试 getFromRedis
     */
    @Test
    public void testGetFromRedis() {
        HotArmorContext context = HotArmorContext.builder()
                .resource("test:resource")
                .key("key1")
                .build();

        HotArmorRule.L4LoaderConfig config = HotArmorRule.L4LoaderConfig.builder()
                .redisKeyPrefix("hotarmor:")
                .build();
        l4SafeLoader.registerConfig("test:resource", config);

        when(valueOperations.get("hotarmor:test:resource:key1")).thenReturn("redisValue");

        String result = l4SafeLoader.getFromRedis(context);

        assertEquals("redisValue", result);
        verify(valueOperations).get("hotarmor:test:resource:key1");
    }

    /**
     * 测试 putToRedis
     */
    @Test
    public void testPutToRedis() {
        HotArmorContext context = HotArmorContext.builder()
                .resource("test:resource")
                .key("key1")
                .build();

        HotArmorRule.L4LoaderConfig config = HotArmorRule.L4LoaderConfig.builder()
                .redisKeyPrefix("hotarmor:")
                .redisTtlSeconds(600)
                .build();
        l4SafeLoader.registerConfig("test:resource", config);

        l4SafeLoader.putToRedis(context, "newValue");

        verify(valueOperations).set("hotarmor:test:resource:key1", "newValue", 600, TimeUnit.SECONDS);
    }

    /**
     * 测试 deleteFromRedis
     */
    @Test
    public void testDeleteFromRedis() {
        HotArmorContext context = HotArmorContext.builder()
                .resource("test:resource")
                .key("key1")
                .build();

        HotArmorRule.L4LoaderConfig config = HotArmorRule.L4LoaderConfig.builder()
                .redisKeyPrefix("hotarmor:")
                .build();
        l4SafeLoader.registerConfig("test:resource", config);

        l4SafeLoader.deleteFromRedis(context);

        verify(redisTemplate).delete("hotarmor:test:resource:key1");
    }

    /**
     * 测试 getRedisKey
     */
    @Test
    public void testGetRedisKey() {
        HotArmorContext context = HotArmorContext.builder()
                .resource("test:resource")
                .key("key1")
                .build();

        HotArmorRule.L4LoaderConfig config = HotArmorRule.L4LoaderConfig.builder()
                .redisKeyPrefix("myprefix:")
                .build();
        l4SafeLoader.registerConfig("test:resource", config);

        String redisKey = l4SafeLoader.getRedisKey(context);

        assertEquals("myprefix:test:resource:key1", redisKey);
    }

    /**
     * 测试 null context 处理
     */
    @Test
    public void testLoad_NullContext() {
        String result = l4SafeLoader.load(null, dbLoader);
        assertNull(result);
        verify(dbLoader, never()).apply(any());
    }

    /**
     * 测试 null resource 处理
     */
    @Test
    public void testLoad_NullResource() {
        HotArmorContext context = HotArmorContext.builder()
                .resource(null)
                .key("key1")
                .build();

        String result = l4SafeLoader.load(context, dbLoader);

        assertNull(result);
        verify(dbLoader, never()).apply(any());
    }

    /**
     * 测试 DB 加载返回 null
     */
    @Test
    public void testLoad_DBReturnsNull() throws InterruptedException {
        HotArmorContext context = HotArmorContext.builder()
                .resource("test:resource")
                .key("key1")
                .build();

        HotArmorRule.L4LoaderConfig config = HotArmorRule.L4LoaderConfig.builder()
                .redisKeyPrefix("hotarmor:")
                .redisTtlSeconds(300)
                .lockWaitTimeMs(3000)
                .lockLeaseTimeMs(5000)
                .build();
        l4SafeLoader.registerConfig("test:resource", config);

        String lockKey = "lock:hotarmor:test:resource:key1";
        when(redissonClient.getLock(lockKey)).thenReturn(lock);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(lock.tryLock(3000, 5000, TimeUnit.MILLISECONDS)).thenReturn(true);

        // Mock DB 返回 null
        when(dbLoader.apply("key1")).thenReturn(null);

        String result = l4SafeLoader.load(context, dbLoader);

        // 验证
        assertNull(result);
        verify(dbLoader).apply("key1");
        // null 值不应该回写 Redis
        verify(valueOperations, never()).set(anyString(), any(), anyLong(), any());
        verify(lock).unlock();
    }

    /**
     * 测试 Redis 异常处理
     */
    @Test
    public void testGetFromRedis_RedisException() {
        HotArmorContext context = HotArmorContext.builder()
                .resource("test:resource")
                .key("key1")
                .build();

        HotArmorRule.L4LoaderConfig config = HotArmorRule.L4LoaderConfig.builder()
                .redisKeyPrefix("hotarmor:")
                .build();
        l4SafeLoader.registerConfig("test:resource", config);

        // Mock Redis 抛异常
        when(valueOperations.get(anyString())).thenThrow(new RuntimeException("Redis error"));

        // 执行 - 应该返回 null，不抛异常
        String result = l4SafeLoader.getFromRedis(context);

        assertNull(result);
    }

    /**
     * 测试锁中断异常处理
     */
    @Test
    public void testLoad_InterruptedException() throws InterruptedException {
        HotArmorContext context = HotArmorContext.builder()
                .resource("test:resource")
                .key("key1")
                .build();

        HotArmorRule.L4LoaderConfig config = HotArmorRule.L4LoaderConfig.builder()
                .redisKeyPrefix("hotarmor:")
                .redisTtlSeconds(300)
                .lockWaitTimeMs(3000)
                .lockLeaseTimeMs(5000)
                .build();
        l4SafeLoader.registerConfig("test:resource", config);

        String lockKey = "lock:hotarmor:test:resource:key1";
        when(redissonClient.getLock(lockKey)).thenReturn(lock);
        when(valueOperations.get(anyString())).thenReturn(null);

        // Mock 锁被中断
        when(lock.tryLock(3000, 5000, TimeUnit.MILLISECONDS))
                .thenThrow(new InterruptedException("Interrupted"));

        // Mock DB 加载
        when(dbLoader.apply("key1")).thenReturn("fallbackValue");

        // 执行
        String result = l4SafeLoader.load(context, dbLoader);

        // 验证 - 应该降级查 DB
        assertEquals("fallbackValue", result);
        verify(dbLoader).apply("key1");
    }
}
