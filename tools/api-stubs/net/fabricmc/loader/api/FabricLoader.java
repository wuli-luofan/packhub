package net.fabricmc.loader.api;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;
public interface FabricLoader {
    static FabricLoader getInstance() { return null; }
    Optional<ModContainer> getModContainer(String id);
    Collection<ModContainer> getAllMods();
    Path getGameDir();
    Path getConfigDir();
}