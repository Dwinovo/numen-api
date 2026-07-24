package com.dwinovo.numen.client.vision;

import com.dwinovo.numen.agent.llm.ConvoState;
import com.dwinovo.numen.agent.llm.VisualObservation;
import com.dwinovo.numen.agent.provider.AssistantTurn;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObservationRequestPolicyTest {

    private static final VisualObservation FRAME =
            new VisualObservation("image/jpeg", "YWJj", 1280, 720, "high");

    @Test
    void hybridCapturesOpeningPulseButNotToolFollowups() {
        assertTrue(ObservationRequestPolicy.shouldCapture(ObservationMode.HYBRID,
                List.of(new ConvoState.Msg.User("<query>look</query>"))));
        assertFalse(ObservationRequestPolicy.shouldCapture(ObservationMode.HYBRID, List.of(
                new ConvoState.Msg.User("<query>look</query>"),
                new ConvoState.Msg.Assistant(new AssistantTurn("", List.of(), null)),
                new ConvoState.Msg.Tool("call-1", "{}"))));
        assertTrue(ObservationRequestPolicy.shouldCapture(ObservationMode.VISUAL,
                List.of(new ConvoState.Msg.Tool("call-1", "{}"))));
        assertFalse(ObservationRequestPolicy.shouldCapture(ObservationMode.STRUCTURED,
                List.of(new ConvoState.Msg.User("look"))));
    }

    @Test
    void pureVisualStripsInjectedContextOnlyWhenFrameExists() {
        String content = "<current_task>working</current_task>\n"
                + "<known_blocks>\n  <block type=\"furnace\"/>\n</known_blocks>\n"
                + "<query>use it</query>";
        List<ConvoState.Msg> messages = List.of(new ConvoState.Msg.User(content));

        assertSame(messages, ObservationRequestPolicy.messagesForRequest(
                ObservationMode.VISUAL, null, messages));
        List<ConvoState.Msg> filtered = ObservationRequestPolicy.messagesForRequest(
                ObservationMode.VISUAL, FRAME, messages);
        String filteredContent = ((ConvoState.Msg.User) filtered.get(0)).content();
        assertEquals("<current_task>working</current_task>\n<query>use it</query>", filteredContent);
    }

    @Test
    void neverStripsOwnerTagInsideQuery() {
        String content = "<query>explain <known_blocks>x</known_blocks></query>";
        assertEquals(content, ObservationRequestPolicy.withoutInjectedKnownBlocks(content));
    }
}
