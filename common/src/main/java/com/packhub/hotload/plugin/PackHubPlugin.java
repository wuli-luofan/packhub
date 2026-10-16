package com.packhub.hotload.plugin;

/**
 * PackHub 热加载插件接口。
 * <p>实现类必须有一个公开的无参构造函数。生命周期：</p>
 * <ol>
 *     <li>{@link #onLoad(PluginContext)} —— jar 被加载时调用一次；</li>
 *     <li>{@link #onClientTick()} —— 客户端每个 tick 调用（实现要尽量轻量）；</li>
 *     <li>{@link #onResourceReload()} —— 资源重载后调用；</li>
 *     <li>{@link #onUnload()} —— 卸载 / 重载 / 退出游戏前调用，务必在这里释放资源。</li>
 * </ol>
 */
public interface PackHubPlugin {

    default void onLoad(PluginContext context) throws Exception {
    }

    default void onClientTick() {
    }

    default void onResourceReload() {
    }

    default void onUnload() {
    }

    /** 可选：在 PackHub 界面里显示的一行状态。 */
    default String statusLine() {
        return null;
    }
}
