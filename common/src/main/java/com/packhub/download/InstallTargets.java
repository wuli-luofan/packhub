package com.packhub.download;

import com.packhub.modrinth.ContentType;
import com.packhub.platform.Platform;
import com.packhub.util.Log;
import net.minecraft.client.Minecraft;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

/** 内容类型 -> 安装目录。 */
public final class InstallTargets {
    private InstallTargets() {
    }

    public static Path resolve(Platform platform, ContentType type) {
        return switch (type) {
            case MOD -> platform.modsDir();
            case RESOURCEPACK -> platform.gameDir().resolve("resourcepacks");
            case SHADER -> platform.gameDir().resolve("shaderpacks");
            case DATAPACK -> datapackDir(platform);
            case MODPACK -> platform.gameDir().resolve("packhub").resolve("modpacks");
        };
    }

    /**
     * 数据包：单人游戏里直接装进当前存档，否则落到全局暂存目录。
     * 存档路径的获取方式在版本间改过（LevelResource / getWorldPath 等），因此走反射。
     */
    private static Path datapackDir(Platform platform) {
        try {
            Path worldDatapacks = currentWorldDatapacks();
            if (worldDatapacks != null) {
                Files.createDirectories(worldDatapacks);
                return worldDatapacks;
            }
        } catch (Throwable t) {
            Log.debug("[PackHub] 读取当前存档数据包目录失败: %s", t.toString());
        }
        Path global = platform.configDir().resolve("packhub").resolve("datapacks");
        try {
            Files.createDirectories(global);
        } catch (Exception e) {
            Log.warn("[PackHub] 创建数据包目录失败: %s", e.toString());
        }
        return global;
    }

    private static Path currentWorldDatapacks() throws Exception {
        Minecraft minecraft = Minecraft.getInstance();
        Object server = Minecraft.class.getMethod("getSingleplayerServer").invoke(minecraft);
        if (server == null) {
            return null;
        }
        Class<?> levelResource = Class.forName("net.minecraft.world.level.storage.LevelResource");
        Object root = levelResource.getField("ROOT").get(null);
        Method getWorldPath = server.getClass().getMethod("getWorldPath", levelResource);
        Object path = getWorldPath.invoke(server, root);
        if (path instanceof Path worldRoot) {
            return worldRoot.resolve("datapacks");
        }
        return null;
    }
}
