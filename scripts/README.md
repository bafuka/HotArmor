# 测试脚本目录

此目录包含 HotArmor 项目的所有测试和监控脚本。

## 脚本列表

### 快速测试
- **test-simple.sh** - 快速功能验证（30秒）
  - 适合快速检查系统是否正常工作
  - 测试基本的缓存功能

### 完整测试
- **test-hotarmor.sh** - 完整测试套件
  - 8个测试场景
  - 包含所有核心功能测试
  - 详细的测试报告

### 性能测试
- **test-performance.sh** - 性能基准测试
  - 使用 Apache Bench (ab) 进行压测
  - 对比缓存前后的性能差异
  - 生成性能报告

### 监控工具
- **monitor.sh** - 实时监控面板
  - 实时显示系统状态
  - 监控缓存命中率
  - 显示 Sentinel 规则状态

## 使用方法

```bash
# 确保脚本有执行权限
chmod +x scripts/*.sh

# 快速测试（推荐首次使用）
./scripts/test-simple.sh

# 完整测试
./scripts/test-hotarmor.sh

# 性能测试
./scripts/test-performance.sh

# 实时监控
./scripts/monitor.sh
```

## 前置条件

所有脚本运行前需要：
1. Redis 已启动（`redis-server` 或 Docker）
2. 应用已编译（`mvn clean install`）
