package com.packhub.hotload;

import com.packhub.util.Log;

import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** 通过 jar 内的元数据文件判断这是哪种模组。 */
public final class ModJarInspector {
    private ModJarInspector() {
    }

    public static JarKind inspect(Path jar) {
        if (jar == null) {
            return JarKind.UNKNOWN;
        }
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            if (has(zip, "packhub-plugin.json")) {
                return JarKind.PACKHUB_PLUGIN;
            }
            if (has(zip, "fabric.mod.json")) {
                return JarKind.FABRIC_MOD;
            }
            if (has(zip, "META-INF/neoforge.mods.toml") || has(zip, "META-INF/mods.toml")) {
                return JarKind.NEOFORGE_MOD;
            }
            return JarKind.UNKNOWN;
        } catch (Exception e) {
            Log.warn("[PackHub] 无法读取 %s: %s", jar.getFileName(), e.toString());
            return JarKind.UNKNOWN;
        }
    }

    private static boolean has(ZipFile zip, String entry) {
        ZipEntry found = zip.getEntry(entry);
        return found != null;
    }
}
