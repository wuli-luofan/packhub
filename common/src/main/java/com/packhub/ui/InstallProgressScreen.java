package com.packhub.ui;

import com.packhub.PackHub;
import com.packhub.download.DownloadJob;
import com.packhub.download.InstallResult;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 安装进度界面：总体进度条 + 每个文件一条进度，安装中可以取消（单独取消或全部取消），
 * 全部结束后变成「本次安装结果」摘要。
 */
public class InstallProgressScreen extends Screen {

    private final Screen parent;
    private Button cancelAllButton;
    private Button openFolderButton;
    private Button restartButton;
    private Button closeButton;
    private long lastActiveAt;

    public InstallProgressScreen(Screen parent) {
        super(Component.literal("PackHub 安装进度"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        lastActiveAt = System.currentTimeMillis();
        cancelAllButton = addRenderableWidget(Button.builder(Component.literal("取消全部安装"), button -> {
            int cancelled = PackHub.downloads().cancelAll();
            if (PackHub.config().showInstallSummary) {
                Toasts.show("PackHub", cancelled > 0 ? "已请求取消 " + cancelled + " 个任务" : "当前没有进行中的任务");
            }
        }).bounds(10, height - 28, 120, 20).build());

        openFolderButton = addRenderableWidget(Button.builder(Component.literal("打开 mods 目录"), button ->
                ScreenUtil.openFolder(PackHub.platform().modsDir()))
                .bounds(136, height - 28, 120, 20).build());

        restartButton = addRenderableWidget(Button.builder(Component.literal("软重启并载入"), button ->
                PackHub.hotLoad().softRestart())
                .bounds(262, height - 28, 120, 20).build());

        addRenderableWidget(Button.builder(Component.literal("后台运行"), button -> onClose())
                .bounds(width - 200, height - 28, 90, 20).build());

        closeButton = addRenderableWidget(Button.builder(Component.literal("完成"), button -> onClose())
                .bounds(width - 104, height - 28, 94, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        List<DownloadJob> jobs = PackHub.downloads().jobs();
        boolean busy = !PackHub.downloads().allFinished();
        if (busy) {
            lastActiveAt = System.currentTimeMillis();
        }

        UiKit.header(graphics, Component.literal(busy ? "PackHub · 正在安装" : "PackHub · 本次安装结果"), width);

        long[] overall = PackHub.downloads().overallProgress();
        float fraction = overall[1] > 0 ? Math.max(0f, Math.min(1f, overall[0] / (float) overall[1])) : (busy ? 0f : 1f);
        int barX = 10;
        int barY = 30;
        int barWidth = width - 20;
        graphics.fill(barX, barY, barX + barWidth, barY + 10, 0xFF202020);
        graphics.fill(barX, barY, barX + (int) (barWidth * fraction), barY + 10, UiKit.COLOR_ACCENT);
        graphics.fill(barX, barY, barX + barWidth, barY + 1, UiKit.COLOR_BORDER);
        graphics.fill(barX, barY + 9, barX + barWidth, barY + 10, UiKit.COLOR_BORDER);
        graphics.text(font, String.format("总进度 %d/%d · %.0f%%", overall[0], overall[1], fraction * 100f),
                barX + 4, barY + 14, UiKit.COLOR_TEXT, false);

        int rowY = 56;
        int rowHeight = 26;
        int maxRows = Math.max(1, (height - 90) / rowHeight);
        List<DownloadJob> shown = new ArrayList<>();
        for (DownloadJob job : jobs) {
            if (!job.finished() || shown.size() < maxRows) {
                shown.add(job);
            }
        }
        for (int i = 0; i < shown.size() && i < maxRows; i++) {
            DownloadJob job = shown.get(i);
            int y = rowY + i * rowHeight;
            boolean hovered = UiKit.inside(mouseX, mouseY, 10, y, width - 20, rowHeight - 2);
            UiKit.row(graphics, 10, y, width - 20, rowHeight - 2, hovered, false);

            String title = job.projectTitle + "  §7" + job.fileName;
            UiKit.drawTrimmed(graphics, title, 16, y + 3, width - 160, UiKit.COLOR_TEXT);

            float jobFraction = job.fraction();
            int jobBarX = 16;
            int jobBarY = y + 14;
            int jobBarWidth = width - 160;
            graphics.fill(jobBarX, jobBarY, jobBarX + jobBarWidth, jobBarY + 4, 0xFF303030);
            int color = switch (job.status()) {
                case DONE -> UiKit.COLOR_OK;
                case FAILED -> UiKit.COLOR_ERROR;
                case CANCELLED -> UiKit.COLOR_WARN;
                default -> UiKit.COLOR_ACCENT;
            };
            graphics.fill(jobBarX, jobBarY, jobBarX + (int) (jobBarWidth * jobFraction), jobBarY + 4, color);

            String status = job.finished() ? job.describe() : (hovered ? "点击取消 · " + job.describe() : job.describe());
            int statusColor = job.status() == DownloadJob.Status.FAILED ? UiKit.COLOR_ERROR : UiKit.COLOR_DIM;
            UiKit.drawTrimmed(graphics, status, width - 140, y + 8, 130, statusColor);
        }

        if (shown.isEmpty()) {
            UiKit.centered(graphics, "§7当前没有进行中的任务", width / 2, height / 2, 0xFFFFFFFF);
        }

        if (!busy) {
            long done = jobs.stream().filter(j -> j.status() == DownloadJob.Status.DONE).count();
            long failed = jobs.stream().filter(j -> j.status() == DownloadJob.Status.FAILED).count();
            long cancelled = jobs.stream().filter(j -> j.status() == DownloadJob.Status.CANCELLED).count();
            String summary = "完成 " + done + " · 失败 " + failed + " · 已取消 " + cancelled;
            graphics.text(font, summary, 10, height - 44, UiKit.COLOR_TEXT, false);
            if (PackHub.hotLoad().pendingRestart().size() > 0) {
                graphics.text(font, "有 " + PackHub.hotLoad().pendingRestart().size()
                        + " 个模组需要重启才生效：点右下角「软重启并载入」", 10, height - 56, UiKit.COLOR_WARN, false);
            }
        } else {
            graphics.text(font, "§7提示：点击某一行可以只取消那一个任务", 10, height - 44, UiKit.COLOR_DIM, false);
        }

        cancelAllButton.active = busy;
        restartButton.active = !busy && PackHub.hotLoad().pendingRestart().size() > 0;
        closeButton.setMessage(Component.literal(busy ? "后台运行" : "完成"));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        double mouseX = UiKit.eventX(event);
        double mouseY = UiKit.eventY(event);
        int button = UiKit.eventButton(event);
        if (button == 0) {
            int rowY = 56;
            int rowHeight = 26;
            List<DownloadJob> jobs = PackHub.downloads().jobs();
            int index = (int) ((mouseY - rowY) / rowHeight);
            if (mouseY >= rowY && index >= 0 && index < jobs.size()) {
                DownloadJob job = jobs.get(Math.min(index, jobs.size() - 1));
                if (!job.finished() && PackHub.downloads().cancelJob(job.id)) {
                    Toasts.show("PackHub", "已取消：" + job.fileName);
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubled);
    }

    /** 给外部用：安装完成的通知（也用于把最近一次结果记进界面）。 */
    public void onInstalled(InstallResult result) {
        lastActiveAt = System.currentTimeMillis();
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}
