package com.dwinovo.numen.network.payload;

import com.dwinovo.numen.Constants;
import com.dwinovo.numen.client.social.GroupClientState;
import com.dwinovo.numen.network.NumenPayload;
import com.dwinovo.numen.social.GroupManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server → Client: a full replacement snapshot of the owner's groups (name, members, live
 * status), same "push a complete replacement" convention as {@link CompanionListPayload}. Sent
 * after every {@link GroupActionPayload} and once on request, so the Group tab never shows stale
 * membership.
 */
public record GroupSyncPayload(List<GroupDto> groups) implements NumenPayload {

    public static final int MAX_GROUPS = 16;
    public static final int MAX_MEMBERS = 32;
    /** Only the tail of the log is worth shipping to the client — the Group tab's transcript
     *  panel can't show more than this many lines at once anyway. */
    public static final int MAX_CHAT_LINES = 20;

    public record MemberDto(UUID uuid, String name, String status, String detail) {}

    /** One transcript line — mirrors {@code GroupManager.ChatLine}. {@code target} empty = a
     *  broadcast CHAT line; non-empty = the delegate's name for a DELEGATE line. */
    public record ChatLineDto(String sender, String target, String text, boolean delegate) {}

    public record GroupDto(String name, List<MemberDto> members, List<ChatLineDto> chatLog) {}

    public static final ResourceLocation ID = new ResourceLocation(Constants.MOD_ID, "group_sync");

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        int n = Math.min(groups.size(), MAX_GROUPS);
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            GroupDto g = groups.get(i);
            buf.writeUtf(g.name(), GroupActionPayload.MAX_NAME);
            int m = Math.min(g.members().size(), MAX_MEMBERS);
            buf.writeVarInt(m);
            for (int j = 0; j < m; j++) {
                MemberDto e = g.members().get(j);
                buf.writeUUID(e.uuid());
                buf.writeUtf(e.name(), 256);
                buf.writeUtf(e.status(), 64);
                buf.writeUtf(e.detail() == null ? "" : e.detail(), 256);
            }
            int c = Math.min(g.chatLog().size(), MAX_CHAT_LINES);
            buf.writeVarInt(c);
            for (int j = 0; j < c; j++) {
                ChatLineDto line = g.chatLog().get(j);
                buf.writeUtf(line.sender(), 256);
                buf.writeUtf(line.target() == null ? "" : line.target(), 256);
                buf.writeUtf(line.text(), GroupActionPayload.MAX_TEXT);
                buf.writeBoolean(line.delegate());
            }
        }
    }

    public static GroupSyncPayload read(FriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), MAX_GROUPS);
        List<GroupDto> groups = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            String name = buf.readUtf(GroupActionPayload.MAX_NAME);
            int m = Math.min(buf.readVarInt(), MAX_MEMBERS);
            List<MemberDto> members = new ArrayList<>(m);
            for (int j = 0; j < m; j++) {
                members.add(new MemberDto(buf.readUUID(), buf.readUtf(256), buf.readUtf(64), buf.readUtf(256)));
            }
            int c = Math.min(buf.readVarInt(), MAX_CHAT_LINES);
            List<ChatLineDto> chatLog = new ArrayList<>(c);
            for (int j = 0; j < c; j++) {
                chatLog.add(new ChatLineDto(buf.readUtf(256), buf.readUtf(256),
                        buf.readUtf(GroupActionPayload.MAX_TEXT), buf.readBoolean()));
            }
            groups.add(new GroupDto(name, members, chatLog));
        }
        return new GroupSyncPayload(groups);
    }

    /** Build and send the current snapshot of {@code player}'s own groups. */
    public static void sendTo(ServerPlayer player) {
        GroupManager mgr = GroupManager.instance();
        List<GroupDto> out = new ArrayList<>();
        for (GroupManager.GroupInfo g : mgr.listGroups(player.getUUID()).values()) {
            Map<UUID, GroupManager.MemberStatus> statuses = mgr.getGroupMemberStatuses(g.name());
            List<MemberDto> members = new ArrayList<>();
            for (Map.Entry<UUID, String> m : g.members().entrySet()) {
                GroupManager.MemberStatus st = statuses.get(m.getKey());
                members.add(new MemberDto(m.getKey(), m.getValue(),
                        st == null ? "idle" : st.status(),
                        st == null || st.detail() == null ? "" : st.detail()));
            }
            List<GroupManager.ChatLine> log = mgr.getChatLog(g.name());
            List<ChatLineDto> chatLog = new ArrayList<>();
            int from = Math.max(0, log.size() - MAX_CHAT_LINES);
            for (GroupManager.ChatLine line : log.subList(from, log.size())) {
                chatLog.add(new ChatLineDto(line.sender(), line.target(), line.text(), line.delegate()));
            }
            out.add(new GroupDto(g.name(), members, chatLog));
        }
        com.dwinovo.numen.platform.Services.NETWORK.sendToPlayer(player, new GroupSyncPayload(out));
    }

    /** Client-side handler. Runs on the client main thread. */
    public static void handle(GroupSyncPayload p) {
        GroupClientState.set(p.groups());
    }
}
