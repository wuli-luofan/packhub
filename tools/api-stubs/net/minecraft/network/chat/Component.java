package net.minecraft.network.chat;
public interface Component {
    String getString();
    static Component literal(String text) { return null; }
    static Component translatable(String key, Object... args) { return null; }
}