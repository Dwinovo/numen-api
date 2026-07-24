package com.dwinovo.numen.client.vision;

/** Selects how the agent receives changing world observations. */
public enum ObservationMode {
    /** Original Numen behaviour: text/tool observations only. */
    STRUCTURED,
    /** Explicitly selected first-person image plus all original structured observations. */
    HYBRID,
    /** Force image input; suppress automatically injected known-block text. */
    VISUAL;

    public static ObservationMode parse(String value) {
        if (value == null) return STRUCTURED;
        return switch (value.trim().toLowerCase()) {
            case "structured", "text", "off" -> STRUCTURED;
            case "hybrid", "mixed" -> HYBRID;
            case "visual", "vision", "pure_visual", "pure-visual" -> VISUAL;
            default -> STRUCTURED;
        };
    }
}
