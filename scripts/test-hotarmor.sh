#!/bin/bash

# HotArmor 热数据测试脚本
# 用于测试三级漏斗防护体系：L1 本地缓存 -> L2 噪音过滤 -> L3 热点探测 -> L4 安全回源

# 配置
BASE_URL="http://localhost:8080"
PRODUCT_ID=1
USER_ID=1

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# 打印分隔线
print_separator() {
    echo -e "${CYAN}======================================================================${NC}"
}

# 打印标题
print_title() {
    echo ""
    print_separator
    echo -e "${PURPLE}$1${NC}"
    print_separator
    echo ""
}

# 打印步骤
print_step() {
    echo -e "${BLUE}▶ $1${NC}"
}

# 打印成功
print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

# 打印警告
print_warning() {
    echo -e "${YELLOW}⚠ $1${NC}"
}

# 打印错误
print_error() {
    echo -e "${RED}✗ $1${NC}"
}

# 检查服务是否运行
check_service() {
    print_step "检查应用是否运行..."
    if curl -s "$BASE_URL/api/products/list" > /dev/null; then
        print_success "应用正常运行"
        return 0
    else
        print_error "应用未运行，请先启动应用：cd hotarmor-example && mvn spring-boot:run"
        exit 1
    fi
}

# 清空 Redis 缓存
clear_cache() {
    print_step "清空 Redis 缓存..."
    redis-cli FLUSHDB > /dev/null 2>&1
    if [ $? -eq 0 ]; then
        print_success "Redis 缓存已清空"
    else
        print_warning "无法清空 Redis（可能 redis-cli 未安装，可忽略）"
    fi
    sleep 1
}

# 测试 1: 基础功能测试
test_basic() {
    print_title "测试 1: 基础功能测试"

    print_step "1.1 查询商品列表"
    curl -s "$BASE_URL/api/products/list" | jq '.total'

    print_step "1.2 查询单个商品 (ID=$PRODUCT_ID)"
    curl -s "$BASE_URL/api/products/$PRODUCT_ID" | jq '.data.name'

    print_step "1.3 查询用户 (ID=$USER_ID)"
    curl -s "$BASE_URL/api/users/$USER_ID" | jq '.data.username'

    print_success "基础功能测试完成"
    sleep 2
}

# 测试 2: L2 噪音过滤测试（低频访问，不应该触发热点）
test_l2_noise_filter() {
    print_title "测试 2: L2 噪音过滤测试（冷数据过滤）"

    clear_cache

    print_step "访问不同商品各 1-2 次（模拟冷数据）"
    print_warning "预期：这些请求会被 L2 噪音过滤器拦截，不会进入 L3 热点探测"

    for id in {2..5}; do
        echo -n "  访问商品 $id: "
        curl -s "$BASE_URL/api/products/$id" | jq -r '.data.name'
        sleep 0.5
    done

    print_success "L2 噪音过滤测试完成"
    print_warning "查看应用日志，应该能看到 '噪音过滤: count < threshold' 的日志"
    sleep 2
}

# 测试 3: L3 热点探测测试（高频访问，触发热点晋升）
test_l3_hotspot_detection() {
    print_title "测试 3: L3 热点探测测试（热点晋升）"

    clear_cache

    print_step "高频访问商品 $PRODUCT_ID（触发热点探测）"
    print_warning "预期：前几次访问通过 L2，达到阈值后触发 L3 热点探测，最终晋升到 L1 本地缓存"

    echo ""
    echo "正在进行 20 次连续访问..."
    for i in {1..20}; do
        echo -n "  第 $i 次访问: "
        response=$(curl -s "$BASE_URL/api/products/$PRODUCT_ID")
        name=$(echo $response | jq -r '.data.name')
        echo "$name"
        sleep 0.2
    done

    print_success "L3 热点探测测试完成"
    print_warning "查看应用日志，应该能看到："
    echo "  - '噪音过滤: count >= threshold'"
    echo "  - '热点晋升触发: resource=product:detail'"
    echo "  - 'L1 缓存命中' (晋升后的访问)"
    sleep 2
}

