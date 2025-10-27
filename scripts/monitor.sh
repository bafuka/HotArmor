#!/bin/bash

# HotArmor 实时监控脚本
# 实时显示缓存命中率和性能指标

BASE_URL="http://localhost:8080"
PRODUCT_ID=1

# 颜色
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

# 统计变量
total_requests=0
l1_hits=0
redis_hits=0
db_queries=0
start_time=$(date +%s)

echo "=========================================="
echo "  HotArmor 实时监控"
echo "=========================================="
echo ""

# 检查服务是否运行
echo "检查服务状态..."
if ! curl -s "$BASE_URL/api/products/$PRODUCT_ID" > /dev/null 2>&1; then
    echo -e "${RED}错误: 服务未运行或无法访问${NC}"
    echo "请先启动应用: cd hotarmor-example && mvn spring-boot:run"
    exit 1
fi
echo -e "${GREEN}✓ 服务正常运行${NC}"
echo ""

# 检查 Redis
echo "检查 Redis 状态..."
if ! redis-cli ping > /dev/null 2>&1; then
    echo -e "${RED}错误: Redis 未运行${NC}"
    echo "请先启动 Redis: redis-server 或 docker run -d -p 6379:6379 redis"
    exit 1
fi
echo -e "${GREEN}✓ Redis 正常运行${NC}"
echo ""

# 清空 Redis
echo "清空 Redis 缓存..."
redis-cli FLUSHDB > /dev/null 2>&1
echo -e "${GREEN}✓ Redis 已清空${NC}"
echo ""

echo "开始监控..."
echo "按 Ctrl+C 停止"
echo ""
sleep 2

# 监控函数
monitor() {
    while true; do
        # 发送请求并测量时间
        start=$(python3 -c 'import time; print(int(time.time() * 1000))' 2>/dev/null || gdate +%s%3N 2>/dev/null || echo "0")
        response=$(curl -s "$BASE_URL/api/products/$PRODUCT_ID" 2>&1)
        end=$(python3 -c 'import time; print(int(time.time() * 1000))' 2>/dev/null || gdate +%s%3N 2>/dev/null || echo "0")

        # 计算响应时间
        if [ "$start" != "0" ] && [ "$end" != "0" ]; then
            duration=$(( end - start ))
        else
            duration=0
        fi

        total_requests=$((total_requests + 1))

        # 根据响应时间推断缓存状态
        # L1 命中：极快 (< 50ms)
        # Redis 命中：较快 (50-100ms)
        # DB 查询：较慢 (> 100ms)
        if [ "$duration" -gt 0 ]; then
            if [ $duration -lt 50 ]; then
                l1_hits=$((l1_hits + 1))
                status="${GREEN}● L1 缓存命中${NC}"
                status_icon="🚀"
            elif [ $duration -lt 100 ]; then
                redis_hits=$((redis_hits + 1))
                status="${CYAN}● Redis 命中${NC}"
                status_icon="⚡"
            else
                db_queries=$((db_queries + 1))
                status="${RED}● 数据库查询${NC}"
                status_icon="🐢"
            fi
        else
            status="${YELLOW}● 未知${NC}"
            status_icon="?"
        fi

        # 计算命中率
        if [ $total_requests -gt 0 ]; then
            l1_hit_rate=$(awk "BEGIN {printf \"%.1f\", ($l1_hits/$total_requests)*100}")
            redis_hit_rate=$(awk "BEGIN {printf \"%.1f\", ($redis_hits/$total_requests)*100}")
            db_rate=$(awk "BEGIN {printf \"%.1f\", ($db_queries/$total_requests)*100}")
        else
            l1_hit_rate="0.0"
            redis_hit_rate="0.0"
            db_rate="0.0"
        fi

        # 计算运行时间
        current_time=$(date +%s)
        elapsed=$((current_time - start_time))
        elapsed_min=$((elapsed / 60))
        elapsed_sec=$((elapsed % 60))

        # 清屏并显示统计
        clear
        echo "╔════════════════════════════════════════╗"
        echo "║       HotArmor 实时监控面板           ║"
        echo "╚════════════════════════════════════════╝"
        echo ""
        echo -e "当前状态: $status_icon  $status"
        echo -e "响应时间: ${BLUE}${duration}ms${NC}"
        echo -e "运行时间: ${elapsed_min}分${elapsed_sec}秒"
        echo ""
        echo "┌─────────────────────────────────────────┐"
        echo "│  统计信息                               │"
        echo "└─────────────────────────────────────────┘"
        echo ""
        printf "  总请求数:       ${BLUE}%5d${NC}\n" $total_requests
        echo ""
        printf "  ${GREEN}● L1 缓存命中:${NC}   %5d  (${GREEN}%5s%%${NC})\n" $l1_hits "$l1_hit_rate"
        printf "  ${CYAN}● Redis 命中:${NC}    %5d  (${CYAN}%5s%%${NC})\n" $redis_hits "$redis_hit_rate"
        printf "  ${RED}● 数据库查询:${NC}    %5d  (${RED}%5s%%${NC})\n" $db_queries "$db_rate"
        echo ""
        echo "┌─────────────────────────────────────────┐"
        echo "│  预期流程                               │"
        echo "└─────────────────────────────────────────┘"
        echo ""
        echo "  1️⃣  前 1-5 次请求"
        echo "     → 数据库查询 (响应时间 > 100ms)"
        echo ""
        echo "  2️⃣  第 6-10 次请求"
        echo "     → L2 噪音过滤生效"
        echo ""
        echo "  3️⃣  第 10+ 次请求（频繁访问）"
        echo "     → L3 热点探测触发"
        echo "     → 数据晋升到 L1 本地缓存"
        echo ""
        echo "  4️⃣  晋升后的请求"
        echo "     → L1 缓存命中 (响应时间 < 50ms) 🚀"
        echo ""
        echo "┌─────────────────────────────────────────┐"
        echo "│  提示                                   │"
        echo "└─────────────────────────────────────────┘"
        echo ""
        echo "  • 查看实时日志:"
        echo "    tail -f logs/hotarmor-framework.log"
        echo ""
        echo "  • 按 Ctrl+C 停止监控"
        echo ""

        sleep 0.5
    done
}

# 捕获 Ctrl+C
trap 'echo ""; echo ""; echo "监控已停止"; echo ""; exit 0' INT

# 开始监控
monitor
