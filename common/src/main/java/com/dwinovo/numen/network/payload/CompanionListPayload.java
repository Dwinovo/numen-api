package com.dwinovo.numen.network.payload;

import com.dwinovo.numen.Constants;
import com.dwinovo.numen.client.agent.NumenRoster;
import net.minecraft.network.FriendlyByteBuf;
import com.dwinovo.numen.network.NumenPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Server → Client: the roster of companions this player owns (UUID + name).
 * Pushed on owner login (after their dormant companions respawn) and right after
 * a fresh summon, so the client's {@link NumenRoster} panel always reflects the truth.
 */
public record CompanionListPayload(List<Entry> companions) implements NumenPayload {

    /** Cap defends against absurd input; nobody owns hundreds of companions. */
    public static final int MAX = 64;

    /** One companion's roster line. */
    public record Entry(UUID uuid, String name) {}

    public static final ResourceLocation ID = new ResourceLocation(Constants.MOD_ID, "companion_list");

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        int n = Math.min(companions.size(), MAX);
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            Entry e = companions.get(i);
            buf.writeUUID(e.uuid());
            buf.writeUtf(e.name(), 256);
        }
    }

    public static CompanionListPayload read(FriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), MAX);
        List<Entry> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(new Entry(buf.readUUID(), buf.readUtf(256)));
        }
        return new CompanionListPayload(list);
    }

    /** Client-side handler. Runs on the client main thread (network layer arranges that). */
    public static void handle(CompanionListPayload p) {
        java.util.List<NumenRoster.Entry> snapshot = new java.util.ArrayList<>();
        for (Entry e : p.companions()) {
            snapshot.add(new NumenRoster.Entry(e.uuid(), e.name()));
        }
        NumenRoster.instance().replaceAll(snapshot);
    }
}
