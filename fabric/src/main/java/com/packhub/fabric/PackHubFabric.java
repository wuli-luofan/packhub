package com.packhub.fabric;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.packhub.PackHub;
import com.packhub.client.KeyMappings;
import com.packhub.platform.LogBridge;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;

/** Fabric 客户端入口。 */
public final class PackHubFabric implements ClientModInitializer {

    private static KeyMapping openHubKey;

    @Override
    public void onInitializeClient() {
        LogBridge.install();
        // 先注册联机通道，再初始化内核（SyncManager 会用到它）
        FabricNetwork.register();
        PackHub.init(new FabricPlatform());

        // 键位构造走反射兼容层：不同版本 KeyMapping 的分类参数类型不同
        // 键值取自原版 InputConstants（26.3 起 GLFW 已被 SDL 取代，不能再引用 GLFW 常量）
        KeyMapping mapping = KeyMappings.createOpenHubMapping(KeyMappings.defaultOpenKey());
        if (mapping != null) {
            openHubKey = KeyMappingHelper.registerKeyMapping(mapping);
        }

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (openHubKey != null) {
                while (openHubKey.consumeClick()) {
                    PackHub.openHub();
                }
            }
            PackHub.clientTick();
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> PackHub.shutdown());

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            LiteralArgumentBuilder<FabricClientCommandSource> builder = ClientCommands.literal("packhub")
                    .executes(context -> {
                        PackHub.openHub();
                        return 1;
                    });
            dispatcher.register(builder);
        });
    }
}
