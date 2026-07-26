package com.dwinovo.numen.client.agent;

/** Pure routing decision for an inboxed event. */
final class EventWakePolicy {

    private EventWakePolicy() {}

    static boolean shouldStartTurn(boolean principal, boolean wakeIdle, boolean duringTask) {
        return principal || wakeIdle || duringTask;
    }
}
