package com.dwinovo.numen.network.payload;

import com.dwinovo.numen.Constants;
import com.dwinovo.numen.client.data.ClientNumenInventory;
import net.minecraft.network.FriendlyByteBuf;
import com.dwinovo.numen.network.NumenPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Server → Client: a companion's 36 main backpack slots, answering
 * {@link RequestInventoryPayload}. {@code loaded=false} means the body is asleep
 * in unloaded chunks (or not the requester's) — no contents. Dropped into
 * {@link ClientNumenInventory} for the Items tab to render read-only.
 */
public record NumenInventoryPayload(UUID uuid, boolean loaded, List<ItemStack> items,
                                    List<ItemStack> craft, int foodLevel, float saturation)
        implements NumenPayload {

    /** Cap defends against absurd input. */
    public static final int MAX_ITEMS = 256;

    public static final ResourceLocation ID = new ResourceLocation(Constants.MOD_ID, "numen_inventory");

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(uuid);
        buf.writeBoolean(loaded);
        writeItems(buf, items);
        writeItems(buf, craft);
        buf.writeVarInt(foodLevel);
        buf.writeFloat(saturation);
    }

    public static NumenInventoryPayload read(FriendlyByteBuf buf) {
        UUID uuid = buf.readUUID();
        boolean loaded = buf.readBoolean();
        List<ItemStack> items = readItems(buf);
        List<ItemStack> craft = readItems(buf);
        int foodLevel = buf.readVarInt();
        float saturation = buf.readFloat();
        return new NumenInventoryPayload(uuid, loaded, items, craft, foodLevel, saturation);
    }

    // FriendlyByteBuf#writeItem / #readItem already encode empty stacks (a leading present-flag),
    // so this is the 1.20.4 equivalent of ItemStack.OPTIONAL_LIST_STREAM_CODEC.
    private static void writeItems(FriendlyByteBuf buf, List<ItemStack> list) {
        int n = Math.min(list.size(), MAX_ITEMS);
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            buf.writeItem(list.get(i));
        }
    }

    private static List<ItemStack> readItems(FriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), MAX_ITEMS);
        List<ItemStack> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(buf.readItem());
        }
        return list;
    }

    /** Client main thread. */
    public static void handle(NumenInventoryPayload p) {
        ClientNumenInventory.update(p.uuid(), new ClientNumenInventory.Snapshot(
                p.loaded(), p.items(), p.craft(), p.foodLevel(), p.saturation(), System.currentTimeMillis()));
    }
}
