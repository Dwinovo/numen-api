package com.dwinovo.numen.client.vision;

import com.dwinovo.numen.agent.llm.ConvoState;
import com.dwinovo.numen.agent.llm.VisualObservation;

import java.util.ArrayList;
import java.util.List;

/** Request-local policy shared by pure-visual and hybrid observation modes. */
public final class ObservationRequestPolicy {

    private static final String KNOWN_BLOCKS_OPEN = "<known_blocks>";
    private static final String KNOWN_BLOCKS_CLOSE = "</known_blocks>";

    private ObservationRequestPolicy() {}

    /**
     * Hybrid pays for one orientation frame when a user/event turn starts, then relies on the
     * structured tool results during the rest of that chain. Pure visual needs a fresh frame on
     * every pulse because images are deliberately not retained in conversation history.
     */
    public static boolean shouldCapture(ObservationMode mode, List<ConvoState.Msg> messages) {
        if (mode == null || mode == ObservationMode.STRUCTURED) return false;
        if (mode == ObservationMode.VISUAL) return true;
        return messages != null && !messages.isEmpty()
                && messages.get(messages.size() - 1) instanceof ConvoState.Msg.User;
    }

    /**
     * Hide automatically injected known-block coordinates only when pure visual actually has an
     * image. If capture times out or the body is unloaded, leave the structured context intact so
     * the turn degrades safely instead of becoming blind. The persisted conversation is untouched.
     */
    public static List<ConvoState.Msg> messagesForRequest(ObservationMode mode,
                                                          VisualObservation observation,
                                                          List<ConvoState.Msg> messages) {
        if (mode != ObservationMode.VISUAL || observation == null || observation.isEmpty()
                || messages == null || messages.isEmpty()) {
            return messages == null ? List.of() : messages;
        }

        List<ConvoState.Msg> filtered = null;
        for (int i = 0; i < messages.size(); i++) {
            ConvoState.Msg message = messages.get(i);
            if (!(message instanceof ConvoState.Msg.User user)) continue;
            String content = withoutInjectedKnownBlocks(user.content());
            if (content.equals(user.content())) continue;
            if (filtered == null) filtered = new ArrayList<>(messages);
            filtered.set(i, new ConvoState.Msg.User(content));
        }
        return filtered == null ? messages : List.copyOf(filtered);
    }

    static String withoutInjectedKnownBlocks(String content) {
        if (content == null || content.isEmpty()) return content == null ? "" : content;
        int open = content.indexOf(KNOWN_BLOCKS_OPEN);
        // Generated context is inserted before owner <query> content. Never strip a similarly named
        // tag supplied by the owner inside their query.
        int query = content.indexOf("<query>");
        if (open < 0 || (query >= 0 && open > query)) return content;
        int close = content.indexOf(KNOWN_BLOCKS_CLOSE, open + KNOWN_BLOCKS_OPEN.length());
        if (close < 0) return content;
        int end = close + KNOWN_BLOCKS_CLOSE.length();
        if (end < content.length() && content.charAt(end) == '\r') end++;
        if (end < content.length() && content.charAt(end) == '\n') end++;
        String result = content.substring(0, open) + content.substring(end);
        return result.strip();
    }
}
