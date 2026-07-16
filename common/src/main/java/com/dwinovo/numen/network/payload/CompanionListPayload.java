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
        // Which companions were already known — so we can spot the ones this snapshot just added.
        java.util.Set<UUID> before = new java.util.HashSet<>();
        for (NumenRoster.Entry e : NumenRoster.instance().entries()) before.add(e.uuid());

        java.util.List<NumenRoster.Entry> snapshot = new java.util.ArrayList<>();
        for (Entry e : p.companions()) {
            snapshot.add(new NumenRoster.Entry(e.uuid(), e.name()));
        }
        NumenRoster.instance().replaceAll(snapshot);

        // A newly-arrived companion may have a persona the owner picked at summon (resolved by name here,
        // since the UUID wasn't known client-side until now). Apply it as the starting persona.
        for (Entry e : p.companions()) {
            if (before.contains(e.uuid())) continue;   // not new
            var persona = com.dwinovo.numen.persona.PersonaLibrary.takePendingSummon(e.name());
            if (persona != null) {
                com.dwinovo.numen.client.agent.AgentLoopRegistry.getOrCreate(e.uuid())
                        .setInitialPersona(persona.id(), persona.text(), persona.name());
            }
            // Same resolution for the provider entry picked at summon (selection is
            // mandatory in the summon panel, so a new companion always carries one).
            String providerEntry = com.dwinovo.numen.agent.llm.ProviderLibrary.takePendingSummon(e.name());
            if (providerEntry != null) {
                com.dwinovo.numen.client.agent.AgentLoopRegistry.getOrCreate(e.uuid())
                        .setProviderEntry(providerEntry);
            }
            // And for the voice entry picked at summon (optional — none pended = silent).
            String voiceEntry = com.dwinovo.numen.client.voice.VoiceLibrary.takePendingSummon(e.name());
            if (voiceEntry != null) {
                com.dwinovo.numen.client.voice.VoiceLibrary.instance().assign(e.uuid(), voiceEntry);
            }
        }
    }
}
