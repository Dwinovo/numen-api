package com.dwinovo.numen.agent.goal;

import java.util.Locale;

/** Parser for the owner-only, local {@code /goal} command surface. */
public record GoalCommand(Action action, String objective) {

    public enum Action { SHOW, ENABLE, DISABLE, PAUSE, RESUME, CLEAR, HELP, SET }

    /** Returns {@code null} for ordinary chat text. */
    public static GoalCommand parse(String input) {
        if (input == null) return null;
        String text = input.strip();
        if (!(text.equalsIgnoreCase("/goal")
                || text.regionMatches(true, 0, "/goal ", 0, 6))) {
            return null;
        }
        String rest = text.length() <= 5 ? "" : text.substring(6).strip();
        if (rest.isEmpty() || rest.equalsIgnoreCase("status")) {
            return new GoalCommand(Action.SHOW, "");
        }
        return switch (rest.toLowerCase(Locale.ROOT)) {
            case "on" -> new GoalCommand(Action.ENABLE, "");
            case "off" -> new GoalCommand(Action.DISABLE, "");
            case "pause" -> new GoalCommand(Action.PAUSE, "");
            case "resume" -> new GoalCommand(Action.RESUME, "");
            case "clear" -> new GoalCommand(Action.CLEAR, "");
            case "help" -> new GoalCommand(Action.HELP, "");
            default -> new GoalCommand(Action.SET, rest);
        };
    }
}
