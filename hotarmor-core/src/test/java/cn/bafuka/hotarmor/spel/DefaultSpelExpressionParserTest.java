package cn.bafuka.hotarmor.spel;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.expression.spel.SpelEvaluationException;

import java.lang.reflect.Method;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * DefaultSpelExpressionParser 单元测试
 * 主要测试 SpEL 表达式解析的正确性和安全性
 */
public class DefaultSpelExpressionParserTest {

    private DefaultSpelExpressionParser parser;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature methodSignature;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        parser = new DefaultSpelExpressionParser();
    }

    /**
     * 测试简单参数表达式
     */
    @Test
    public void testParseKey_SimpleParameter() throws NoSuchMethodException {
        // 准备测试数据
        Method method = TestService.class.getMethod("getUserById", Long.class);
        Object[] args = {123L};

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(joinPoint.getArgs()).thenReturn(args);

        // 执行测试
        Object result = parser.parseKey("#userId", joinPoint, TestService.class, "getUserById");

        // 验证结果
        assertEquals(123L, result);
    }

    /**
     * 测试参数索引表达式 (p0, p1)
     */
    @Test
    public void testParseKey_ParameterIndex() throws NoSuchMethodException {
        Method method = TestService.class.getMethod("getUserById", Long.class);
        Object[] args = {456L};

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(joinPoint.getArgs()).thenReturn(args);

        // 测试 p0
        Object result = parser.parseKey("#p0", joinPoint, TestService.class, "getUserById");
        assertEquals(456L, result);

        // 测试 a0
        result = parser.parseKey("#a0", joinPoint, TestService.class, "getUserById");
        assertEquals(456L, result);
    }

    /**
     * 测试对象属性访问
     */
    @Test
    public void testParseKey_ObjectProperty() throws NoSuchMethodException {
        Method method = TestService.class.getMethod("updateUser", TestUser.class);
        TestUser user = new TestUser();
        user.setId(789L);
        user.setName("Alice");
        Object[] args = {user};

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(joinPoint.getArgs()).thenReturn(args);

        // 访问对象属性
        Object result = parser.parseKey("#user.id", joinPoint, TestService.class, "updateUser");
        assertEquals(789L, result);

        result = parser.parseKey("#user.name", joinPoint, TestService.class, "updateUser");
        assertEquals("Alice", result);
    }

    /**
     * 测试复杂表达式（字符串拼接）
     */
    @Test
    public void testParseKey_ComplexExpression() throws NoSuchMethodException {
        Method method = TestService.class.getMethod("getUserByIdAndType", Long.class, String.class);
        Object[] args = {100L, "VIP"};

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(joinPoint.getArgs()).thenReturn(args);

        // 字符串拼接
        Object result = parser.parseKey("#userId + ':' + #type", joinPoint, TestService.class, "getUserByIdAndType");
        assertEquals("100:VIP", result);
    }

    /**
     * 测试条件表达式 - true
     */
    @Test
    public void testParseCondition_True() throws NoSuchMethodException {
        Method method = TestService.class.getMethod("getUserById", Long.class);
        Object[] args = {100L};

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(joinPoint.getArgs()).thenReturn(args);

        // 条件为真
        boolean result = parser.parseCondition("#userId > 0", joinPoint, TestService.class, "getUserById");
        assertTrue(result);
    }

    /**
     * 测试条件表达式 - false
     */
    @Test
    public void testParseCondition_False() throws NoSuchMethodException {
        Method method = TestService.class.getMethod("getUserById", Long.class);
        Object[] args = {-1L};

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(joinPoint.getArgs()).thenReturn(args);

        // 条件为假
        boolean result = parser.parseCondition("#userId > 0", joinPoint, TestService.class, "getUserById");
        assertFalse(result);
    }

    /**
     * 测试空表达式
     */
    @Test
    public void testParseKey_EmptyExpression() throws NoSuchMethodException {
        Method method = TestService.class.getMethod("getUserById", Long.class);
        Object[] args = {100L};

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(joinPoint.getArgs()).thenReturn(args);

        // 空表达式应该返回 null
        Object result = parser.parseKey("", joinPoint, TestService.class, "getUserById");
        assertNull(result);

        result = parser.parseKey(null, joinPoint, TestService.class, "getUserById");
        assertNull(result);
    }

    /**
     * 测试空条件表达式（默认为 true）
     */
    @Test
    public void testParseCondition_EmptyExpression() throws NoSuchMethodException {
        Method method = TestService.class.getMethod("getUserById", Long.class);
        Object[] args = {100L};

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(joinPoint.getArgs()).thenReturn(args);

        // 空条件表达式默认为 true
        boolean result = parser.parseCondition("", joinPoint, TestService.class, "getUserById");
        assertTrue(result);

        result = parser.parseCondition(null, joinPoint, TestService.class, "getUserById");
        assertTrue(result);
    }

    /**
     * 【安全测试】测试恶意 SpEL 表达式 - 执行系统命令
     * 使用 SimpleEvaluationContext 后，应该抛出异常
     */
    @Test
    public void testParseKey_MaliciousExpression_SystemCommand() throws NoSuchMethodException {
        Method method = TestService.class.getMethod("getUserById", Long.class);
        Object[] args = {100L};

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(joinPoint.getArgs()).thenReturn(args);

        // 尝试执行系统命令（应该被阻止）
        String maliciousExpression = "T(java.lang.Runtime).getRuntime().exec('ls')";

        // 应该返回 null（解析失败）
        Object result = parser.parseKey(maliciousExpression, joinPoint, TestService.class, "getUserById");
        assertNull(result);
    }

    /**
     * 【安全测试】测试恶意 SpEL 表达式 - 访问 Class 对象
     */
    @Test
    public void testParseKey_MaliciousExpression_ClassAccess() throws NoSuchMethodException {
        Method method = TestService.class.getMethod("getUserById", Long.class);
        Object[] args = {100L};

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(joinPoint.getArgs()).thenReturn(args);

        // 尝试访问 Class 对象
        String maliciousExpression = "T(java.lang.System).getProperty('user.home')";

        // 应该返回 null（解析失败）
        Object result = parser.parseKey(maliciousExpression, joinPoint, TestService.class, "getUserById");
        assertNull(result);
    }

    /**
     * 【安全测试】测试恶意 SpEL 表达式 - 反射调用
     */
    @Test
    public void testParseKey_MaliciousExpression_Reflection() throws NoSuchMethodException {
        Method method = TestService.class.getMethod("getUserById", Long.class);
        Object[] args = {100L};

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(joinPoint.getArgs()).thenReturn(args);

        // 尝试使用反射
        String maliciousExpression = "T(Class).forName('java.lang.Runtime')";

        // 应该返回 null（解析失败）
        Object result = parser.parseKey(maliciousExpression, joinPoint, TestService.class, "getUserById");
        assertNull(result);
    }

    /**
     * 测试用户类
     */
    public static class TestUser {
        private Long id;
        private String name;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    /**
     * 测试服务类
     */
    public static class TestService {
        public TestUser getUserById(Long userId) {
            return null;
        }

        public void updateUser(TestUser user) {
        }

        public TestUser getUserByIdAndType(Long userId, String type) {
            return null;
        }
    }
}
