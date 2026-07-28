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
 * Client → Server: the Group tab's buttons ("new group", "add/remove member", "delegate",
 * "chat to all") all funnel through this one payload — mirrors how the Settings tab's many forms
 * still go through a handful of request payloads rather than one-per-button. The server applies
 * the action to {@link GroupManager}, then replies with a fresh {@link GroupSyncPayload} so the
 * panel repaints from the new truth (same request/refresh shape as
 * {@link RequestInventoryPayload} → {@link NumenInventoryPayload}).
 *
 * <p>CHAT and DELEGATE are handled by {@link GroupMessaging}: it pushes a "principal" {@code
 * <event>} to the target companion's own agent loop (opens a reasoning turn immediately, like a
 * live human speaking). The line is also recorded once via {@link GroupManager#recordChatLine} so
 * the Group tab's own transcript panel can show it — nothing is echoed into vanilla chat.
 */
public record GroupActionPayload(Action action, String groupName, UUID target, String text)
        implements NumenPayload {

    public enum Action { CREATE, DISBAND, ADD_MEMBER, REMOVE_MEMBER, DELEGATE, CHAT }

    /** Group names are player-typed; keep them short and sane (mirrors SummonRequestPayload's name cap). */
    public static final int MAX_NAME = 32;
    public static final int MAX_TEXT = 256;

    private static final UUID NO_TARGET = new UUID(0L, 0L);

    public static final ResourceLocation ID = new ResourceLocation(Constants.MOD_ID, "group_action");

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeEnum(action);
        buf.writeUtf(groupName, MAX_NAME);
        buf.writeUUID(target == null ? NO_TARGET : target);
        buf.writeUtf(text == null ? "" : text, MAX_TEXT);
    }

    public static GroupActionPayload read(FriendlyByteBuf buf) {
        Action action = buf.readEnum(Action.class);
        String groupName = buf.readUtf(MAX_NAME);
        UUID target = buf.readUUID();
        String text = buf.readUtf(MAX_TEXT);
        return new GroupActionPayload(action, groupName, NO_TARGET.equals(target) ? null : target, text);
    }

    /** Server main thread. */
    public static void handle(GroupActionPayload p, ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        GroupManager mgr = GroupManager.instance();
        UUID owner = player.getUUID();
        switch (p.action()) {
            case CREATE -> mgr.createGroup(p.groupName(), owner);
            case DISBAND -> {
                GroupManager.GroupInfo g = mgr.getGroup(p.groupName());
                if (g != null && g.owner().equals(owner)) mgr.disbandGroup(p.groupName());
            }
            case ADD_MEMBER -> {
                if (p.target() == null) break;
                NumenPlayer body = NumenPlayer.findByUuid(server, p.target());
                GroupManager.GroupInfo g = mgr.getGroup(p.groupName());
                if (body != null && g != null && g.owner().equals(owner) && body.isOwnedByPlayer(owner)) {
                    mgr.addMember(p.groupName(), p.target(), body.getName().getString());
                }
            }
            case REMOVE_MEMBER -> {
                GroupManager.GroupInfo g = mgr.getGroup(p.groupName());
                if (g != null && g.owner().equals(owner) && p.target() != null) {
                    mgr.removeMember(p.groupName(), p.target());
                }
            }
            case DELEGATE -> {
                if (p.target() == null || p.text() == null || p.text().isBlank()) break;
                GroupManager.GroupInfo dg = mgr.getGroup(p.groupName());
                if (dg == null || !dg.owner().equals(owner)) break;   // group must belong to the caller
                NumenPlayer target = NumenPlayer.findByUuid(server, p.target());
                if (target != null && target.isOwnedByPlayer(owner)) {
                    GroupMessaging.deliver(target, p.text(), true);
                    mgr.recordChatLine(p.groupName(), "主人", target.getName().getString(), p.text(), true);
                }
            }
            case CHAT -> {
                GroupManager.GroupInfo g = mgr.getGroup(p.groupName());
                if (g == null || !g.owner().equals(owner) || p.text() == null || p.text().isBlank()) break;
                for (UUID memberUuid : g.members().keySet()) {
                    NumenPlayer member = NumenPlayer.findByUuid(server, memberUuid);
                    if (member != null) {
                        GroupMessaging.deliver(member, p.text(), false);
                    }
                }
                mgr.recordChatLine(p.groupName(), "主人", null, p.text(), false);
            }
        }
        GroupSyncPayload.sendTo(player);
    }
}
