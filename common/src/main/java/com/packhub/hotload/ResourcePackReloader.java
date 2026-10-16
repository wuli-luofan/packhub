package com.packhub.hotload;

import com.packhub.PackHub;
import com.packhub.config.PackHubConfig;
import com.packhub.util.Log;
import net.minecraft.client.Minecraft;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;

/**
 * 资源包 / 光影包的真实热重载。
 * <p>资源包：改写 options.resourcePacks 后调用 {@code Minecraft#reloadResourcePacks()}，
 * 这一步完全由原版支持；光影：尝试反射调用 Iris / Oculus 的重载入口（装了才调）。
 * 全部通过反射访问，避免不同版本字段改名导致编译失败。</p>
 */
public final class ResourcePackReloader {
    private ResourcePackReloader() {
    }

    /** 把 zip 勾选进已启用资源包列表（原版 pack id 形如 file/xxx.zip）。 */
    public static boolean enablePack(Path packFile) {
        String fileName = packFile.getFileName().toString();
        String packId = "file/" + fileName;
        try {
            Object options = Minecraft.getInstance().options;
            Field field = findField(options.getClass(), "resourcePacks");
            if (field == null) {
                Log.warn("[PackHub] 找不到 options.resourcePacks 字段，无法自动启用资源包");
                return false;
            }
            field.setAccessible(true);
            Object value = field.get(options);
            if (value instanceof List<?> rawList) {
                @SuppressWarnings("unchecked")
                List<String> list = (List<String>) rawList;
                if (!list.contains(packId)) {
                    list.add(packId);
                }
            }
            saveOptions(options);
            Log.info("[PackHub] 已启用资源包 %s", packId);
            return true;
        } catch (Throwable t) {
            Log.warn("[PackHub] 自动启用资源包失败: %s", t.toString());
            return false;
        }
    }

    public static void disablePack(Path packFile) {
        String packId = "file/" + packFile.getFileName();
        try {
            Object options = Minecraft.getInstance().options;
            Field field = findField(options.getClass(), "resourcePacks");
            if (field == null) {
                return;
            }
            field.setAccessible(true);
            Object value = field.get(options);
            if (value instanceof List<?> rawList) {
                @SuppressWarnings("unchecked")
                List<String> list = (List<String>) rawList;
                list.remove(packId);
            }
            saveOptions(options);
        } catch (Throwable t) {
            Log.warn("[PackHub] 取消启用资源包失败: %s", t.toString());
        }
    }

    /** 触发原版资源重载。 */
    public static void reload(PackHubConfig config) {
        Minecraft minecraft = Minecraft.getInstance();
        try {
            Method method = findMethod(minecraft.getClass(), "reloadResourcePacks");
            if (method != null) {
                method.setAccessible(true);
                method.invoke(minecraft);
                Log.info("[PackHub] 已触发原版资源重载");
            } else {
                Log.warn("[PackHub] 找不到 reloadResourcePacks()，资源包需要手动 F3+T 重载");
            }
        } catch (Throwable t) {
            Log.warn("[PackHub] 资源重载失败: %s", t.toString());
        }
        if (config.reloadShaderLoader) {
            reloadShaderLoader();
        }
    }

    /** 尝试让 Iris / Oculus 重新加载光影（可选，失败只是记日志）。 */
    public static void reloadShaderLoader() {
        String[] classes = {
                "net.irisshaders.iris.Iris",
                "net.coderbot.iris.Iris",
                "net.irisshaders.oculus.Oculus"
        };
        String[] methods = {"reload", "reloadWithRenderer", "onResourceManagerReload"};
        for (String className : classes) {
            Class<?> type;
            try {
                type = Class.forName(className);
            } catch (Throwable ignored) {
                continue;
            }
            for (String methodName : methods) {
                try {
                    Method method = findMethod(type, methodName);
                    if (method == null || method.getParameterCount() != 0) {
                        continue;
                    }
                    method.setAccessible(true);
                    method.invoke(null);
                    Log.info("[PackHub] 已调用 %s#%s 热重载光影", className, methodName);
                    return;
                } catch (Throwable ignored) {
                    // 试下一个
                }
            }
        }
        Log.debug("[PackHub] 未检测到 Iris/Oculus 重载入口（未安装或接口变更），光影列表将在下次重载后刷新");
    }

    /** 检测是否安装了光影加载器。 */
    public static boolean hasShaderLoader() {
        for (String className : new String[]{"net.irisshaders.iris.Iris", "net.coderbot.iris.Iris"}) {
            try {
                Class.forName(className);
                return true;
            } catch (Throwable ignored) {
                // 继续
            }
        }
        return PackHub.platform().isModLoaded("iris") || PackHub.platform().isModLoaded("oculus");
    }

    private static void saveOptions(Object options) {
        try {
            Method save = findMethod(options.getClass(), "save");
            if (save != null) {
                save.setAccessible(true);
                save.invoke(options);
            }
        } catch (Throwable t) {
            Log.debug("[PackHub] 保存 options 失败: %s", t.toString());
        }
    }

    private static Field findField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static Method findMethod(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null && current != Object.class) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getName().equals(name)) {
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }
}
