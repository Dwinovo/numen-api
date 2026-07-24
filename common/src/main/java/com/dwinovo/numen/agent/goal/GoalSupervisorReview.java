package com.dwinovo.numen.agent.goal;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Locale;

/** Strict, tool-free verdict returned by the independent Goal supervisor role. */
public record GoalSupervisorReview(Verdict verdict, String reason, String nextStep) {

    public enum Verdict { COMPLETE, CONTINUE, BLOCKED }

    /**
     * Parse the first JSON object in a response. A malformed or unknown verdict is rejected instead
     * of being treated as permission to continue autonomously.
     */
    public static GoalSupervisorReview parse(String raw) {
        if (raw == null || raw.isBlank()) return null;
        int open = raw.indexOf('{');
        int close = raw.lastIndexOf('}');
        if (open < 0 || close <= open) return null;
        try {
            JsonElement parsed = JsonParser.parseString(raw.substring(open, close + 1));
            if (!parsed.isJsonObject()) return null;
            JsonObject object = parsed.getAsJsonObject();
            String value = string(object, "verdict").toUpperCase(Locale.ROOT);
            Verdict verdict;
            try {
                verdict = Verdict.valueOf(value);
            } catch (IllegalArgumentException ex) {
                return null;
            }
            String reason = string(object, "reason");
            String next = string(object, "next_step");
            if (reason.isBlank()) return null;
            if (verdict == Verdict.CONTINUE && next.isBlank()) return null;
            return new GoalSupervisorReview(verdict, reason.strip(), next.strip());
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static String string(JsonObject object, String key) {
        if (!object.has(key) || object.get(key).isJsonNull()
                || !object.get(key).isJsonPrimitive()) return "";
        try {
            return object.get(key).getAsString();
        } catch (RuntimeException ex) {
            return "";
        }
    }
}
