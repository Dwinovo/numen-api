package com.dwinovo.numen.platform.services;

import com.dwinovo.numen.client.voice.EntityVoiceSound;
import com.dwinovo.numen.client.voice.PcmAudio;
import com.dwinovo.numen.client.voice.VoicePreviewSound;
import net.minecraft.client.player.AbstractClientPlayer;

import java.util.UUID;

/**
 * 语音声音实例的平台工厂。语音的 PCM 取数在两个 loader 上走不同机制,
 * 所以实例创建必须分家(两侧都是覆写取数钩子的子类,零自有 mixin):
 * <ul>
 *   <li><b>Forge(1.20.1)</b> — Forge 补丁把 {@code SoundEngine.play} 的取数
 *       改成调用 {@code SoundInstance.getStream(SoundBufferLibrary, Sound, boolean)}
 *       补丁钩子(vanilla 形状的 {@code SoundBufferLibrary.getStream} INVOKE
 *       在其运行时字节码里不存在,@Redirect 会 0 目标掀桌)。该侧返回覆写了
 *       这个钩子的子类({@code ForgeEntityVoiceSound} / {@code ForgeVoicePreviewSound});</li>
 *   <li><b>Fabric</b> — Fabric API 的 sound-api-v1 自带同一取数点的重定向
 *       (重定向到接口注入的 {@code FabricSoundInstance.getAudioStream}),
 *       自己再上同目标 @Redirect 必与之冲突。该侧返回覆写了 {@code getAudioStream}
 *       钩子的子类({@code FabricEntityVoiceSound} / {@code FabricVoicePreviewSound})。</li>
 * </ul>
 * 客户端专用(声音引擎只存在于客户端);服务端永远不该调这两个方法。
 */
public interface IVoiceSoundFactory {

    /** 挂在同伴身上的一句 3D 空间语音(跟随实体、距离衰减)。 */
    EntityVoiceSound entityVoice(UUID entityUuid, AbstractClientPlayer body, PcmAudio audio, float volume);

    /** 设置界面试听的一句 2D 就地播放(不挂实体、无衰减)。 */
    VoicePreviewSound previewVoice(PcmAudio audio, float volume);
}
