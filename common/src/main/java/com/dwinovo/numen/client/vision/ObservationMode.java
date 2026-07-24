package com.dwinovo.numen.client.vision;

/** Selects how the agent receives changing world observations. */
public enum ObservationMode {
    /** Original Numen behaviour: text/tool observations only. */
    STRUCTURED,
    /** Structured observations plus a low-cost orientation frame at each user/event turn. */
    HYBRID,
    /** Fresh high-detail frame on every pulse; hide known-block text only after capture succeeds. */
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
