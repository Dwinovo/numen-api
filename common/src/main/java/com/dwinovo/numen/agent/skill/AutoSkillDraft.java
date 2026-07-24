package com.dwinovo.numen.agent.skill;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;

/** Parser/normalizer for the constrained SKILL.md draft emitted by automatic workflow learning. */
public final class AutoSkillDraft {

    public static final int MAX_BODY_CHARS = 24_000;
    private static final int MAX_DESCRIPTION_CHARS = 320;

    public record Draft(String name, String description, String body) {
        public String markdown() {
            String safeDescription = description.replace('"', '\'').replace('\n', ' ').replace('\r', ' ').strip();
            return "---\nname: " + name + "\ndescription: \"" + safeDescription
                    + "\"\ngenerated: true\n---\n\n" + body.strip() + "\n";
        }
    }

    private AutoSkillDraft() {}

    /**
     * Accepts either a raw SKILL.md or one wrapped in {@code <skill>}; {@code <skip>} and malformed
     * drafts return empty. Name/path normalization is intentionally strict so model output can never
     * escape the player's skills directory.
     */
    public static Optional<Draft> parse(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        String text = raw.strip();
        if (text.regionMatches(true, 0, "<skip", 0, 5)) return Optional.empty();

        int skillOpen = text.indexOf("<skill>");
        if (skillOpen >= 0) {
            int start = skillOpen + "<skill>".length();
            int close = text.indexOf("</skill>", start);
            text = (close >= 0 ? text.substring(start, close) : text.substring(start)).strip();
        }
        if (text.startsWith("```")) {
            int firstLine = text.indexOf('\n');
            int close = text.lastIndexOf("```");
            if (firstLine >= 0) text = text.substring(firstLine + 1, close > firstLine ? close : text.length()).strip();
        }

        SkillMarkdown.Parsed parsed = SkillMarkdown.parse(text);
        String proposedName = parsed.frontmatter().get("name");
        String description = oneLine(parsed.frontmatter().get("description"));
        String body = parsed.content() == null ? "" : parsed.content().strip();
        if (proposedName == null || proposedName.isBlank() || description.isBlank() || body.length() < 40) {
            return Optional.empty();
        }
        if (body.length() > MAX_BODY_CHARS) body = body.substring(0, MAX_BODY_CHARS).strip();
        if (description.length() > MAX_DESCRIPTION_CHARS) {
            description = description.substring(0, MAX_DESCRIPTION_CHARS).strip();
        }
        return Optional.of(new Draft(slug(proposedName), description, body));
    }

    static String slug(String value) {
        String slug = value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_-]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^[_-]+|[_-]+$", "");
        if (slug.isEmpty()) {
            // Stable fallback for non-Latin names; no raw model text enters the path.
            int hash = java.util.Arrays.hashCode(value.getBytes(StandardCharsets.UTF_8));
            slug = "learned_" + Integer.toUnsignedString(hash, 36);
        }
        if (!Character.isLetterOrDigit(slug.charAt(0))) slug = "learned_" + slug;
        return slug.length() <= 64 ? slug : slug.substring(0, 64).replaceAll("[_-]+$", "");
    }

    private static String oneLine(String value) {
        if (value == null) return "";
        return value.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ').strip();
    }
}
