package com.dwinovo.numen.network.payload;

import com.dwinovo.numen.Constants;
import com.dwinovo.numen.network.NumenPayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Client → Server: "show me my current groups." No fields — the Group tab sends this once when
 * opened (mirrors {@link RequestInventoryPayload}'s on-demand-fetch shape, just with nothing to
 * key by since groups are a per-owner, not per-companion, view).
 */
public record RequestGroupsPayload() implements NumenPayload {

    public static final RequestGroupsPayload INSTANCE = new RequestGroupsPayload();

    public static final ResourceLocation ID = new ResourceLocation(Constants.MOD_ID, "request_groups");

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        // no fields
    }

    public static RequestGroupsPayload read(FriendlyByteBuf buf) {
        return INSTANCE;
    }

    /** Server main thread. */
    public static void handle(RequestGroupsPayload p, ServerPlayer player) {
        GroupSyncPayload.sendTo(player);
    }
}
