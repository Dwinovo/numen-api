package com.dwinovo.numen.entity;

import net.minecraft.network.PacketSendListener;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;

/**
 * A channel-less {@link Connection} for a companion fake {@code ServerPlayer}.
 * The server pushes a steady stream of clientbound packets at any list-resident
 * player (position, chunks, entity updates, keep-alives); a real connection
 * writes them to a netty channel, but our companion has no client on the other
 * end. A bare {@code new Connection(...)} is NOT safe: its {@code send} queues
 * every packet into an unbounded {@code pendingActions} list when no channel is
 * attached — a slow leak. So we subclass and DISCARD all outbound I/O.
 *
 * <p>{@code Connection} is non-final with a public {@code PacketFlow} ctor, so
 * this is pure common code — no reflection, access-wideners or mixins. Two
 * details make {@code placeNewPlayer} accept it:
 * <ul>
 *   <li><b>SERVERBOUND</b> receiving flow — a server-side player connection
 *       receives serverbound packets, so its listener is serverbound;
 *       {@code validateListener} rejects a CLIENTBOUND mismatch.</li>
 *   <li>a real (in-memory) {@link EmbeddedChannel} — registering this Connection
 *       as the embedded channel's handler fires {@code channelActive}, setting
 *       {@code this.channel} so {@code placeNewPlayer} has a channel to work on.</li>
 * </ul>
 * Outbound packets are still discarded by the {@code send} override (the embedded
 * channel is never written to, so it never buffers/leaks), and the keep-alive
 * timeout is neutralised via the no-op {@code disconnect}. Lifecycle is governed
 * by {@code CompanionLifecycle}, not by connection state.
 *
 * <p>1.20.2 introduced the configuration phase: the packet codec now lives in
 * channel ATTRIBUTES ({@code ATTRIBUTE_*_PROTOCOL}), and
 * {@code ServerGamePacketListenerImpl}'s ctor reads them immediately — a bare
 * embedded channel NPEs there, so both attributes are seeded with the PLAY
 * codec below.
 */
@com.dwinovo.numen.api.Internal
public final class FakeConnection extends Connection {

    public FakeConnection() {
        super(PacketFlow.SERVERBOUND);
        // channelActive (fired by registering this handler) sets this.channel.
        EmbeddedChannel ch = new EmbeddedChannel(this);
        // 1.20.2:协议编解码挂在 channel attribute 上,placeNewPlayer →
        // ServerGamePacketListenerImpl 构造即读取,不种直接 NPE。
        ch.attr(Connection.ATTRIBUTE_SERVERBOUND_PROTOCOL)
                .set(net.minecraft.network.ConnectionProtocol.PLAY.codec(PacketFlow.SERVERBOUND));
        ch.attr(Connection.ATTRIBUTE_CLIENTBOUND_PROTOCOL)
                .set(net.minecraft.network.ConnectionProtocol.PLAY.codec(PacketFlow.CLIENTBOUND));
    }

    /** Discard every outbound packet — there is no client to receive it.
     *  1.20.1's {@code send(Packet)} routes through this 2-arg overload. */
    @Override
    public void send(Packet<?> packet, PacketSendListener listener) {
        // no-op: drop it on the floor (no channel, no pendingActions growth)
    }

    /**
     * Report live so player ticking / chunk tracking proceed as for a real
     * player. Safe because every channel-dereferencing path (send, tick) is
     * overridden to no-op, so the null channel is never touched.
     */
    @Override
    public boolean isConnected() {
        return true;
    }

    /** Don't drive the packet listener's tick (that's what runs the keep-alive). */
    @Override
    public void tick() {
        // no-op
    }

    /** Neutralise the keep-alive timeout (and any other) disconnect. */
    @Override
    public void disconnect(Component message) {
        // no-op: the companion is removed via CompanionLifecycle, never by the wire
    }

    @Override
    public void handleDisconnection() {
        // no-op
    }

    @Override
    public void setReadOnly() {
        // no-op
    }
}
