package com.dwinovo.numen.agent.tool;

import com.dwinovo.numen.agent.llm.ConvoState;
import com.dwinovo.numen.agent.provider.AssistantTurn;
import com.dwinovo.numen.agent.provider.LlmToolCall;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolContextPrunerTest {

    @Test
    void neverPrunesAResultTheModelHasNotConsumed() {
        List<ConvoState.Msg> history = List.of(
                user("do it"), calling("pending", "move_to", "{}"),
                new ConvoState.Msg.Tool("pending", "{\"success\":true}"));

        ToolContextPruner.Result result = ToolContextPruner.prune(history);
        assertFalse(result.changed());
        assertEquals(history, result.messages());
    }

    @Test
    void batchesOldTransactionsAndKeepsRecentWorkingContext() {
        List<ConvoState.Msg> history = completedHistory(8, 12);
        ToolContextPruner.Result result = ToolContextPruner.prune(history);

        assertTrue(result.changed());
        assertEquals(4, result.prunedToolCallIds().size());
        assertTrue(result.prunedToolCallIds().contains("call-0"));
        assertTrue(result.prunedToolCallIds().contains("call-3"));
        assertFalse(result.prunedToolCallIds().contains("call-4"));
        assertEquals(4, result.removedAssistantMessages());
        assertEquals(4, result.removedToolResults());
        assertTrue(hasCall(result.messages(), "call-7"));
        assertFalse(hasCall(result.messages(), "call-0"));
    }

    @Test
    void largeOutputsCrossThePayloadThresholdWithoutWaitingForFourCandidates() {
        List<ConvoState.Msg> history = completedHistory(5, 9_000);
        ToolContextPruner.Result result = ToolContextPruner.prune(history);

        assertTrue(result.changed());
        assertEquals(3, result.prunedToolCallIds().size());
        assertTrue(result.prunedToolCallIds().contains("call-0"));
        assertTrue(result.removedPayloadChars() >= 27_000);
    }

    @Test
    void preservesVisibleAssistantTextWhenItsCallIsPruned() {
        List<ConvoState.Msg> history = new ArrayList<>();
        history.add(user("inspect"));
        JsonObject extras = new JsonObject();
        extras.addProperty("reasoning_content", "private rationale");
        history.add(new ConvoState.Msg.Assistant(new AssistantTurn("Checking it.",
                List.of(new LlmToolCall("old", "inspect", "{}")), extras)));
        history.add(new ConvoState.Msg.Tool("old", "x".repeat(30_000)));
        history.add(calling("recent", "inspect", "{}"));
        history.add(new ConvoState.Msg.Tool("recent", "ok"));
        history.add(new ConvoState.Msg.Assistant(new AssistantTurn("Done.", List.of(), null)));

        ToolContextPruner.Result result = ToolContextPruner.prune(history);
        ConvoState.Msg.Assistant kept = (ConvoState.Msg.Assistant) result.messages().get(1);
        assertEquals("Checking it.", kept.turn().content());
        assertTrue(kept.turn().toolCalls().isEmpty());
        assertTrue(kept.turn().extras().entrySet().isEmpty());
    }

    @Test
    void leavesSmallHistoriesUntouched() {
        List<ConvoState.Msg> history = completedHistory(5, 10);
        ToolContextPruner.Result result = ToolContextPruner.prune(history);
        assertFalse(result.changed());
        assertEquals(history, result.messages());
    }

    @Test
    void preservesLatestTodoPlanWhilePruningOlderPayloads() {
        List<ConvoState.Msg> history = new ArrayList<>();
        history.add(user("build a base"));
        history.add(calling("plan", "todowrite", "{\"todos\":[]}"));
        history.add(new ConvoState.Msg.Tool("plan",
                "{\"success\":true,\"todos\":[{\"content\":\"find iron\",\"status\":\"in_progress\"}]}"));
        for (int i = 0; i < 8; i++) {
            history.add(calling("work-" + i, "look_around", "{}"));
            history.add(new ConvoState.Msg.Tool("work-" + i, "x".repeat(2_000)));
        }
        history.add(new ConvoState.Msg.Assistant(new AssistantTurn("Continuing.", List.of(), null)));

        ToolContextPruner.Result result = ToolContextPruner.prune(history);

        assertTrue(result.changed());
        assertFalse(result.prunedToolCallIds().contains("plan"));
        assertTrue(hasCall(result.messages(), "plan"));
        assertTrue(result.messages().stream().anyMatch(message -> message instanceof ConvoState.Msg.Tool tool
                && "plan".equals(tool.toolCallId())));
    }

    private static List<ConvoState.Msg> completedHistory(int count, int resultChars) {
        List<ConvoState.Msg> history = new ArrayList<>();
        history.add(user("work"));
        for (int i = 0; i < count; i++) {
            history.add(calling("call-" + i, "tool_" + i, "{\"step\":" + i + "}"));
            history.add(new ConvoState.Msg.Tool("call-" + i, "x".repeat(resultChars)));
        }
        history.add(new ConvoState.Msg.Assistant(new AssistantTurn("Finished.", List.of(), null)));
        return history;
    }

    private static ConvoState.Msg.User user(String text) {
        return new ConvoState.Msg.User(text);
    }

    private static ConvoState.Msg.Assistant calling(String id, String name, String arguments) {
        return new ConvoState.Msg.Assistant(new AssistantTurn("",
                List.of(new LlmToolCall(id, name, arguments)), null));
    }

    private static boolean hasCall(List<ConvoState.Msg> messages, String id) {
        return messages.stream()
                .filter(ConvoState.Msg.Assistant.class::isInstance)
                .map(ConvoState.Msg.Assistant.class::cast)
                .flatMap(message -> message.turn().toolCalls().stream())
                .anyMatch(call -> id.equals(call.id()));
    }
}
