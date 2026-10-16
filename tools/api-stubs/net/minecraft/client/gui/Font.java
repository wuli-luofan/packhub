package net.minecraft.client.gui;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
public class Font {
    public int width(String text) { return 0; }
    public int width(FormattedCharSequence text) { return 0; }
    public String plainSubstrByWidth(String text, int width) { return text; }
    public List<FormattedCharSequence> split(Component text, int width) { return List.of(); }
}