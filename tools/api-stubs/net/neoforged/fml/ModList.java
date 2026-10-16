package net.neoforged.fml;
import java.util.List;
import java.util.Optional;
import net.neoforged.neoforgespi.language.IModInfo;
public class ModList {
    public static ModList get() { return null; }
    public Optional<ModContainer> getModContainerById(String id) { return Optional.empty(); }
    public List<IModInfo> getMods() { return List.of(); }
}