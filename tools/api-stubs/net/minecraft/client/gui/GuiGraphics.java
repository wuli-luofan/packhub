package net.minecraft.client.gui;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
public class GuiGraphics {
    public void fill(int x1, int y1, int x2, int y2, int color) { }
    public int drawString(Font font, String text, int x, int y, int color, boolean shadow) { return 0; }
    public int drawString(Font font, Component text, int x, int y, int color, boolean shadow) { return 0; }
    public int drawString(Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow) { return 0; }
    public int drawCenteredString(Font font, Component text, int x, int y, int color) { return 0; }
    public int drawCenteredString(Font font, String text, int x, int y, int color) { return 0; }
}