#!/bin/bash

# HotArmor 性能压测脚本
# 使用 Apache Bench (ab) 进行压力测试

BASE_URL="http://localhost:8080"
PRODUCT_ID=1

# 颜色
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
RED='\033[0;31m'
NC='\033[0m'

echo "=========================================="
echo "  HotArmor 性能压测"
echo "=========================================="
echo ""

# 检查 ab 是否安装
if ! command -v ab &> /dev/null; then
    echo -e "${RED}❌ Apache Bench (ab) 未安装${NC}"
    echo ""
    echo "安装方法:"
    echo "  macOS: brew install httpd"
    echo "  Ubuntu: sudo apt-get install apache2-utils"
    echo "  CentOS: sudo yum install httpd-tools"
    echo ""
    exit 1
fi

# 检查 redis-cli 是否安装
if ! command -v redis-cli &> /dev/null; then
    echo -e "${RED}❌ redis-cli 未安装${NC}"
    echo ""
    echo "安装方法:"
    echo "  macOS: brew install redis"
    echo "  Ubuntu: sudo apt-get install redis-tools"
    echo "  CentOS: sudo yum install redis"
    echo ""
    exit 1
fi

# 检查并设置文件描述符限制（macOS）
echo -e "${BLUE}检查系统配置...${NC}"
current_limit=$(ulimit -n)
echo "当前文件描述符限制: $current_limit"
if [ "$current_limit" != "unlimited" ] && [ "$current_limit" -lt 10000 ]; then
    echo -e "${YELLOW}⚠️  文件描述符限制较低，尝试增加...${NC}"
    ulimit -n 10000 2>/dev/null || echo -e "${YELLOW}⚠️  无法自动调整，请手动运行: ulimit -n 10000${NC}"
    echo "新的限制: $(ulimit -n)"
fi
echo ""

# 检查应用是否启动
echo -e "${BLUE}检查应用是否启动...${NC}"
if ! curl -s -f "$BASE_URL/actuator/health" > /dev/null 2>&1; then
    echo -e "${RED}❌ 应用未启动或无法访问: $BASE_URL${NC}"
    echo ""
    echo "请先启动应用:"
    echo "  cd hotarmor-example"
    echo "  mvn spring-boot:run"
    echo ""
    exit 1
fi
echo -e "${GREEN}✅ 应用运行正常${NC}"
echo ""

# 检查 Redis 是否可用
echo -e "${BLUE}检查 Redis 是否可用...${NC}"
if ! redis-cli PING > /dev/null 2>&1; then
    echo -e "${RED}❌ Redis 未启动或无法连接${NC}"
    echo ""
    echo "请先启动 Redis:"
    echo "  redis-server"
    echo ""
    exit 1
fi
echo -e "${GREEN}✅ Redis 运行正常${NC}"
echo ""

# 清空 Redis
echo -e "${BLUE}清空 Redis 缓存...${NC}"
if redis-cli FLUSHDB > /dev/null 2>&1; then
    echo -e "${GREEN}✅ Redis 缓存已清空${NC}"
else
    echo -e "${RED}❌ 清空 Redis 失败${NC}"
    exit 1
fi
echo ""

# 测试 1: 预热（触发热点晋升）
echo -e "${YELLOW}=========================================="
echo "测试 1: 预热（触发热点晋升）"
echo "==========================================${NC}"
echo "参数: 1000 个请求, 20 个并发, keep-alive"
echo "预计耗时: ~2-5 秒"
echo ""
if ab -n 1000 -c 20 -k "$BASE_URL/api/products/$PRODUCT_ID"; then
    echo -e "${GREEN}✅ 测试 1 完成${NC}"
else
    echo -e "${RED}❌ 测试 1 失败${NC}"
    exit 1
fi
echo ""
sleep 3

# 测试 2: 冷启动性能（未命中 L1）
echo -e "${YELLOW}=========================================="
echo "测试 2: 冷启动性能（L1 未命中）"
echo "==========================================${NC}"
echo -e "${BLUE}清空 Redis 缓存...${NC}"
redis-cli FLUSHDB > /dev/null 2>&1
echo "参数: 5000 个请求, 50 个并发, keep-alive"
echo "预计耗时: ~5-10 秒"
echo ""
if ab -n 5000 -c 50 -k "$BASE_URL/api/products/$PRODUCT_ID"; then
    echo -e "${GREEN}✅ 测试 2 完成${NC}"
