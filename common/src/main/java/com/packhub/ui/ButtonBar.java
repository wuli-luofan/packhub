package com.packhub.ui;

import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/**
 * 自适应底部按钮栏。
 *
 * <p>以前每个界面的按钮都是写死的 x 坐标，窗口小 / GUI 缩放大的时候会互相压叠，
 * 甚至跑到屏幕外面（表现就是「全屏界面不全」）。这里按可用宽度自动分行，
 * 从下往上贴底排列，并返回占用的总高度，让上面的内容区可以据此收缩。</p>
 */
public final class ButtonBar {

    /** 一行按钮之间的间距。 */
    private static final int GAP = 4;
    private static final int ROW_HEIGHT = 20;
    private static final int SIDE_MARGIN = 10;

    private record Item(String label, int width, BooleanSupplier enabled, Runnable action) {
    }

    private final List<Item> items = new ArrayList<>();
    private boolean rightAligned;

    public ButtonBar rightAligned() {
        this.rightAligned = true;
        return this;
    }

    public ButtonBar add(String label, int width, Runnable action) {
        items.add(new Item(label, width, () -> true, action));
        return this;
    }

    public ButtonBar add(String label, int width, BooleanSupplier enabled, Runnable action) {
        items.add(new Item(label, width, enabled, action));
        return this;
    }

    /** 把标签动态刷新的按钮也纳入管理（label 由调用方每次重建时给出）。 */
    public boolean isEmpty() {
        return items.isEmpty();
    }

    /**
     * 计算布局并登记按钮。
     *
     * @param screenWidth  当前界面逻辑宽度
     * @param screenHeight 当前界面逻辑高度
     * @param bottomMargin 距离底部的留白
     * @param registrar    通常是 {@code screen::addRenderableWidget}
     * @return 占用的垂直高度（含行间距），调用方用它来计算内容区底部
     */
    public int build(int screenWidth, int screenHeight, int bottomMargin,
                     Function<Button, Button> registrar) {
        if (items.isEmpty()) {
            return 0;
        }
        int available = Math.max(60, screenWidth - SIDE_MARGIN * 2);

        // 先把按钮按可用宽度分行（单个按钮过宽时收紧到可用宽度）
        List<List<Item>> rows = new ArrayList<>();
        List<Item> current = new ArrayList<>();
        int currentWidth = 0;
        for (Item item : items) {
            int width = Math.min(item.width(), available);
            int extra = current.isEmpty() ? width : width + GAP;
            if (!current.isEmpty() && currentWidth + extra > available) {
                rows.add(current);
                current = new ArrayList<>();
                currentWidth = 0;
            }
            current.add(new Item(item.label(), width, item.enabled(), item.action()));
            currentWidth += current.isEmpty() ? width : width + GAP;
        }
        if (!current.isEmpty()) {
            rows.add(current);
        }

        int rowCount = rows.size();
        int usedHeight = rowCount * ROW_HEIGHT + (rowCount - 1) * GAP;
        int firstRowY = screenHeight - bottomMargin - usedHeight;

        for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
            List<Item> row = rows.get(rowIndex);
            int rowWidth = 0;
            for (int i = 0; i < row.size(); i++) {
                rowWidth += row.get(i).width() + (i > 0 ? GAP : 0);
            }
            int x = rightAligned ? screenWidth - SIDE_MARGIN - rowWidth : SIDE_MARGIN;
            x = Math.max(SIDE_MARGIN, x);
            int y = firstRowY + rowIndex * (ROW_HEIGHT + GAP);
            for (Item item : row) {
                int buttonX = x;
                Button button = Button.builder(Component.literal(item.label()), b -> item.action().run())
                        .bounds(buttonX, y, item.width(), ROW_HEIGHT)
                        .build();
                button.active = item.enabled().getAsBoolean();
                registrar.apply(button);
                x += item.width() + GAP;
            }
        }
        return usedHeight;
    }

    /** 不登记控件，只算高度（给只想预留空间的场景）。 */
    public int measure(int screenWidth) {
        if (items.isEmpty()) {
            return 0;
        }
        int available = Math.max(60, screenWidth - SIDE_MARGIN * 2);
        int rows = 1;
        int currentWidth = 0;
        for (Item item : items) {
            int width = Math.min(item.width(), available);
            int extra = currentWidth == 0 ? width : width + GAP;
            if (currentWidth > 0 && currentWidth + extra > available) {
                rows++;
                currentWidth = 0;
                extra = width;
            }
            currentWidth += extra;
        }
        return rows * ROW_HEIGHT + (rows - 1) * GAP;
    }
}
