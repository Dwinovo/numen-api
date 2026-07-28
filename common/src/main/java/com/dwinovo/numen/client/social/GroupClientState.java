package com.dwinovo.numen.client.social;

import com.dwinovo.numen.network.payload.GroupSyncPayload;

import java.util.List;
import java.util.UUID;

/**
 * Latest {@link GroupSyncPayload} the client has received, held for {@code GroupView} to render.
 * Single static snapshot (like {@code NumenRoster}) — groups are a per-owner view, not
 * per-companion, so there's exactly one list regardless of which companion tab is focused.
 */
public final class GroupClientState {

    private static volatile List<GroupSyncPayload.GroupDto> groups = List.of();

    private GroupClientState() {}

    public static List<GroupSyncPayload.GroupDto> groups() {
        return groups;
    }

    public static void set(List<GroupSyncPayload.GroupDto> snapshot) {
        groups = snapshot == null ? List.of() : List.copyOf(snapshot);
    }

    public static GroupSyncPayload.GroupDto groupOf(UUID memberUuid) {
        for (GroupSyncPayload.GroupDto g : groups) {
            for (GroupSyncPayload.MemberDto m : g.members()) {
                if (m.uuid().equals(memberUuid)) {
                    return g;
                }
            }
        }
        return null;
    }
}
