package com.packhub.ui;

import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 自绘的可滚动列表。刻意不使用 AbstractSelectionList，
 * 以免被不同版本的渲染接口变更波及；只依赖 fill / drawString 这两个最稳定的方法。
 */
public final class ListPanel<T> {

    public static final class Action<T> {
        final String label;
        final int width;
        final Consumer<T> callback;
        final java.util.function.Predicate<T> enabled;
        int x;
        int y;

        Action(String label, int width, Consumer<T> callback, java.util.function.Predicate<T> enabled) {
            this.label = label;
            this.width = width;
            this.callback = callback;
            this.enabled = enabled;
        }
    }

    public static final class Item<T> {
        final T value;
        final List<String> lines = new ArrayList<>();
        final List<Action<T>> actions = new ArrayList<>();
        String tag = "";

        Item(T value) {
            this.value = value;
        }

        public Item<T> line(String text) {
            lines.add(text);
            return this;
        }

        public Item<T> tag(String text) {
            this.tag = text == null ? "" : text;
            return this;
        }

        public Item<T> action(String label, int width, Consumer<T> callback) {
            actions.add(new Action<>(label, width, callback, value -> true));
            return this;
        }

        public Item<T> action(String label, int width, java.util.function.Predicate<T> enabled, Consumer<T> callback) {
            actions.add(new Action<>(label, width, callback, enabled));
            return this;
        }
    }

    private final List<Item<T>> items = new ArrayList<>();
    private int x;
    private int y;
    private int width;
    private int height;
    private int rowHeight = 34;
    private double scroll;
    private T selected;

    public ListPanel<T> bounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        return this;
    }

    public ListPanel<T> rowHeight(int value) {
        this.rowHeight = value;
        return this;
    }

    public Item<T> add(T value) {
        Item<T> item = new Item<>(value);
        items.add(item);
        return item;
    }

    public void clear() {
        items.clear();
        scroll = 0;
        selected = null;
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public int size() {
        return items.size();
    }

    public List<Item<T>> items() {
        return items;
    }

    public T selected() {
        return selected;
    }

    public void selected(T value) {
        this.selected = value;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    private int contentHeight() {
        return items.size() * rowHeight;
    }

    private void clampScroll() {
        int max = Math.max(0, contentHeight() - height);
        scroll = Math.max(0, Math.min(scroll, max));
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        clampScroll();
        UiKit.panel(graphics, x, y, width, height);
        int first = (int) (scroll / rowHeight);
        int offset = (int) (scroll % rowHeight);
        int visibleRows = height / rowHeight + 2;
        for (int index = first; index < Math.min(items.size(), first + visibleRows); index++) {
            Item<T> item = items.get(index);
            int rowY = y + 1 + (index - first) * rowHeight - offset;
            if (rowY + rowHeight < y || rowY > y + height) {
                continue;
            }
            boolean hovered = UiKit.inside(mouseX, mouseY, x + 1, rowY, width - 2, rowHeight)
                    && UiKit.inside(mouseX, mouseY, x, y, width, height);
            UiKit.row(graphics, x + 1, rowY, width - 2, rowHeight, hovered, item.value == selected);

            int textX = x + 6;
            int textWidth = width - 12;
            int actionSpace = 0;
            for (Action<T> action : item.actions) {
                actionSpace += action.width + 4;
            }
            textWidth -= actionSpace;
            int lineY = rowY + 4;
            for (int i = 0; i < item.lines.size() && i < 2; i++) {
                int color = i == 0 ? UiKit.COLOR_TEXT : UiKit.COLOR_DIM;
                UiKit.drawTrimmed(graphics, item.lines.get(i), textX, lineY + i * 11, textWidth, color);
            }
            if (!item.tag.isEmpty()) {
                int tagWidth = UiKit.font().width(item.tag);
                int tagX = Math.max(textX, x + width - actionSpace - tagWidth - 8);
                graphics.drawString(UiKit.font(), item.tag, tagX, rowY + 4, UiKit.COLOR_ACCENT, false);
            }

            int actionX = x + width - 6;
            for (int i = item.actions.size() - 1; i >= 0; i--) {
                Action<T> action = item.actions.get(i);
                actionX -= action.width;
                action.x = actionX;
                action.y = rowY + (rowHeight - 16) / 2;
                boolean enabled = action.enabled.test(item.value);
                boolean actionHovered = enabled && UiKit.inside(mouseX, mouseY, action.x, action.y, action.width, 16);
                UiKit.drawButton(graphics, action.x, action.y, action.width, 16, action.label, actionHovered, enabled);
                actionX -= 4;
            }
        }
        float visibleFraction = contentHeight() <= 0 ? 1f : Math.min(1f, height / (float) contentHeight());
        float fraction = contentHeight() <= height
                ? 0f
                : (float) (scroll / (double) (contentHeight() - height));
        UiKit.scrollbar(graphics, x + width - 2, y + 2, height - 4, fraction, visibleFraction);

        if (items.isEmpty()) {
            graphics.drawCenteredString(UiKit.font(), "§7这里还没有内容", x + width / 2, y + height / 2 - 4, 0xFFFFFF);
        }
    }

    /** @return 是否消费了点击 */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!UiKit.inside(mouseX, mouseY, x, y, width, height)) {
            return false;
        }
        int first = (int) (scroll / rowHeight);
        int offset = (int) (scroll % rowHeight);
        for (int index = first; index < items.size(); index++) {
            Item<T> item = items.get(index);
            int rowY = y + 1 + (index - first) * rowHeight - offset;
            if (rowY > y + height) {
                break;
            }
            for (Action<T> action : item.actions) {
                if (action.enabled.test(item.value) && UiKit.inside(mouseX, mouseY, action.x, action.y, action.width, 16)) {
                    action.callback.accept(item.value);
                    return true;
                }
            }
        }
        // 选中整行
        int index = (int) ((mouseY - y + scroll) / rowHeight);
        if (index >= 0 && index < items.size()) {
            selected = items.get(index).value;
        }
        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!UiKit.inside(mouseX, mouseY, x, y, width, height)) {
            return false;
        }
        scroll -= delta * rowHeight * 0.5;
        clampScroll();
        return true;
    }

    public int scrollOffset() {
        return (int) scroll;
    }
}
