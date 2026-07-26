package com.dwinovo.numen.api;

import com.dwinovo.numen.entity.NumenPlayer;
import com.dwinovo.numen.event.GameEvents;

import java.util.Map;
import java.util.regex.Pattern;

/** Public server-side entry point for delivering structured events to a companion brain. */
public final class NumenEvents {

    private static final Pattern XML_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_.-]*");

    private NumenEvents() {}

    /**
     * Deliver an event with one unit of idle-wake authority granted beforehand by
     * the owner or agent. This is intended for durable one-shot leases such as a
     * scheduled reminder, not for ordinary world observations.
     *
     * <p>The event starts a reasoning turn when the brain is idle, or joins the
     * inbox at the next protocol boundary when a turn is already running. The
     * producer remains a system component: this does not impersonate a human
     * principal.
     *
     * @param body      live companion body whose owner should receive the event
     * @param kind      stable event kind, for example {@code scheduled_wake}
     * @param attrs     optional event attributes; values are XML-escaped
     * @param text      event body; XML-special characters are escaped
     * @return true only when the owner is online and the event packet was sent
     */
    public static boolean emitAuthorizedWake(NumenPlayer body, String kind,
                                             Map<String, String> attrs, String text) {
        if (body == null) return false;
        requireXmlName(kind, "kind");
        if (attrs != null) attrs.keySet().forEach(key -> requireXmlName(key, "attribute name"));
        return GameEvents.authorizedWake(body, kind, attrs, text == null ? "" : text);
    }

    private static void requireXmlName(String value, String label) {
        if (value == null || !XML_NAME.matcher(value).matches()) {
            throw new IllegalArgumentException(label + " is not a valid XML name: " + value);
        }
    }
}
