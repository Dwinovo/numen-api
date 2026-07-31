package com.dwinovo.numen.network.payload;

import com.dwinovo.numen.Constants;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Server → Client:某个同伴的头顶气泡状态(见 {@link SpeechBubblePayload}
 * 的上行说明)。收到的客户端不一定是主人——附近路过的玩家同样收到,
 * 同伴说话路人也听得见。
 */
public record SpeechBubbleSyncPayload(UUID entityUuid, byte kind, String text) implements CustomPacketPayload {

    public static final ResourceLocation ID = new ResourceLocation(Constants.MOD_ID, "speech_bubble_sync");

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(entityUuid);
        buf.writeByte(kind);
        buf.writeUtf(text, SpeechBubblePayload.MAX_TEXT);
    }

    public static SpeechBubbleSyncPayload read(FriendlyByteBuf buf) {
        return new SpeechBubbleSyncPayload(buf.readUUID(), buf.readByte(),
                buf.readUtf(SpeechBubblePayload.MAX_TEXT));
    }

    /** Client main thread. */
    public static void handle(SpeechBubbleSyncPayload p) {
        com.dwinovo.numen.client.hud.SpeechBubbles.apply(p.entityUuid(), p.kind(), p.text());
    }
}
