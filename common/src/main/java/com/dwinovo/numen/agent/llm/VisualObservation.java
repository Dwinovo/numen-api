package com.dwinovo.numen.agent.llm;

/**
 * One ephemeral visual observation attached to an LLM request.
 *
 * <p>The image is deliberately not part of {@link ConvoState}: persisting a base64 screenshot in
 * every conversation turn would make logs enormous and would repeatedly bill old image tokens.
 * The agent stores only the model's textual/tool response; each request receives one fresh frame.
 *
 * @param mimeType image MIME type (normally {@code image/jpeg})
 * @param base64   raw base64 payload, without a {@code data:} prefix
 * @param width    encoded pixel width
 * @param height   encoded pixel height
 * @param detail   OpenAI-compatible image detail hint ({@code low}, {@code high}, or {@code auto})
 */
public record VisualObservation(String mimeType, String base64, int width, int height, String detail) {

    public VisualObservation {
        mimeType = mimeType == null || mimeType.isBlank() ? "image/jpeg" : mimeType;
        base64 = base64 == null ? "" : base64;
        width = Math.max(1, width);
        height = Math.max(1, height);
        detail = switch (detail == null ? "auto" : detail.toLowerCase()) {
            case "low", "high" -> detail.toLowerCase();
            default -> "auto";
        };
    }

    public boolean isEmpty() {
        return base64.isEmpty();
    }

    public String dataUrl() {
        return "data:" + mimeType + ";base64," + base64;
    }
}
