package com.dwinovo.numen.network.payload;

import com.dwinovo.numen.Constants;
import com.dwinovo.numen.entity.NumenPlayer;
import com.dwinovo.numen.network.NumenPayload;
import com.dwinovo.numen.social.GroupManager;
import com.dwinovo.numen.social.GroupMessaging;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Client → Server: {@code from}'s agent loop just finished a turn with a text reply while it was
 * a member of a group (checked client-side via {@code GroupClientState.groupOf}, since group
 * membership is synced there already). The server relays the same line to every OTHER member of
 * that group and records it in the group's transcript ({@link GroupManager#recordChatLine}) —
 * this is what makes two companions actually carry on a conversation with each other in the
 * Group tab, rather than only ever hearing from the owner (see {@link GroupActionPayload} for the
 * owner→group direction).
 *
 * <p>Fires once per finished reply from a grouped companion, regardless of whether that reply
 * was actually about group business — a companion privately chatting with the owner about
 * something unrelated still relays if it happens to be in a group. Acceptable trade-off for now;
 * tightening it to "only relay replies that were themselves prompted by a group event" would need
 * a turn-provenance flag that doesn't exist yet.
 */
public record GroupRelayPayload(UUID from, String text) implements NumenPayload {

    public static final int MAX_TEXT = 1024;

    public static final ResourceLocation ID = new ResourceLocation(Constants.MOD_ID, "group_relay");

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(from);
        buf.writeUtf(text, MAX_TEXT);
    }

    public static GroupRelayPayload read(FriendlyByteBuf buf) {
        return new GroupRelayPayload(buf.readUUID(), buf.readUtf(MAX_TEXT));
    }

    /** Server main thread. */
    public static void handle(GroupRelayPayload p, ServerPlayer owner) {
        MinecraftServer server = owner.getServer();
        if (server == null || p.text() == null || p.text().isBlank()) return;
        NumenPlayer from = NumenPlayer.findByUuid(server, p.from());
        if (from == null || !from.isOwnedByPlayer(owner.getUUID())) return;   // spoofed uuid guard
        GroupManager mgr = GroupManager.instance();
        String groupName = mgr.groupOf(p.from());
        if (groupName == null) return;
        GroupManager.GroupInfo g = mgr.getGroup(groupName);
        if (g == null || !g.owner().equals(owner.getUUID())) return;
        boolean delivered = false;
        for (UUID memberUuid : g.members().keySet()) {
            if (memberUuid.equals(p.from())) continue;   // don't echo a companion's line back to itself
            NumenPlayer to = NumenPlayer.findByUuid(server, memberUuid);
            if (to != null) {
                GroupMessaging.relay(from, to, p.text());
                delivered = true;
            }
        }
        if (delivered) {
            mgr.recordChatLine(groupName, from.getName().getString(), null, p.text(), false);
            GroupSyncPayload.sendTo(owner);
        }
    }
}
