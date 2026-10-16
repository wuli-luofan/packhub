package net.fabricmc.fabric.api.client.event.lifecycle.v1;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.Minecraft;
public class ClientTickEvents {
    public static final Event<EndTick> END_CLIENT_TICK = null;
    public interface EndTick { void onEndTick(Minecraft client); }
}