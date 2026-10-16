package com.packhub.ui;

import com.packhub.PackHub;
import com.packhub.config.PackHubConfig;
import com.packhub.util.Log;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 统一的消息提示（右上角 toast）+ 聊天栏兜底。
 * <p>26.x 里 toast 的调用方式改过（{@code minecraft.gui.toastManager().addToast(new SystemToast(id, title, msg))}），
 * 这里全部走反射：即使某个版本又换了 API，也只是退化成聊天栏提示，不会让模组编译/运行失败。</p>
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
        push(title, message);
    }

    public static void error(String title, String message) {
        Log.warn("[PackHub] %s: %s", title, message);
        push(title, message);
    }

    private static void push(String title, String message) {
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

    /** 反射调用 {@code minecraft.gui.toastManager().addToast(new SystemToast(id, title, msg))}。 */
    private static boolean tryToast(Minecraft minecraft, String title, String message) {
        try {
            Class<?> systemToast = Class.forName("net.minecraft.client.gui.components.toasts.SystemToast");
            Class<?> toastId = Class.forName("net.minecraft.client.gui.components.toasts.SystemToast$SystemToastId");
            Object id = firstConstant(systemToast, toastId);
            if (id == null) {
                return false;
            }
            Constructor<?> constructor = systemToast.getConstructor(toastId, Component.class, Component.class);
            Object toast = constructor.newInstance(id, Component.literal(title), Component.literal(truncate(message)));

            Object toastManager = resolveToastManager(minecraft);
            if (toastManager == null) {
                return false;
            }
            Method addToast = toastManager.getClass().getMethod("addToast", Class.forName(
                    "net.minecraft.client.gui.components.toasts.Toast"));
            addToast.invoke(toastManager, toast);
            return true;
        } catch (Throwable t) {
            Log.debug("[PackHub] Toast 调用失败（将退回聊天栏）: %s", t.toString());
            return false;
        }
    }

    /** 26.x：minecraft.gui.toastManager()；旧版本：minecraft.getToastManager()。 */
    private static Object resolveToastManager(Minecraft minecraft) {
        try {
            Method guiGetter = Minecraft.class.getMethod("gui");
            Object gui = guiGetter.invoke(minecraft);
            if (gui != null) {
                Method toastManager = gui.getClass().getMethod("toastManager");
                Object manager = toastManager.invoke(gui);
                if (manager != null) {
                    return manager;
                }
            }
        } catch (Throwable ignored) {
            // 落到旧 API
        }
        try {
            return Minecraft.class.getMethod("getToastManager").invoke(minecraft);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Object firstConstant(Class<?> owner, Class<?> idType) {
        for (Field field : owner.getFields()) {
            if (!idType.isAssignableFrom(field.getType())) {
                continue;
            }
            try {
                Object value = field.get(null);
                if (value != null) {
                    return value;
                }
            } catch (Throwable ignored) {
                // 换下一个
            }
        }
        return null;
    }

    private static void chatFallback(Minecraft minecraft, String title, String message) {
        try {
            if (minecraft.player == null) {
                return;
            }
            Component text = Component.literal("§b[" + title + "] §f" + truncate(message));
            // 26.x 用 sendSystemMessage；旧版本叫 displayClientMessage
            for (String method : new String[]{"sendSystemMessage", "displayClientMessage"}) {
                try {
                    Method target = minecraft.player.getClass().getMethod(method, Component.class);
                    target.invoke(minecraft.player, text);
                    return;
                } catch (NoSuchMethodException ignored) {
                    // 试下一个
                }
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
