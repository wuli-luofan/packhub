package net.minecraft.client.gui;
import net.minecraft.network.chat.Component;
public class GuiGraphicsExtractor {
    public void fill(int x1, int y1, int x2, int y2, int color) { }
    public void fillGradient(int x1, int y1, int x2, int y2, int colorFrom, int colorTo) { }
    public void outline(int x, int y, int width, int height, int color) { }
    public void text(Font font, String text, int x, int y, int color) { }
    public void text(Font font, String text, int x, int y, int color, boolean shadow) { }
    public void text(Font font, Component text, int x, int y, int color) { }
    public void text(Font font, Component text, int x, int y, int color, boolean shadow) { }
}