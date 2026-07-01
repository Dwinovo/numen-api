package com.dwinovo.numen.network.payload;

import com.dwinovo.numen.Constants;
import com.dwinovo.numen.client.agent.AgentLoopRegistry;
import net.minecraft.network.FriendlyByteBuf;
import com.dwinovo.numen.network.NumenPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Server → Client: an Numen body died for good. The owner's client-side
 * {@link com.dwinovo.numen.client.agent.EntityAgentLoop} must stop — a dead
 * companion has no body to act through, so it must never call the LLM again.
 *
 * <h2>Death is recoverable (not disposed)</h2>
 * The companion respawns at its owner after a delay (see {@link NumenRespawnPayload}), so the loop
 * is SUSPENDED, not disposed. {@code cause} is the vanilla death message for that tool result.
 */
public record NumenDeathPayload(UUID entityUuid, String cause, long respawnDelayMs)
        implements NumenPayload {

    public static final ResourceLocation ID = new ResourceLocation(Constants.MOD_ID, "numen_death");

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(entityUuid);
        buf.writeUtf(cause);
        buf.writeVarLong(respawnDelayMs);
    }

    public static NumenDeathPayload read(FriendlyByteBuf buf) {
        return new NumenDeathPayload(buf.readUUID(), buf.readUtf(), buf.readVarLong());
    }

    /** Client-side handler. Runs on the client main thread (network layer arranges that). */
    public static void handle(NumenDeathPayload p) {
        Constants.LOG.info("[numen-net] numen_death entity={} ({}) — suspending loop", p.entityUuid(), p.cause());
        AgentLoopRegistry.get(p.entityUuid()).ifPresent(loop -> loop.onEntityDied(p.cause()));
        // Keep it in the roster (marked dead) so the HUD / rail can show the respawn countdown;
        // it goes live again on NumenRespawnPayload.
        com.dwinovo.numen.client.agent.ClientDeaths.markDead(
                p.entityUuid(), System.currentTimeMillis() + p.respawnDelayMs());
    }
}
