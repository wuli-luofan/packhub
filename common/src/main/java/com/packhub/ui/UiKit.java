package com.packhub.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * 绘制小工具：面板、按钮、文本截断等。
 * <p>26.x 的渲染接口变化较大：{@code GuiGraphics} 已改名为 {@code GuiGraphicsExtractor}，
 * 文本绘制统一走 {@code text(...)}，且颜色必须是 ARGB（0xAARRGGBB）。这里把这些差异收敛在一处。</p>
 */
public final class UiKit {

    public static final int COLOR_PANEL = 0xC0101010;
    public static final int COLOR_PANEL_LIGHT = 0x80181818;
    public static final int COLOR_BORDER = 0xFF3C3C3C;
    public static final int COLOR_ACCENT = 0xFF4CC2FF;
    public static final int COLOR_OK = 0xFF55FF55;
    public static final int COLOR_WARN = 0xFFFFAA00;
    public static final int COLOR_ERROR = 0xFFFF5555;
    public static final int COLOR_TEXT = 0xFFE0E0E0;
    public static final int COLOR_DIM = 0xFF9A9A9A;
    private static final int COLOR_WHITE = 0xFFFFFFFF;

    private UiKit() {
    }

    public static Font font() {
        return Minecraft.getInstance().font;
    }

    public static void panel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, COLOR_PANEL);
        graphics.fill(x, y, x + width, y + 1, COLOR_BORDER);
        graphics.fill(x, y + height - 1, x + width, y + height, COLOR_BORDER);
        graphics.fill(x, y, x + 1, y + height, COLOR_BORDER);
        graphics.fill(x + width - 1, y, x + width, y + height, COLOR_BORDER);
    }

    public static void row(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                           boolean hovered, boolean selected) {
        int color = selected ? 0x8030B0FF : (hovered ? 0x40FFFFFF : 0x20FFFFFF);
        graphics.fill(x, y, x + width, y + height, color);
    }

    public static void drawButton(GuiGraphicsExtractor graphics, int x, int y, int width, int height, String label,
                                  boolean hovered, boolean enabled) {
        int background = !enabled ? 0xFF2A2A2A : (hovered ? 0xFF3A6F9E : 0xFF2E2E2E);
        graphics.fill(x, y, x + width, y + height, background);
        graphics.fill(x, y, x + width, y + 1, COLOR_BORDER);
        graphics.fill(x, y + height - 1, x + width, y + height, COLOR_BORDER);
        graphics.fill(x, y, x + 1, y + height, COLOR_BORDER);
        graphics.fill(x + width - 1, y, x + width, y + height, COLOR_BORDER);
        int textColor = enabled ? COLOR_WHITE : 0xFF808080;
        Font font = font();
        int textWidth = font.width(label);
        if (textWidth > width - 6) {
            label = font.plainSubstrByWidth(label, width - 8) + "…";
            textWidth = font.width(label);
        }
        graphics.text(font, label, x + (width - textWidth) / 2, y + (height - 8) / 2, textColor, false);
    }

    public static void drawTrimmed(GuiGraphicsExtractor graphics, String text, int x, int y, int maxWidth, int color) {
        Font font = font();
        String value = text == null ? "" : text;
        if (font.width(value) > maxWidth) {
            value = font.plainSubstrByWidth(value, Math.max(0, maxWidth - font.width("…"))) + "…";
        }
        graphics.text(font, value, x, y, color, false);
    }

    /** 居中绘制一行文本（自己算宽度，不依赖版本相关的居中辅助方法）。 */
    public static void centered(GuiGraphicsExtractor graphics, String text, int centerX, int y, int color) {
        Font font = font();
        String value = text == null ? "" : text;
        graphics.text(font, value, centerX - font.width(value) / 2, y, color, false);
    }

    public static void centered(GuiGraphicsExtractor graphics, Component text, int centerX, int y, int color) {
        centered(graphics, text == null ? "" : text.getString(), centerX, y, color);
    }

    /** 自己实现的换行绘制（不依赖 Font#split，避免版本差异）。 */
    public static void drawWrapped(GuiGraphicsExtractor graphics, String text, int x, int y, int maxWidth,
                                   int color, int maxLines) {
        Font font = font();
        String remaining = text == null ? "" : text;
        int line = 0;
        while (!remaining.isEmpty() && line < maxLines) {
            int fit = 0;
            while (fit < remaining.length() && font.width(remaining.substring(0, fit + 1)) <= maxWidth) {
                fit++;
            }
            if (fit == 0) {
                break;
            }
            graphics.text(font, remaining.substring(0, fit), x, y + line * 10, color, false);
            remaining = remaining.substring(fit).stripLeading();
            line++;
        }
    }

    public static void scrollbar(GuiGraphicsExtractor graphics, int x, int y, int height, float fraction,
                                 float visibleFraction) {
        graphics.fill(x, y, x + 3, y + height, 0x40FFFFFF);
        int barHeight = Math.max(12, (int) (height * Math.max(0.05f, visibleFraction)));
        int offset = (int) ((height - barHeight) * Math.max(0f, Math.min(1f, fraction)));
        graphics.fill(x, y + offset, x + 3, y + offset + barHeight, COLOR_ACCENT);
    }

    public static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    public static void header(GuiGraphicsExtractor graphics, Component title, int width) {
        centered(graphics, title, width / 2, 8, COLOR_WHITE);
    }

    public static String prettyBytes(long bytes) {
        if (bytes <= 0) {
            return "?";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format("%.0f KiB", kb);
        }
        double mb = kb / 1024.0;
        if (mb < 1024) {
            return String.format("%.1f MiB", mb);
        }
        return String.format("%.2f GiB", mb / 1024.0);
    }
}
