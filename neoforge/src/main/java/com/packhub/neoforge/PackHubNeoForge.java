package com.packhub.neoforge;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.packhub.PackHub;
import com.packhub.client.KeyMappings;
import com.packhub.platform.LogBridge;
import net.minecraft.client.KeyMapping;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/** NeoForge 客户端入口。 */
@Mod(value = PackHub.MOD_ID, dist = Dist.CLIENT)
public final class PackHubNeoForge {

    /** 键位构造走反射兼容层，失败时退化成只用 /packhub 命令。 */
    public static final KeyMapping OPEN_HUB = KeyMappings.createOpenHubMapping(GLFW.GLFW_KEY_P);

    public PackHubNeoForge(IEventBus modEventBus, ModContainer container) {
        LogBridge.install();
        PackHub.init(new NeoForgePlatform());
        modEventBus.addListener(this::onRegisterKeyMappings);
    }

    private void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        if (OPEN_HUB != null) {
            event.register(OPEN_HUB);
        }
    }

    /** 客户端事件订阅。 */
    @EventBusSubscriber(modid = PackHub.MOD_ID, value = Dist.CLIENT)
    public static final class ClientEvents {

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            if (OPEN_HUB != null) {
                while (OPEN_HUB.consumeClick()) {
                    PackHub.openHub();
                }
            }
            PackHub.clientTick();
        }

        @SubscribeEvent
        public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
            LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal("packhub").executes(context -> {
                PackHub.openHub();
                return 1;
            });
            event.getDispatcher().register(builder);
        }
    }
}
