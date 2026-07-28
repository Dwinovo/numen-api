package com.dwinovo.numen.social;

import com.dwinovo.numen.entity.Companions;
import com.dwinovo.numen.entity.NumenPlayer;
import com.dwinovo.numen.event.GameEvents;

/**
 * The one place that both the Group UI tab ({@code GroupActionPayload}/{@code GroupRelayPayload})
 * and the {@code /numen group delegate|chat} commands go through, so entry points can never
 * drift: feed the message into the target companion's own agent loop.
 *
 * <p>Sent with {@code principal=true} — see {@link Companions#emitEvent} — because this is a
 * live human (or, for {@link #relay}, another companion the owner is already listening to)
 * speaking, exactly like a chat message from the owner; it opens a reasoning turn immediately
 * instead of silently riding along on the companion's next one the way an ordinary
 * {@code GameEvents.emit} world event would (that call hardcodes {@code principal=false}).
 *
 * <p>This does NOT echo into the owner's vanilla chat box. Coordination is shown inside the
 * mod's own Group tab instead — see {@code GroupManager}'s chat log, kept in sync with the
 * client via {@code GroupSyncPayload}. The caller (payload/command handler) is responsible for
 * recording the line via {@link GroupManager#recordChatLine} once per action, not once per
 * recipient, so a broadcast to N members doesn't produce N duplicate transcript lines.
 */
public final class GroupMessaging {

    private GroupMessaging() {}

    /** Owner → companion. {@code delegate=true} phrases it as a task handoff; {@code false} as a
     *  group chat line. */
    public static void deliver(NumenPlayer target, String text, boolean delegate) {
        String body = (delegate ? "主人委派了一项任务给你：" : "主人对你的群组说：") + text;
        emit(target, "owner", body);
    }

    /** Companion → companion: {@code from} just finished a reply while a member of a group, and
     *  it gets relayed to groupmate {@code to} the same way a chat message would — this is what
     *  lets two companions actually carry on a conversation instead of only ever hearing from the
     *  owner. See {@code GroupRelayPayload} (the network half) and the hook in
     *  {@code EntityAgentLoop} that fires this after every finished turn. */
    public static void relay(NumenPlayer from, NumenPlayer to, String text) {
        String body = from.getName().getString() + " 对群组说：" + text;
        emit(to, "companion", body);
    }

    private static void emit(NumenPlayer target, String from, String body) {
        String xml = "<event kind=\"body_log\" from=\"" + from + "\">" + GameEvents.escape(body) + "</event>";
        Companions.emitEvent(target, xml, true);
    }
}
