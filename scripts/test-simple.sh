#!/bin/bash

# HotArmor 简单热点测试脚本
# 快速测试热点晋升功能

BASE_URL="http://localhost:8080"
PRODUCT_ID=1

echo "=========================================="
echo "  HotArmor 热点测试"
echo "=========================================="
echo ""

# 清空 Redis
echo "1. 清空 Redis 缓存..."
redis-cli FLUSHDB > /dev/null 2>&1
echo "   ✓ 完成"
sleep 1

# 第一次访问（数据库查询）
echo ""
echo "2. 第一次访问（从数据库加载）..."
curl -s "$BASE_URL/api/products/$PRODUCT_ID" | jq -r '.data.name'
echo "   查看日志: 应该看到 '从数据库查询商品'"
sleep 1

# 多次访问（触发热点探测）
echo ""
echo "3. 连续访问 20 次（触发热点探测）..."
for i in {1..20}; do
    echo -n "   访问 $i/20..."
    curl -s "$BASE_URL/api/products/$PRODUCT_ID" > /dev/null
    echo " ✓"
    sleep 0.1
done
echo "   查看日志: 应该看到 '热点晋升触发'"
sleep 1

# 再次访问（L1 缓存命中）
echo ""
echo "4. 再次访问（应该命中 L1 缓存）..."
for i in {1..5}; do
    # macOS 兼容的时间测量
    start=$(python3 -c 'import time; print(int(time.time() * 1000))' 2>/dev/null || echo "0")
    name=$(curl -s "$BASE_URL/api/products/$PRODUCT_ID" | jq -r '.data.name')
    end=$(python3 -c 'import time; print(int(time.time() * 1000))' 2>/dev/null || echo "0")

    if [ "$start" != "0" ] && [ "$end" != "0" ]; then
        duration=$(( end - start ))
        echo "   访问 $i: $name (${duration}ms)"
    else
        echo "   访问 $i: $name"
    fi
    sleep 0.2
done
echo "   查看日志: 应该看到大量 'L1 缓存命中'"

echo ""
echo "=========================================="
echo "  测试完成！"
echo "=========================================="
echo ""
echo "建议查看应用日志，观察以下内容："
echo "  - L2 噪音过滤: count < threshold"
echo "  - L3 热点探测: 热点晋升触发"
echo "  - L1 缓存命中: 极速返回"
echo ""
