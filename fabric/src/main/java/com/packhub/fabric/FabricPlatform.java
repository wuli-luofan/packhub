package com.packhub.fabric;

import com.packhub.platform.Platform;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

/** Fabric 加载器适配。 */
public final class FabricPlatform implements Platform {

    @Override
    public String loaderName() {
        return "Fabric";
    }

    @Override
    public String loaderVersion() {
        return FabricLoader.getInstance().getModContainer("fabricloader")
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }

    @Override
    public Path gameDir() {
        return FabricLoader.getInstance().getGameDir();
    }

    @Override
    public Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public Path modsDir() {
        return FabricLoader.getInstance().getGameDir().resolve("mods");
    }

    @Override
    public com.packhub.platform.NetworkBridge network() {
        return FabricNetwork.get();
    }

    @Override
    public Set<String> loadedModIds() {
        Set<String> ids = new LinkedHashSet<>();
        for (ModContainer container : FabricLoader.getInstance().getAllMods()) {
            ModMetadata metadata = container.getMetadata();
            if (metadata != null && metadata.getId() != null) {
                ids.add(metadata.getId());
            }
        }
        return ids;
    }
}
