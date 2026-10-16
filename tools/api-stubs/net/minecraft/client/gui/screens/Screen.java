package net.minecraft.client.gui.screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
public class Screen {
    protected final Component title;
    protected Minecraft minecraft;
    protected Font font;
    protected int width;
    protected int height;
    public Screen(Component title) { this.title = title; }
    protected void init() { }
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) { }
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) { return false; }
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) { return false; }
    public boolean keyPressed(KeyEvent event) { return false; }
    public void onClose() { }
    protected <T> T addRenderableWidget(T widget) { return widget; }
}