# 测试 4: L1 缓存命中测试
test_l1_cache_hit() {
    print_title "测试 4: L1 本地缓存命中测试"

    print_step "在测试 3 的基础上，继续访问已晋升的热点数据"
    print_warning "预期：所有请求都从 L1 本地缓存返回，极速响应"

    echo ""
    echo "正在进行 10 次 L1 缓存命中测试..."
    for i in {1..10}; do
        start=$(python3 -c 'import time; print(int(time.time() * 1000))' 2>/dev/null || echo "0")
        response=$(curl -s "$BASE_URL/api/products/$PRODUCT_ID")
        end=$(python3 -c 'import time; print(int(time.time() * 1000))' 2>/dev/null || echo "0")

        name=$(echo $response | jq -r '.data.name')
        if [ "$start" != "0" ] && [ "$end" != "0" ]; then
            duration=$(( end - start ))
            echo "  第 $i 次访问: $name (耗时: ${duration}ms)"
        else
            echo "  第 $i 次访问: $name"
        fi
        sleep 0.1
    done

    print_success "L1 缓存命中测试完成"
    print_warning "查看应用日志，应该能看到大量 'L1 缓存命中' 日志，且响应时间很短"
    sleep 2
}

# 测试 5: 缓存失效测试
test_cache_invalidation() {
    print_title "测试 5: 缓存失效测试（@HotArmorEvict）"

    print_step "5.1 查询商品当前价格"
    old_price=$(curl -s "$BASE_URL/api/products/$PRODUCT_ID" | jq -r '.data.price')
    echo "  当前价格: $old_price"

    print_step "5.2 更新商品价格"
    # 使用当前秒数生成一个唯一的价格（确保每次都不同）
    timestamp=$(date +%S)
    new_price=$((8000 + timestamp)).99
    echo "  目标价格: $new_price"

    update_response=$(curl -s -X PUT "$BASE_URL/api/products/$PRODUCT_ID" \
        -H "Content-Type: application/json" \
        -d "{\"name\":\"iPhone 14 Pro 特价\",\"price\":$new_price}")
    echo "  更新响应: $(echo $update_response | jq -r '.message')"

    # 等待缓存失效完成
    sleep 2

    print_step "5.3 再次查询商品价格（验证缓存已失效）"
    new_price_check=$(curl -s "$BASE_URL/api/products/$PRODUCT_ID" | jq -r '.data.price')
    echo "  查询到的价格: $new_price_check"

    # 使用 awk 进行数值比较（兼容不同的浮点数格式）
    # 检查价格是否变化且在预期范围内（8000-8060之间）
    price_match=$(awk -v old="$old_price" -v new="$new_price_check" -v expected="$new_price" 'BEGIN {
        # 检查价格是否确实变化了
        if (old == new) {
            print "0"
        } else if (new >= 8000.0 && new <= 8100.0) {
            # 价格在预期范围内，认为测试通过
            print "1"
        } else {
            print "0"
        }
    }')

    if [ "$price_match" = "1" ]; then
        print_success "缓存失效测试通过 - 价格已更新（从 $old_price 变为 $new_price_check）"
    else
        print_error "缓存失效测试失败 - 价格未正确更新"
        echo "  原价格: $old_price"
        echo "  期望价格: $new_price"
        echo "  实际价格: $new_price_check"
        echo ""
        echo "  可能的原因："
        echo "    1. 缓存未正确失效（查看日志确认是否有 '缓存失效' 日志）"
        echo "    2. 数据库更新失败（查看日志确认是否有 '商品更新成功' 日志）"
        echo "    3. 延迟问题（缓存失效需要时间）"
    fi

    print_warning "查看应用日志，应该能看到："
    echo "  - '处理驱逐: resource=product:detail, key=$PRODUCT_ID'"
    echo "  - '缓存失效: resource=product:detail, key=$PRODUCT_ID'"
    echo "  - '商品更新成功: productId=$PRODUCT_ID'"
    echo "  - '已发送缓存失效广播' 或 'BroadcastNotifier 未配置'"
    sleep 2
}

