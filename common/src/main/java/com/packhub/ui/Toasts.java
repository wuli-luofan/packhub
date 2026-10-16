package com.packhub.ui;

import com.packhub.PackHub;
import com.packhub.config.PackHubConfig;
import com.packhub.util.Log;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Method;

/**
 * 统一的消息提示（右上角 toast）+ 聊天栏兜底。
 * <p>toast 的具体签名在不同版本里改过几次，这里全部走反射调用：
 * 即使某个版本换了 API，也只是退化成聊天栏提示，不会让整个模组编译/运行失败。</p>
 */
public final class Toasts {
    private Toasts() {
    }

    public static void show(String title, String message) {
        Log.info("[PackHub] %s: %s", title, message);
        PackHubConfig config = safeConfig();
        if (config != null && !config.showToasts) {
            return;
        }
        push(title, message, false);
    }

    public static void error(String title, String message) {
        Log.warn("[PackHub] %s: %s", title, message);
        push(title, message, true);
    }

    private static void push(String title, String message, boolean error) {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.execute(() -> {
                if (!tryToast(minecraft, title, message)) {
                    chatFallback(minecraft, title, message);
                }
            });
        } catch (Throwable t) {
            Log.debug("[PackHub] 显示提示失败: %s", t.toString());
        }
    }

    /** 反射调用 SystemToast.addOrUpdate(ToastManager, SystemToastId, Component, Component)。 */
    private static boolean tryToast(Minecraft minecraft, String title, String message) {
        try {
            Object toastManager = Minecraft.class.getMethod("getToastManager").invoke(minecraft);
            Class<?> systemToast = Class.forName("net.minecraft.client.gui.components.toasts.SystemToast");
            Class<?> toastManagerClass = Class.forName("net.minecraft.client.gui.components.toasts.ToastManager");
            Class<?> toastId = Class.forName("net.minecraft.client.gui.components.toasts.SystemToast$SystemToastId");

            Object id = null;
            for (String field : new String[]{"PERIODIC_NOTIFICATION", "NARRATOR", "TUTORIAL_HINT", "WORLD_LOADING"}) {
                try {
                    id = systemToast.getField(field).get(null);
                    break;
                } catch (Throwable ignored) {
                    // 换下一个候选常量
                }
            }
            if (id == null) {
                return false;
            }
            Method add = systemToast.getMethod("addOrUpdate", toastManagerClass, toastId, Component.class, Component.class);
            add.invoke(null, toastManager, id, Component.literal(title), Component.literal(truncate(message)));
            return true;
        } catch (Throwable t) {
            Log.debug("[PackHub] Toast 调用失败（将退回聊天栏）: %s", t.toString());
            return false;
        }
    }

    private static void chatFallback(Minecraft minecraft, String title, String message) {
        try {
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(
                        Component.literal("§b[" + title + "] §f" + truncate(message)), false);
            }
        } catch (Throwable ignored) {
            // 什么都不做
        }
    }

    private static PackHubConfig safeConfig() {
        try {
            return PackHub.isInitialized() ? PackHub.config() : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static String truncate(String message) {
        if (message == null) {
            return "";
        }
        return message.length() > 120 ? message.substring(0, 117) + "..." : message;
    }
}
