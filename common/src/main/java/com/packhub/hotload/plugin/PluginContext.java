package com.packhub.hotload.plugin;

import java.nio.file.Path;

/**
 * 插件拿到的上下文对象。
 * <p>插件格式（packhub-plugin.json）是 PackHub 定义的“可热加载模组”格式：
 * 只要遵守这个接口，jar 就可以在游戏运行中被加载 / 卸载 / 重新加载，不需要重启。</p>
 * <p>这里刻意<b>不</b>直接引用 Minecraft 类型，这样插件在编译期只需依赖 PackHub 的插件 API，
 * 不需要完整的 Minecraft 依赖；需要 Minecraft 实例时用 {@link #minecraftClient()} 再自行转型。</p>
 */
public interface PluginContext {

    String pluginId();

    String pluginName();

    String pluginVersion();

    /** 插件自己的数据目录（config/packhub/plugins/data/&lt;id&gt;），可自由读写。 */
    Path dataDir();

    Path gameDir();

    Path configDir();

    Path modsDir();

    /**
     * 当前 Minecraft 客户端实例（编译期类型为 Object）。
     * 如果插件自己依赖了 Minecraft，可以直接强转成 {@code net.minecraft.client.Minecraft}。
     */
    Object minecraftClient();

    void log(String message, Object... args);

    /** 在游戏里弹一条提示。 */
    void toast(String message);
}
