package com.dwinovo.numen.network.payload;

import com.dwinovo.numen.Constants;
import com.dwinovo.numen.client.agent.AgentLoopRegistry;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Server → Client: an asynchronous WORLD EVENT for a companion's brain (dimension change, a hazard,
 * task wind-down, …). The server ships a ready-made {@code <event>} XML string; the client-side
 * INBOX decides consumption timing by the brain's state at arrival (mid-turn → next boundary;
 * background task running → immediate turn; fully idle → wait for the next turn). {@code principal}
 * marks "a live human is speaking" (bridge mods relaying danmaku / QQ messages). {@code wakeIdle}
 * is separate delivery authority granted ahead of time by the agent/owner (for example, a one-shot
 * scheduled reminder). Both may open a turn from full idle; plain world events leave both false.
 */
public record NumenEventPayload(UUID entityUuid, String xml, boolean principal,
                                boolean wakeIdle) implements CustomPacketPayload {

    /** Source-compatible ambient/principal constructor for existing event producers. */
    public NumenEventPayload(UUID entityUuid, String xml, boolean principal) {
        this(entityUuid, xml, principal, false);
    }

    public static final Type<NumenEventPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "numen_event"));

    public static final StreamCodec<RegistryFriendlyByteBuf, NumenEventPayload> STREAM_CODEC =
            StreamCodec.composite(
                    UUIDUtil.STREAM_CODEC, NumenEventPayload::entityUuid,
                    ByteBufCodecs.STRING_UTF8, NumenEventPayload::xml,
                    ByteBufCodecs.BOOL, NumenEventPayload::principal,
                    ByteBufCodecs.BOOL, NumenEventPayload::wakeIdle,
                    NumenEventPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Client-side handler. Runs on the client main thread (network layer arranges that). */
    public static void handle(NumenEventPayload p) {
        // A principal message or an authorized idle wake is allowed to start a turn. After a
        // reconnect the client-side registry is empty, so a read-only lookup would silently drop
        // exactly the packet that is supposed to wake the restored conversation. Ambient facts
        // remain lazy: without an existing loop they must not create a brain on their own.
        if (p.principal() || p.wakeIdle()) {
            AgentLoopRegistry.getOrCreate(p.entityUuid())
                    .pushEvent(p.xml(), p.principal(), p.wakeIdle());
            return;
        }
        AgentLoopRegistry.get(p.entityUuid()).ifPresent(loop ->
                loop.pushEvent(p.xml(), p.principal(), p.wakeIdle()));
    }
}
