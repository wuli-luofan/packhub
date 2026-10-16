package net.neoforged.neoforge.client.event;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
public class RegisterClientCommandsEvent {
    public CommandDispatcher<CommandSourceStack> getDispatcher() { return null; }
}