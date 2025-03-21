package cn.bafuka.hotarmor.consistency;

import cn.bafuka.hotarmor.core.HotArmorContext;

/**
 * 缓存失效监听器接口
 * 当收到广播消息时，清理本地缓存
 */
public interface InvalidationListener {

    /**
     * 处理缓存失效事件
     *
     * @param context 上下文信息
     */
    void onInvalidate(HotArmorContext context);
}
