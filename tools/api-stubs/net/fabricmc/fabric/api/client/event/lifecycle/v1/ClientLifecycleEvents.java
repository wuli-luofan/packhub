package net.fabricmc.fabric.api.client.event.lifecycle.v1;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.Minecraft;
public class ClientLifecycleEvents {
    public static final Event<ClientStopping> CLIENT_STOPPING = null;
    public interface ClientStopping { void onClientStopping(Minecraft client); }
}