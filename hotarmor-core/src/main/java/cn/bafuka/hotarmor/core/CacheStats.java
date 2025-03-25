package cn.bafuka.hotarmor.core;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 缓存统计信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CacheStats {

    private long hitCount;
    private long missCount;
    private long loadSuccessCount;
    private long loadFailureCount;
    private long evictionCount;

    /**
     * 计算命中率
     *
     * @return 命中率（0.0 ~ 1.0）
     */
    public double hitRate() {
        long requestCount = hitCount + missCount;
        return requestCount == 0 ? 1.0 : (double) hitCount / requestCount;
    }
}