else
    echo -e "${RED}❌ 测试 2 失败${NC}"
    exit 1
fi
echo ""
sleep 3

# 测试 3: 中等负载热数据性能（L1 已命中）
echo -e "${YELLOW}=========================================="
echo "测试 3: 中等负载热数据性能（L1 已命中）"
echo "==========================================${NC}"
echo "参数: 20000 个请求, 100 个并发, keep-alive"
echo "预计耗时: ~10-15 秒"
echo ""
if ab -n 20000 -c 100 -k "$BASE_URL/api/products/$PRODUCT_ID"; then
    echo -e "${GREEN}✅ 测试 3 完成${NC}"
else
    echo -e "${RED}❌ 测试 3 失败${NC}"
    exit 1
fi
echo ""
sleep 3

# 测试 4: 高负载性能测试
echo -e "${YELLOW}=========================================="
echo "测试 4: 高负载性能测试"
echo "==========================================${NC}"
echo "参数: 50000 个请求, 200 个并发, keep-alive"
echo "预计耗时: ~15-30 秒"
echo ""
if ab -n 50000 -c 200 -k "$BASE_URL/api/products/$PRODUCT_ID"; then
    echo -e "${GREEN}✅ 测试 4 完成${NC}"
else
    echo -e "${RED}❌ 测试 4 失败${NC}"
    exit 1
fi
echo ""
sleep 3

# 测试 5: 极限并发压力测试
echo -e "${YELLOW}=========================================="
echo "测试 5: 极限并发压力测试"
echo "==========================================${NC}"
echo "参数: 100000 个请求, 300 个并发, keep-alive"
echo "预计耗时: ~30-60 秒"
echo ""
if ab -n 100000 -c 300 -k "$BASE_URL/api/products/$PRODUCT_ID"; then
    echo -e "${GREEN}✅ 测试 5 完成${NC}"
else
    echo -e "${RED}❌ 测试 5 失败${NC}"
    exit 1
fi
echo ""
sleep 3

# 测试 6: 持续时间压测（60秒）
echo -e "${YELLOW}=========================================="
echo "测试 6: 持续时间压测（60秒）"
echo "==========================================${NC}"
echo "参数: 持续 60 秒, 200 个并发, keep-alive"
echo "预计耗时: ~60 秒"
echo ""
if ab -t 60 -c 200 -k "$BASE_URL/api/products/$PRODUCT_ID"; then
    echo -e "${GREEN}✅ 测试 6 完成${NC}"
else
    echo -e "${RED}❌ 测试 6 失败${NC}"
    exit 1
fi
echo ""

echo "=========================================="
echo -e "${GREEN}压测完成！${NC}"
echo "=========================================="
echo ""
echo "📊 测试总结:"
echo "  测试 1: 预热                - 1,000 请求 / 20 并发"
echo "  测试 2: 冷启动              - 5,000 请求 / 50 并发"
echo "  测试 3: 中等负载            - 20,000 请求 / 100 并发"
echo "  测试 4: 高负载              - 50,000 请求 / 200 并发"
echo "  测试 5: 极限压力            - 100,000 请求 / 300 并发"
echo "  测试 6: 持续压测            - 60 秒 / 200 并发"
echo ""
echo "⏱️  总耗时: 约 2-3 分钟"
echo ""
echo "📈 关键指标说明:"
echo "  - Requests per second: 每秒处理请求数（越高越好）"
echo "  - Time per request: 每个请求耗时（越低越好）"
echo "  - 50% (median): 50% 的请求响应时间"
echo "  - 95%: 95% 的请求响应时间"
echo "  - 99%: 99% 的请求响应时间（尾延迟）"
echo ""
echo "🎯 HotArmor 性能基准:"
echo "  - 冷启动（Redis+DB）: 1,000-5,000 req/s"
echo "  - 热数据（L1 缓存）:  20,000-80,000 req/s"
echo "  - 极限并发:           30,000+ req/s"
echo ""
echo "💡 查看详细数据:"
echo "  - 查看 Redis 缓存: redis-cli KEYS 'hotarmor:*'"
echo "  - 查看应用日志: tail -f hotarmor-example/logs/hotarmor.log"
echo "  - 查看健康状态: curl http://localhost:8080/actuator/health"
echo ""
echo "🚀 如需更长时间的持续压测:"
echo "  ./scripts/test-long-duration.sh [秒数] [并发数]"
echo "  示例: ./scripts/test-long-duration.sh 300 200  # 5分钟, 200并发"
echo ""
