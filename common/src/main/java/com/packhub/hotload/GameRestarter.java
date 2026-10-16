package com.packhub.hotload;

import com.packhub.util.Log;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 软重启：把当前 JVM 的启动命令行原样再跑一遍，然后退出本进程。
 * <p>标准模组（Fabric / NeoForge）在 JVM 里加载后无法卸载，Mixin 也已经在类加载期完成织入，
 * 所以「热加载普通模组」在技术上等价于「重启进程」。PackHub 把这一步自动化：
 * 玩家点一下就完成「下载 -> 载入」的全过程，不需要手动关游戏和启动器。</p>
 */
public final class GameRestarter {

    private static final String LOOP_GUARD_ENV = "PACKHUB_RESTART_DEPTH";

    private GameRestarter() {
    }

    /** 是否能够重建启动命令。 */
    public static boolean canRestart() {
        return buildCommand() != null;
    }

    /** 生成一条可读的启动命令（用于界面展示 / 排查问题）。 */
    public static String describeCommand() {
        List<String> command = buildCommand();
        return command == null ? "无法获取当前启动命令" : String.join(" ", command);
    }

    /** 手写一份重启脚本，作为无法自动重启时的兜底方案。 */
    public static Path writeRestartScript(Path targetDir) {
        List<String> command = buildCommand();
        try {
            Files.createDirectories(targetDir);
            boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
            Path script = targetDir.resolve(windows ? "restart-minecraft.bat" : "restart-minecraft.sh");
            StringBuilder sb = new StringBuilder();
            if (windows) {
                sb.append("@echo off\r\n");
                if (command != null) {
                    sb.append(String.join(" ", command.stream().map(GameRestarter::quoteWindows).toList())).append("\r\n");
                } else {
                    sb.append("echo 无法自动生成启动命令，请手动启动游戏\r\n");
                }
            } else {
                sb.append("#!/bin/sh\n");
                if (command != null) {
                    sb.append(String.join(" ", command.stream().map(GameRestarter::quoteShell).toList())).append("\n");
                } else {
                    sb.append("echo 无法自动生成启动命令，请手动启动游戏\n");
                }
            }
            Files.writeString(script, sb.toString());
            return script;
        } catch (Exception e) {
            Log.warn("[PackHub] 生成重启脚本失败: %s", e.toString());
            return null;
        }
    }

    /**
     * 启动新进程并返回 true（调用方随后应尽快退出游戏）。
     */
    public static boolean restart() {
        int depth = restartDepth();
        if (depth >= 3) {
            Log.warn("[PackHub] 连续软重启 %d 次，停止自动重启以防死循环", depth);
            return false;
        }
        List<String> command = buildCommand();
        if (command == null) {
            Log.warn("[PackHub] 无法获取当前启动命令，无法软重启");
            return false;
        }
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            String cwd = System.getProperty("user.dir");
            if (cwd != null) {
                builder.directory(new File(cwd));
            }
            builder.environment().put(LOOP_GUARD_ENV, Integer.toString(depth + 1));
            builder.inheritIO();
            builder.start();
            Log.info("[PackHub] 已启动新进程，准备退出当前进程: %s", String.join(" ", command));
            return true;
        } catch (Exception e) {
            Log.error("[PackHub] 软重启失败", e);
            return false;
        }
    }

    private static int restartDepth() {
        try {
            return Integer.parseInt(Optional.ofNullable(System.getenv(LOOP_GUARD_ENV)).orElse("0"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 从 ProcessHandle 还原 java 可执行文件 + 参数。
     * 不同 JDK / 启动器下 arguments() 的形态略有差异，这里做统一清洗。
     */
    private static List<String> buildCommand() {
        try {
            ProcessHandle.Info info = ProcessHandle.current().info();
            String javaExecutable = resolveJavaExecutable(info);
            if (javaExecutable == null) {
                return null;
            }
            List<String> command = new ArrayList<>();
            command.add(javaExecutable);
            List<String> arguments = new ArrayList<>();
            info.arguments().ifPresent(args -> arguments.addAll(java.util.Arrays.asList(args)));
            for (String argument : arguments) {
                if (isJavaExecutable(argument)) {
                    continue;
                }
                command.add(argument);
            }
            if (command.size() <= 1) {
                return null;
            }
            return command;
        } catch (Throwable t) {
            Log.warn("[PackHub] 解析启动命令失败: %s", t.toString());
            return null;
        }
    }

    private static String resolveJavaExecutable(ProcessHandle.Info info) {
        String javaHome = System.getProperty("java.home");
        if (javaHome != null) {
            boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
            Path candidate = Path.of(javaHome, "bin", windows ? "java.exe" : "java");
            if (Files.isExecutable(candidate) || Files.isRegularFile(candidate)) {
                return candidate.toString();
            }
        }
        return info.command().orElse(null);
    }

    private static boolean isJavaExecutable(String argument) {
        if (argument == null) {
            return false;
        }
        String lower = argument.toLowerCase(Locale.ROOT);
        return lower.endsWith("java.exe") || lower.endsWith("/java") || lower.endsWith("\\java") || lower.endsWith("javaw.exe");
    }

    private static String quoteWindows(String value) {
        return value.contains(" ") ? "\"" + value + "\"" : value;
    }

    private static String quoteShell(String value) {
        return value.contains(" ") ? "'" + value + "'" : value;
    }
}
