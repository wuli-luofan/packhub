package com.packhub.platform;

import java.nio.file.Path;
import java.util.Set;

/**
 * 加载器适配层：所有 Fabric / NeoForge 差异都收敛到这几个方法。
 */
public interface Platform {

    /** 人类可读的加载器名称，例如 "Fabric" / "NeoForge"。 */
    String loaderName();

    /** 加载器版本号。 */
    String loaderVersion();

    /** 游戏根目录（.minecraft）。 */
    Path gameDir();

    /** 配置目录（.minecraft/config）。 */
    Path configDir();

    /** 模组目录（.minecraft/mods）。 */
    Path modsDir();

    /** 当前已加载的所有模组 id。 */
    Set<String> loadedModIds();

    default boolean isModLoaded(String modId) {
        return loadedModIds().contains(modId);
    }

    /** 是否处于开发/数据生成环境以外正常客户端。 */
    default boolean isClient() {
        return true;
    }

    /** 加载器主目录（.minecraft/.fabric 或 .minecraft/neoforge）。 */
    default Path loaderDataDir() {
        return gameDir();
    }

    /**
     * PackHub 自己的联机通道（房主下发模组用）。
     * 默认返回不可用实现，加载器模块按需覆盖。
     */
    default NetworkBridge network() {
        return NetworkBridge.NOOP;
    }
}
