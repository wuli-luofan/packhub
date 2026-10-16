package net.minecraft.client.gui.components;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
public class EditBox {
    public EditBox(Font font, int x, int y, int width, int height, Component message) { }
    public void setMaxLength(int value) { }
    public void setHint(Component hint) { }
    public void setValue(String value) { }
    public String getValue() { return ""; }
    public void setResponder(Consumer<String> responder) { }
}