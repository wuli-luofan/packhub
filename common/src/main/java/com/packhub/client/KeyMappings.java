package com.packhub.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.packhub.util.Log;
import net.minecraft.client.KeyMapping;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 键位注册的兼容工厂。
 * <p>26.x 里键位相关 API 变动很大：分类参数从 {@code String} 变成了 {@code KeyMapping.Category} 对象，
 * SDL 化之后 {@code InputConstants.Type} 的枚举常量也改了名。为了不再被这些改名打断构建，
 * 这里<b>完全用反射</b>构造 {@link KeyMapping}：按构造函数的参数形状去匹配，并动态挑选可用的枚举常量。</p>
 */
public final class KeyMappings {

    public static final String OPEN_HUB_KEY = "key.packhub.open_hub";
    private static final String CATEGORY_NAME = "category.packhub";
    private static final String CATEGORY_ID = "packhub";

    private KeyMappings() {
    }

    /**
     * 打开界面的默认键位。
     * <p>键值取自原版 {@link InputConstants}：Minecraft 26.3 用 SDL 取代了 GLFW，
     * {@code org.lwjgl.glfw.GLFW} 已不在类路径上，且常量数值也有调整，不能硬编码。</p>
     */
    public static int defaultOpenKey() {
        return InputConstants.KEY_P;
    }

    /** 建一个开界面用的键位（失败返回 null，功能会退化成只用 /packhub 命令）。 */
    public static KeyMapping createOpenHubMapping(int defaultKeyCode) {
        try {
            Object category = resolveCategory();
            Object type = resolveKeyType();
            if (type == null) {
                Log.warn("[PackHub] 找不到 InputConstants.Type 的可用常量，键位注册跳过");
                return null;
            }
            // 1) 新版：(String, InputConstants.Type, int, KeyMapping.Category)
            if (category != null) {
                KeyMapping mapping = tryConstructor(new Class<?>[]{String.class, type.getClass(), int.class,
                        category.getClass()}, new Object[]{OPEN_HUB_KEY, type, defaultKeyCode, category});
                if (mapping != null) {
                    return mapping;
                }
            }
            // 2) 老版：(String, InputConstants.Type, int, String)
            KeyMapping legacy = tryConstructor(new Class<?>[]{String.class, type.getClass(), int.class, String.class},
                    new Object[]{OPEN_HUB_KEY, type, defaultKeyCode, CATEGORY_NAME});
            if (legacy != null) {
                return legacy;
            }
            // 3) 再退一步：不带分类的构造
            KeyMapping minimal = tryConstructor(new Class<?>[]{String.class, type.getClass(), int.class},
                    new Object[]{OPEN_HUB_KEY, type, defaultKeyCode});
            if (minimal != null) {
                return minimal;
            }
        } catch (Throwable t) {
            Log.warn("[PackHub] 创建键位失败: %s", t.toString());
        }
        Log.warn("[PackHub] 无法创建键位绑定，请使用 /packhub 命令打开界面");
        return null;
    }

    private static KeyMapping tryConstructor(Class<?>[] parameters, Object[] arguments) {
        try {
            Constructor<KeyMapping> constructor = KeyMapping.class.getConstructor(parameters);
            return constructor.newInstance(arguments);
        } catch (Throwable t) {
            Log.debug("[PackHub] KeyMapping 构造 %s 不可用: %s", java.util.Arrays.toString(parameters), t.toString());
            return null;
        }
    }

    /** 动态挑一个 InputConstants.Type 常量（SDL 化后名字可能是 KEYBOARD / KEYS 等）。 */
    private static Object resolveKeyType() {
        try {
            Class<?> typeClass = Class.forName("com.mojang.blaze3d.platform.InputConstants$Type");
            Object[] constants = typeClass.getEnumConstants();
            if (constants == null || constants.length == 0) {
                return null;
            }
            for (String preferred : new String[]{"KEYSYM", "KEYBOARD", "KEY", "KEYS"}) {
                for (Object constant : constants) {
                    if (String.valueOf(constant).equalsIgnoreCase(preferred)) {
                        return constant;
                    }
                }
            }
            for (Object constant : constants) {
                if (String.valueOf(constant).toUpperCase(java.util.Locale.ROOT).contains("KEY")) {
                    return constant;
                }
            }
            return constants[0];
        } catch (Throwable t) {
            Log.debug("[PackHub] 解析 InputConstants.Type 失败: %s", t.toString());
            return null;
        }
    }

    /** 解析（或注册）键位分类对象。 */
    private static Object resolveCategory() {
        Class<?> categoryClass;
        try {
            categoryClass = Class.forName("net.minecraft.client.KeyMapping$Category");
        } catch (Throwable t) {
            return null;
        }
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
        // 注册自定义分类：26.x 的 ID 类型叫 Identifier，旧版本叫 ResourceLocation
        for (String idClassName : new String[]{"net.minecraft.resources.Identifier",
                "net.minecraft.resources.ResourceLocation"}) {
            try {
                Class<?> idClass = Class.forName(idClassName);
                Object id;
                try {
                    id = idClass.getMethod("parse", String.class).invoke(null, CATEGORY_ID);
                } catch (Throwable t) {
                    id = idClass.getConstructor(String.class).newInstance(CATEGORY_ID);
                }
                for (String method : new String[]{"register", "of", "create"}) {
                    try {
                        Method register = categoryClass.getMethod(method, idClass);
                        Object category = register.invoke(null, id);
                        if (category != null) {
                            return category;
                        }
                    } catch (Throwable ignored) {
                        // 试下一个方法名
                    }
                }
            } catch (Throwable t) {
                Log.debug("[PackHub] 用 %s 注册键位分类失败: %s", idClassName, t.toString());
            }
        }
        return null;
    }
}
