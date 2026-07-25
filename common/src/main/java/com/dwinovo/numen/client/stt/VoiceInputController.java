package com.dwinovo.numen.client.stt;

import com.dwinovo.numen.data.ModLanguageData;
import com.dwinovo.numen.platform.services.INumenConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;

import java.util.function.Consumer;

/**
 * 麦克风按钮的胶水:{@link #toggle} 一下开录、再一下停。串起
 * {@link SttProviders#fromConfig}(建后端)、{@link MicrophoneManager}(采集)、
 * {@link SttListener}(回结果)。转写文本经 {@code onText} 刷输入框——批量在结尾
 * 一次刷,流式边说边刷,按钮不关心是哪种。所有 UI 回调切回客户端主线程。
 */
public final class VoiceInputController {

    private static volatile SttSession session;
    private static volatile boolean active;

    private VoiceInputController() {}

    public static boolean isActive() {
        return active || MicrophoneManager.isRecording();
    }

    /**
     * 切换录音。{@code onText} 收到(增量/最终)转写文本刷输入框;{@code onStatus}
     * 收到状态/错误提示(如未配置、无麦克风、请求失败)。
     */
    public static synchronized void toggle(INumenConfig cfg, Consumer<String> onText, Consumer<String> onStatus) {
        if (isActive()) {
            stop();
            return;
        }
        start(cfg, onText, onText, onStatus);
    }

    /**
     * 开始一次语音输入。流式临时文字只给 {@code onPartial}，服务端正式收尾后只调用一次
     * {@code onFinal}。全局 PTT 由此把最终文本直接送给伙伴，而聊天面板仍可把两者都刷进输入框。
     *
     * @return true 表示麦克风已经开始采集；false 表示已有会话或配置/设备不可用
     */
    public static synchronized boolean start(INumenConfig cfg,
                                             Consumer<String> onPartial,
                                             Consumer<String> onFinal,
                                             Consumer<String> onStatus) {
        if (isActive()) {
            return false;
        }
        SttBackend backend = SttProviders.fromConfig(cfg);
        if (backend == null) {
            onStatus.accept(I18n.get(ModLanguageData.Keys.STT_NOT_CONFIGURED));
            return false;
        }
        active = true;
        SttSession s = backend.open(new SttListener() {
            @Override
            public void onPartial(String text) {
                onMain(() -> onPartial.accept(text));
            }

            @Override
            public void onFinal(String text) {
                onMain(() -> {
                    onFinal.accept(text);
                    active = false;
                    session = null;
                });
            }

            @Override
            public void onError(Throwable error) {
                onMain(() -> {
                    onStatus.accept(I18n.get(ModLanguageData.Keys.STT_FAILED, rootMessage(error)));
                    active = false;
                    session = null;
                });
            }
        });
        if (!active) {
            s.cancel();
            return false;
        }
        session = s;
        boolean started = MicrophoneManager.start(cfg.getSttMicrophone(), s::feed, s::finish);
        if (!started) {
            s.cancel();
            session = null;
            active = false;
            onStatus.accept(I18n.get(ModLanguageData.Keys.STT_NO_MIC));
            return false;
        }
        return true;
    }

    /** 松开 PTT / 再点麦克风：停止采集，但保持会话到云端返回最终结果。 */
    public static synchronized void stop() {
        if (MicrophoneManager.isRecording()) {
            MicrophoneManager.stop();
        }
    }

    /** 退出世界等场景的硬取消：不提交识别结果。 */
    public static synchronized void cancel() {
        MicrophoneManager.stop();
        SttSession current = session;
        session = null;
        active = false;
        if (current != null) {
            current.cancel();
        }
    }

    private static void onMain(Runnable r) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.execute(r);
        } else {
            r.run();
        }
    }

    private static String rootMessage(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null && c.getCause() != c) {
            c = c.getCause();
        }
        String m = c.getMessage();
        return m == null || m.isBlank() ? c.getClass().getSimpleName() : m;
    }
}
