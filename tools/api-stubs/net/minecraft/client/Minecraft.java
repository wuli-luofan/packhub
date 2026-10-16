package net.minecraft.client;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
public class Minecraft {
    public Font font;
    public LocalPlayer player;
    public Object options;
    public final Gui gui = new Gui();
    public static Minecraft getInstance() { return null; }
    public void execute(Runnable task) { }
    public boolean isSameThread() { return true; }
    public void stop() { }
    public Object getToastManager() { return null; }
    public Object getSingleplayerServer() { return null; }
}