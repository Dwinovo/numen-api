package com.dwinovo.numen.client.stt;

import com.dwinovo.numen.Constants;
import com.dwinovo.numen.api.NumenGateway;
import com.dwinovo.numen.client.agent.AgentLoopRegistry;
import com.dwinovo.numen.client.agent.NumenRoster;
import com.dwinovo.numen.platform.Services;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.UUID;

/**
 * 世界画面中的全局按住说话控制器。按下时开始采集，松开时结束；最终转写不经过聊天框，
 * 直接走 {@link NumenGateway} 的正式入站 API，进入伙伴自己的 LLM 会话。
 */
public final class GlobalPushToTalk {

    private static boolean wasDown;
    private static UUID preferredCompanion;
    private static UUID recordingTarget;
    private static String recordingName;

    private GlobalPushToTalk() {}

    /** Numen 面板打开/切换伙伴时记住目标，供关闭面板后的 V 键使用。 */
    public static void rememberCompanion(UUID uuid) {
        if (uuid != null) {
            preferredCompanion = uuid;
        }
    }

    /** 每客户端 tick 调用；{@code down} 是可改键的 PTT KeyMapping 当前状态。 */
    public static void tick(boolean down) {
        Minecraft mc = Minecraft.getInstance();
        if (down && !wasDown && mc.player != null && mc.screen == null) {
            begin();
        } else if (!down && wasDown && recordingTarget != null) {
            VoiceInputController.stop();
            actionBar("正在识别…");
        }
        wasDown = down;
    }

    private static void begin() {
        List<NumenRoster.Entry> entries = NumenRoster.instance().entries();
        UUID target = selectTarget(entries, preferredCompanion);
        if (target == null) {
            actionBar("没有可用的 Numen 伙伴");
            return;
        }
        NumenRoster.Entry selected = entry(entries, target);
        String name = selected == null || selected.name() == null || selected.name().isBlank()
                ? "伙伴" : selected.name();

        var loop = AgentLoopRegistry.getOrCreate(target);
        String endpointProblem = loop.endpointProblem();
        if (endpointProblem != null) {
            actionBar(endpointProblem);
            return;
        }

        recordingTarget = target;
        recordingName = name;
        boolean started = VoiceInputController.start(
                Services.CONFIG,
                GlobalPushToTalk::showPartial,
                text -> submitFinal(target, name, text),
                GlobalPushToTalk::showFailure);
        if (started) {
            Constants.LOG.info("[numen-ptt] recording started for {} ({})", name, target);
            actionBar("按住说话：" + name);
        } else {
            clearRecording();
        }
    }

    private static void showPartial(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        actionBar("🎙 " + clip(text, 60));
    }

    private static void submitFinal(UUID target, String name, String text) {
        String finalText = text == null ? "" : text.strip();
        clearRecording();
        if (finalText.isEmpty()) {
            Constants.LOG.info("[numen-ptt] recognition finished with no speech");
            actionBar("没有识别到语音");
            return;
        }
        Constants.LOG.info("[numen-ptt] recognition final ({} chars), submitting to {} ({})",
                finalText.length(), name, target);
        if (NumenGateway.enqueue(target, finalText)) {
            actionBar("已发送给 " + name + "：" + clip(finalText, 50));
        } else {
            Constants.LOG.warn("[numen-ptt] submit failed — target loop unavailable: {}", target);
            actionBar("发送失败：" + name + " 当前不可用");
        }
    }

    private static void showFailure(String status) {
        Constants.LOG.warn("[numen-ptt] {}", status);
        clearRecording();
        actionBar(status);
    }

    /** 退出世界/断线时取消在途录音，避免识别结果落进下一次会话。 */
    public static void reset() {
        VoiceInputController.cancel();
        wasDown = false;
        preferredCompanion = null;
        clearRecording();
    }

    static UUID selectTarget(List<NumenRoster.Entry> entries, UUID preferred) {
        if (entries == null || entries.isEmpty()) {
            return null;
        }
        if (preferred != null) {
            for (NumenRoster.Entry candidate : entries) {
                if (preferred.equals(candidate.uuid())) {
                    return preferred;
                }
            }
        }
        return entries.get(0).uuid();
    }

    private static NumenRoster.Entry entry(List<NumenRoster.Entry> entries, UUID uuid) {
        for (NumenRoster.Entry candidate : entries) {
            if (uuid.equals(candidate.uuid())) {
                return candidate;
            }
        }
        return null;
    }

    private static void clearRecording() {
        recordingTarget = null;
        recordingName = null;
    }

    private static void actionBar(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && text != null && !text.isBlank()) {
            mc.player.displayClientMessage(Component.literal(text), true);
        }
    }

    private static String clip(String text, int max) {
        String value = text == null ? "" : text.replaceAll("\\s+", " ").strip();
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }
}