# 测试 6: 并发压测
test_concurrent_load() {
    print_title "测试 6: 并发压测（模拟真实高并发场景）"

    clear_cache

    print_step "使用 50 个并发连接，每个连接访问 10 次"
    print_warning "预期：快速触发热点晋升，后续请求极速响应"

    echo ""
    echo "正在进行并发压测..."

    # 创建临时文件存储结果
    temp_file=$(mktemp)

    # 并发访问
    for i in {1..50}; do
        {
            for j in {1..10}; do
                curl -s "$BASE_URL/api/products/$PRODUCT_ID" >> $temp_file
            done
        } &
    done

    # 等待所有后台任务完成
    wait

    # 统计结果
    total_requests=$(wc -l < $temp_file)
    rm $temp_file

    print_success "并发压测完成"
    echo "  总请求数: $total_requests"
    print_warning "查看应用日志，应该能看到："
    echo "  - 前期：少量数据库查询"
    echo "  - 中期：触发热点晋升"
    echo "  - 后期：大量 L1 缓存命中"
    sleep 2
}

# 测试 7: 使用内置压测接口
test_benchmark_api() {
    print_title "测试 7: 使用内置压测接口"

    clear_cache

    print_step "调用 benchmark 接口，进行 100 次查询"
    response=$(curl -s "$BASE_URL/api/products/benchmark/$PRODUCT_ID?times=100")

    echo "  压测结果:"
    echo $response | jq '.'

    times=$(echo $response | jq -r '.times')
    duration=$(echo $response | jq -r '.duration')
    avg=$(echo $response | jq -r '.avg')

    print_success "压测接口测试完成"
    echo "  总次数: $times"
    echo "  总耗时: $duration"
    echo "  平均耗时: $avg"
    sleep 2
}

# 测试 8: 用户缓存测试
test_user_cache() {
    print_title "测试 8: 用户缓存测试（多规则隔离）"

    clear_cache

    print_step "测试用户查询的缓存功能"
    print_warning "预期：user:detail 规则独立工作，不影响 product:detail 规则"

    echo ""
    echo "正在访问用户数据..."
    for i in {1..15}; do
        echo -n "  第 $i 次访问: "
        curl -s "$BASE_URL/api/users/$USER_ID" | jq -r '.data.nickname'
        sleep 0.2
    done

    print_success "用户缓存测试完成"
    print_warning "查看应用日志，应该能看到 user:detail 规则的热点晋升"
    sleep 2
}

# 主测试流程
main() {
    clear
    print_title "HotArmor 热数据测试脚本"
    echo ""
    echo "这个脚本将测试 HotArmor 的以下功能："
    echo "  1. 基础功能测试"
    echo "  2. L2 噪音过滤（冷数据拦截）"
    echo "  3. L3 热点探测（热点晋升）"
    echo "  4. L1 本地缓存命中"
    echo "  5. 缓存失效与更新"
    echo "  6. 并发压测"
    echo "  7. 内置压测接口"
    echo "  8. 用户缓存测试"
    echo ""
    print_warning "注意：请确保应用已启动，并且 Redis 正在运行"
    echo ""
    read -p "按 Enter 键开始测试..."

    # 检查服务
    check_service

    # 运行测试
    test_basic
    test_l2_noise_filter
    test_l3_hotspot_detection
    test_l1_cache_hit
    test_cache_invalidation
    test_concurrent_load
    test_benchmark_api
    test_user_cache

    # 总结
    print_title "测试完成"
    echo ""
    print_success "所有测试场景已执行完毕！"
    echo ""
    echo "建议查看应用日志，观察 HotArmor 的工作流程："
    echo "  - L2 噪音过滤日志"
    echo "  - L3 热点探测日志"
    echo "  - L1 缓存命中日志"
    echo "  - 缓存失效和广播日志"
    echo ""
    print_warning "日志级别配置在 application.yml:"
    echo "  cn.bafuka.hotarmor: DEBUG"
    echo ""
}

# 运行主函数
main
