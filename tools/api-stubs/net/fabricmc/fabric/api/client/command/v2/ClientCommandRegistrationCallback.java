package net.fabricmc.fabric.api.client.command.v2;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.event.Event;
public interface ClientCommandRegistrationCallback {
    Event<ClientCommandRegistrationCallback> EVENT = null;
    void register(CommandDispatcher<FabricClientCommandSource> dispatcher, Object registryAccess);
}