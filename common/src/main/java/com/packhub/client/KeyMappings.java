package com.packhub.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.packhub.util.Log;
import net.minecraft.client.KeyMapping;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 键位注册的兼容工厂。
 * <p>1.21.6 起 {@code KeyMapping} 的分类参数从 {@code String} 换成了 {@code KeyMapping.Category} 对象，
 * 这里用反射依次尝试新老两种构造方式，避免换版本就必须改代码。</p>
 */
public final class KeyMappings {

    public static final String OPEN_HUB_KEY = "key.packhub.open_hub";
    private static final String CATEGORY_NAME = "category.packhub";
    private static final String CATEGORY_ID = "packhub";

    private KeyMappings() {
    }

    /** 建一个开界面用的键位（失败返回 null，功能会退化成只用 /packhub 命令）。 */
    public static KeyMapping createOpenHubMapping(int defaultKeyCode) {
        KeyMapping mapping = tryCreateWithCategoryObject(defaultKeyCode);
        if (mapping != null) {
            return mapping;
        }
        mapping = tryCreateWithCategoryString(defaultKeyCode);
        if (mapping != null) {
            return mapping;
        }
        try {
            // 最后的兜底：直接 new（老版本签名）
            return new KeyMapping(OPEN_HUB_KEY, InputConstants.Type.KEYSYM, defaultKeyCode, CATEGORY_NAME);
        } catch (Throwable t) {
            Log.warn("[PackHub] 无法创建键位绑定，请使用 /packhub 命令打开界面: %s", t.toString());
            return null;
        }
    }

    private static KeyMapping tryCreateWithCategoryObject(int defaultKeyCode) {
        try {
            Class<?> categoryClass = Class.forName("net.minecraft.client.KeyMapping$Category");
            Object category = resolveCategory(categoryClass);
            if (category == null) {
                return null;
            }
            Constructor<KeyMapping> constructor = KeyMapping.class.getConstructor(
                    String.class, InputConstants.Type.class, int.class, categoryClass);
            return constructor.newInstance(OPEN_HUB_KEY, InputConstants.Type.KEYSYM, defaultKeyCode, category);
        } catch (Throwable t) {
            Log.debug("[PackHub] 新版 KeyMapping.Category 构造不可用: %s", t.toString());
            return null;
        }
    }

    private static Object resolveCategory(Class<?> categoryClass) {
        for (String field : new String[]{"MISC", "GAMEPLAY", "MOVEMENT", "MULTIPLAYER", "CREATIVE", "DEBUG"}) {
            try {
                Field candidate = categoryClass.getField(field);
                Object value = candidate.get(null);
                if (value != null) {
                    return value;
                }
            } catch (Throwable ignored) {
                // 换下一个候选
            }
        }
        // 注册一个自定义分类（1.21.6+ 提供 Category.register）
        try {
            Class<?> resourceLocation = Class.forName("net.minecraft.resources.ResourceLocation");
            Object id;
            try {
                id = resourceLocation.getMethod("parse", String.class).invoke(null, CATEGORY_ID);
            } catch (Throwable t) {
                id = resourceLocation.getConstructor(String.class).newInstance(CATEGORY_ID);
            }
            Method register = categoryClass.getMethod("register", resourceLocation);
            return register.invoke(null, id);
        } catch (Throwable t) {
            Log.debug("[PackHub] 注册自定义键位分类失败: %s", t.toString());
            return null;
        }
    }

    private static KeyMapping tryCreateWithCategoryString(int defaultKeyCode) {
        try {
            Constructor<KeyMapping> constructor = KeyMapping.class.getConstructor(
                    String.class, InputConstants.Type.class, int.class, String.class);
            return constructor.newInstance(OPEN_HUB_KEY, InputConstants.Type.KEYSYM, defaultKeyCode, CATEGORY_NAME);
        } catch (Throwable t) {
            return null;
        }
    }
}
