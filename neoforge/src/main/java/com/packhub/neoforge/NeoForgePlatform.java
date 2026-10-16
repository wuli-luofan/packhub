package com.packhub.neoforge;

import com.packhub.platform.Platform;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforgespi.language.IModInfo;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

/** NeoForge 加载器适配。 */
public final class NeoForgePlatform implements Platform {

    @Override
    public String loaderName() {
        return "NeoForge";
    }

    @Override
    public String loaderVersion() {
        try {
            return ModList.get().getModContainerById("neoforge")
                    .map(container -> container.getModInfo().getVersion().toString())
                    .orElse("unknown");
        } catch (Throwable t) {
            return "unknown";
        }
    }

    @Override
    public Path gameDir() {
        return FMLPaths.GAMEDIR.get();
    }

    @Override
    public Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public Path modsDir() {
        return FMLPaths.MODSDIR.get();
    }

    @Override
    public com.packhub.platform.NetworkBridge network() {
        return NeoForgeNetwork.get();
    }

    @Override
    public Set<String> loadedModIds() {
        Set<String> ids = new LinkedHashSet<>();
        try {
            for (IModInfo info : ModList.get().getMods()) {
                if (info != null && info.getModId() != null) {
                    ids.add(info.getModId());
                }
            }
        } catch (Throwable t) {
            // 忽略：某些早期阶段 ModList 尚未就绪
        }
        return ids;
    }
}
