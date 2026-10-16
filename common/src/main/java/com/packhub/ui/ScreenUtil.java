package com.packhub.ui;

import com.packhub.util.Log;
import net.minecraft.client.Minecraft;

import java.awt.Desktop;
import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * 界面通用小功能：打开目录、打开链接、复制到剪贴板。
 * <p>26.x 里 {@code net.minecraft.Util} 已经不存在了，所以这里不再依赖任何具体的 MC 工具类：
 * 先尝试反射调用（多套候选类名/方法名），失败就退回 {@link Desktop}，再失败就调系统命令。</p>
 */
public final class ScreenUtil {
    private ScreenUtil() {
    }

    private static final String[] PLATFORM_HOLDERS = {
            "net.minecraft.Util",
            "net.minecraft.util.Util",
            "net.minecraft.Util$Platform"
    };

    public static void openFolder(Path path) {
        try {
            if (path == null) {
                return;
            }
            Files.createDirectories(path);
            if (openViaMinecraft(path)) {
                return;
            }
            if (openViaDesktop(path.toFile())) {
                return;
            }
            if (openViaSystemCommand(path)) {
                return;
            }
            Log.warn("[PackHub] 无法打开目录: %s", path);
        } catch (Throwable t) {
            Log.warn("[PackHub] 打开目录失败: %s", t.toString());
        }
    }

    public static void openUrl(String url) {
        try {
            if (openViaMinecraft(url)) {
                return;
            }
            try {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    Desktop.getDesktop().browse(java.net.URI.create(url));
                    return;
                }
            } catch (Throwable ignored) {
                // 落到系统命令
            }
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            if (os.contains("win")) {
                new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start();
            } else if (os.contains("mac")) {
                new ProcessBuilder("open", url).start();
            } else {
                new ProcessBuilder("xdg-open", url).start();
            }
        } catch (Throwable t) {
            Log.warn("[PackHub] 打开链接失败: %s", t.toString());
        }
    }

    public static void copyToClipboard(String text) {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            Object handler = null;
            try {
                handler = Minecraft.class.getMethod("getKeyboardHandler").invoke(minecraft);
            } catch (Throwable ignored) {
                // 试字段
            }
            if (handler == null) {
                try {
                    handler = Minecraft.class.getField("keyboardHandler").get(minecraft);
                } catch (Throwable ignored) {
                    // 继续
                }
            }
            if (handler != null) {
                Method setClipboard = handler.getClass().getMethod("setClipboard", String.class);
                setClipboard.invoke(handler, text);
                return;
            }
        } catch (Throwable t) {
            Log.debug("[PackHub] 复制到剪贴板失败: %s", t.toString());
        }
    }

    // ------------------------------------------------------------ 内部

    private static boolean openViaMinecraft(Path path) {
        for (String holder : PLATFORM_HOLDERS) {
            try {
                Class<?> type = Class.forName(holder);
                Object platform = type.getMethod("getPlatform").invoke(null);
                if (platform == null) {
                    continue;
                }
                if (invokeIfPresent(platform, "openPath", Path.class, path)
                        || invokeIfPresent(platform, "openFile", File.class, path.toFile())
                        || invokeIfPresent(platform, "openUri", String.class, path.toUri().toString())) {
                    return true;
                }
            } catch (Throwable ignored) {
                // 试下一个候选
            }
        }
        return false;
    }

    private static boolean openViaMinecraft(String url) {
        for (String holder : PLATFORM_HOLDERS) {
            try {
                Class<?> type = Class.forName(holder);
                Object platform = type.getMethod("getPlatform").invoke(null);
                if (platform != null && invokeIfPresent(platform, "openUri", String.class, url)) {
                    return true;
                }
            } catch (Throwable ignored) {
                // 试下一个候选
            }
        }
        return false;
    }

    private static boolean openViaDesktop(File file) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(file);
                return true;
            }
        } catch (Throwable ignored) {
            // 交给系统命令
        }
        return false;
    }

    private static boolean openViaSystemCommand(Path path) {
        try {
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            if (os.contains("win")) {
                new ProcessBuilder("explorer", path.toAbsolutePath().toString()).start();
                return true;
            }
            if (os.contains("mac")) {
                new ProcessBuilder("open", path.toAbsolutePath().toString()).start();
                return true;
            }
            new ProcessBuilder("xdg-open", path.toAbsolutePath().toString()).start();
            return true;
        } catch (Throwable t) {
            return false;
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
