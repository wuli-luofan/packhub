package net.minecraft.client.gui.screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
public class Screen {
    protected final Component title;
    protected Minecraft minecraft;
    protected Font font;
    protected int width;
    protected int height;
    public Screen(Component title) { this.title = title; }
    protected void init() { }
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) { }
    public boolean mouseClicked(double mouseX, double mouseY, int button) { return false; }
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) { return false; }
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) { return false; }
    public void onClose() { }
    protected <T> T addRenderableWidget(T widget) { return widget; }
}