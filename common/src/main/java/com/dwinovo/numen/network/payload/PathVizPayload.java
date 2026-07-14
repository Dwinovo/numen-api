package com.dwinovo.numen.network.payload;

import com.dwinovo.numen.Constants;
import com.dwinovo.numen.client.path.ClientPathViz;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import com.dwinovo.numen.network.NumenPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Server → Client: the companion's current pathfinding plan, for the in-world
 * path overlay. The path is computed and owned on the server (the client has
 * no planner state of its own to render from), so the body pushes the plan to
 * the owner whenever it (re)plans a segment, and pushes an EMPTY one (all
 * lists empty) to clear the overlay when the path ends.
 *
 * <ul>
 *   <li>{@code nodes} — the path positions (feet cells); drawn as a red poly-line.</li>
 *   <li>{@code toBreak} — blocks the path will dig; drawn as red boxes.</li>
 *   <li>{@code toPlace} — scaffold blocks the path will place; drawn as green boxes.</li>
 *   <li>{@code targets} — the goal cell(s); drawn as green boxes.</li>
 * </ul>
 */
public record PathVizPayload(UUID companion,
                             ResourceLocation dimension,
                             List<BlockPos> nodes,
                             List<BlockPos> toBreak,
                             List<BlockPos> toPlace,
                             List<BlockPos> targets) implements NumenPayload {

    /** Cap per list — paths are trimmed well below this; defends against absurd input. */
    public static final int MAX = 512;

    public static final ResourceLocation ID = new ResourceLocation(Constants.MOD_ID, "path_viz");

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(companion);
        buf.writeResourceLocation(dimension);
        writeList(buf, nodes);
        writeList(buf, toBreak);
        writeList(buf, toPlace);
        writeList(buf, targets);
    }

    public static PathVizPayload read(FriendlyByteBuf buf) {
        UUID companion = buf.readUUID();
        ResourceLocation dimension = buf.readResourceLocation();
        return new PathVizPayload(companion, dimension,
                readList(buf), readList(buf), readList(buf), readList(buf));
    }

    private static void writeList(FriendlyByteBuf buf, List<BlockPos> list) {
        int n = Math.min(list.size(), MAX);
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            buf.writeBlockPos(list.get(i));
        }
    }

    private static List<BlockPos> readList(FriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), MAX);
        List<BlockPos> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(buf.readBlockPos());
        }
        return list;
    }

    /** Client-side handler. Runs on the client main thread (network layer arranges that). */
    public static void handle(PathVizPayload p) {
        ClientPathViz.accept(p);
    }
}
