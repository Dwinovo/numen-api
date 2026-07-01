package com.dwinovo.numen.network;

import com.dwinovo.numen.network.payload.NumenDeathPayload;
import com.dwinovo.numen.network.payload.NumenLocationsPayload;
import com.dwinovo.numen.network.payload.LocateNumenPayload;
import com.dwinovo.numen.network.payload.ClientUiActionPayload;
import com.dwinovo.numen.network.payload.CompanionListPayload;
import com.dwinovo.numen.network.payload.PathVizPayload;
import com.dwinovo.numen.platform.Services;

/**
 * Central registration hub for every {@link
 * com.dwinovo.numen.network.NumenPayload} the mod
 * declares. Each loader's mod-init code calls {@link #register} exactly once
 * during startup; the {@link Services#NETWORK} platform implementation handles
 * the loader-specific timing.
 *
 * <h2>Adding a new payload</h2>
 * <ol>
 *   <li>Define a record under {@code com.dwinovo.numen.network.payload}
 *       implementing {@code NumenPayload} (1.20.4 shape: {@code write} +
 *       {@code id}) with a public {@code ID} and a static {@code read}.</li>
 *   <li>Add one {@code registerClientToServer(...)} or
 *       {@code registerServerToClient(...)} call here.</li>
 * </ol>
 */
public final class NumenNetwork {

    private NumenNetwork() {}

    public static void register() {
        // S→C: an Numen body died; suspend the owner's agent loop (resolves the in-flight
        // tool call with the death cause). Recoverable — see NumenRespawnPayload.
        Services.NETWORK.registerServerToClient(
                NumenDeathPayload.ID, NumenDeathPayload::read,
                NumenDeathPayload::handle);

        // S→C: the dead companion has respawned at its owner; resume the suspended loop.
        Services.NETWORK.registerServerToClient(
                com.dwinovo.numen.network.payload.NumenRespawnPayload.ID,
                com.dwinovo.numen.network.payload.NumenRespawnPayload::read,
                com.dwinovo.numen.network.payload.NumenRespawnPayload::handle);

        // S→C: a generic async world event (dimension change, hazard, …) for a companion's brain.
        Services.NETWORK.registerServerToClient(
                com.dwinovo.numen.network.payload.NumenEventPayload.ID,
                com.dwinovo.numen.network.payload.NumenEventPayload::read,
                com.dwinovo.numen.network.payload.NumenEventPayload::handle);

        // S→C: the owner's companion roster (UUID + name), pushed on login + summon
        // so the client panel knows which fake players are its companions.
        Services.NETWORK.registerServerToClient(
                CompanionListPayload.ID, CompanionListPayload::read,
                CompanionListPayload::handle);

        // S→C: the companion's current pathfinding plan, for the in-world path
        // overlay (Baritone PathRenderer, ported to our server-authored path).
        Services.NETWORK.registerServerToClient(
                PathVizPayload.ID, PathVizPayload::read,
                PathVizPayload::handle);

        // S→C: server `/numen` verbs that must act on the caller's own client
        // (open settings GUI / reset conversations).
        Services.NETWORK.registerServerToClient(
                ClientUiActionPayload.ID, ClientUiActionPayload::read,
                ClientUiActionPayload::handle);

        // C→S: roster panel asks where its (possibly far / cross-dimension) pets are.
        Services.NETWORK.registerClientToServer(
                LocateNumenPayload.ID, LocateNumenPayload::read,
                LocateNumenPayload::handle);

        // S→C: locate answers — position/dimension/HP snapshots per pet.
        Services.NETWORK.registerServerToClient(
                NumenLocationsPayload.ID, NumenLocationsPayload::read,
                NumenLocationsPayload::handle);

        // C→S: the Items tab asks for a companion's backpack (not client-synced).
        Services.NETWORK.registerClientToServer(
                com.dwinovo.numen.network.payload.RequestInventoryPayload.ID,
                com.dwinovo.numen.network.payload.RequestInventoryPayload::read,
                com.dwinovo.numen.network.payload.RequestInventoryPayload::handle);

        // S→C: the requested backpack contents.
        Services.NETWORK.registerServerToClient(
                com.dwinovo.numen.network.payload.NumenInventoryPayload.ID,
                com.dwinovo.numen.network.payload.NumenInventoryPayload::read,
                com.dwinovo.numen.network.payload.NumenInventoryPayload::handle);

        // C→S: the panel's "+" button asks to summon a companion by name.
        Services.NETWORK.registerClientToServer(
                com.dwinovo.numen.network.payload.SummonRequestPayload.ID,
                com.dwinovo.numen.network.payload.SummonRequestPayload::read,
                com.dwinovo.numen.network.payload.SummonRequestPayload::handle);

        // C→S: the rail ✕ → confirm asks to permanently delete a companion (drops its inventory first).
        Services.NETWORK.registerClientToServer(
                com.dwinovo.numen.network.payload.DismissRequestPayload.ID,
                com.dwinovo.numen.network.payload.DismissRequestPayload::read,
                com.dwinovo.numen.network.payload.DismissRequestPayload::handle);
    }
}
