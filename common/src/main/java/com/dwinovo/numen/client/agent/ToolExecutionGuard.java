package com.dwinovo.numen.client.agent;

import java.util.Locale;
import java.util.Set;

/** Pure preflight policy for preventing stale or immediately duplicated world actions. */
final class ToolExecutionGuard {

    private ToolExecutionGuard() {}

    static boolean hasNewInput(int promptCount, int eventCount) {
        return promptCount > 0 || eventCount > 0;
    }

    static boolean duplicatesCompleted(String completedFingerprint,
                                       long completedDirectiveGeneration,
                                       long currentDirectiveGeneration,
                                       long completedAtMs,
                                       long nowMs,
                                       long ttlMs,
                                       Set<String> repeatableTools,
                                       String requestedTool,
                                       String requestedArguments) {
        if (completedFingerprint == null || requestedTool == null
                || completedDirectiveGeneration != currentDirectiveGeneration
                || nowMs - completedAtMs > ttlMs) {
            return false;
        }
        String name = requestedTool.strip().toLowerCase(Locale.ROOT);
        if (repeatableTools != null && repeatableTools.contains(name)) return false;
        return completedFingerprint.equals(ToolCallFingerprint.of(requestedTool, requestedArguments));
    }
}
