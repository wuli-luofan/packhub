package com.packhub.ui;

import com.packhub.util.Log;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

/** 界面通用小功能：打开目录、打开链接等（全部走反射以兼容不同版本）。 */
public final class ScreenUtil {
    private ScreenUtil() {
    }

    public static void openFolder(Path path) {
        try {
            if (path == null) {
                return;
            }
            Files.createDirectories(path);
            Object platform = Util.class.getMethod("getPlatform").invoke(null);
            if (invokeIfPresent(platform, "openPath", Path.class, path)
                    || invokeIfPresent(platform, "openFile", File.class, path.toFile())
                    || invokeIfPresent(platform, "openUri", String.class, path.toUri().toString())) {
                return;
            }
            Log.warn("[PackHub] 无法打开目录: %s", path);
        } catch (Throwable t) {
            Log.warn("[PackHub] 打开目录失败: %s", t.toString());
        }
    }

    public static void openUrl(String url) {
        try {
            Object platform = Util.class.getMethod("getPlatform").invoke(null);
            if (!invokeIfPresent(platform, "openUri", String.class, url)) {
                Log.warn("[PackHub] 无法打开链接: %s", url);
            }
        } catch (Throwable t) {
            Log.warn("[PackHub] 打开链接失败: %s", t.toString());
        }
    }

    public static void copyToClipboard(String text) {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            Method getHandler = Minecraft.class.getMethod("getKeyboardHandler");
            Object handler = getHandler.invoke(minecraft);
            if (handler == null) {
                handler = Minecraft.class.getField("keyboardHandler").get(minecraft);
            }
            Method setClipboard = handler.getClass().getMethod("setClipboard", String.class);
            setClipboard.invoke(handler, text);
        } catch (Throwable t) {
            Log.debug("[PackHub] 复制到剪贴板失败: %s", t.toString());
        }
    }

    private static boolean invokeIfPresent(Object target, String method, Class<?> parameterType, Object argument) {
        try {
            Method found = target.getClass().getMethod(method, parameterType);
            found.invoke(target, argument);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
