package com.dwinovo.numen.agent.tool;

import com.dwinovo.numen.agent.llm.ConvoState;
import com.dwinovo.numen.agent.provider.AssistantTurn;
import com.dwinovo.numen.agent.provider.LlmToolCall;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Removes stale, completed tool transactions from a request-local conversation snapshot.
 *
 * <p>This follows the safe transaction boundary used by o-pi's {@code /prune-tools}: a call and its
 * matching result are removed together, tool-only assistant messages disappear, and visible text is
 * retained. Numen applies it automatically in conservative batches. The in-memory conversation,
 * append-only JSONL log, and chat display are never modified.
 *
 * <p>A result is prunable only after a later assistant response proves that the model has consumed
 * it. Trailing results awaiting the next model pulse are therefore always retained. Four recent
 * consumed transactions remain as short-term working context, subject to a payload cap.
 */
public final class ToolContextPruner {

    static final int RECENT_TRANSACTIONS_TO_KEEP = 4;
    static final int MIN_BATCH_TRANSACTIONS = 4;
    static final int MIN_BATCH_CHARS = 8_000;
    static final int MAX_RETAINED_CHARS = 24_000;

    private ToolContextPruner() {}

    public record Result(List<ConvoState.Msg> messages, Set<String> prunedToolCallIds,
                         int removedAssistantMessages, int removedToolResults,
                         long removedPayloadChars) {
        public Result {
            messages = List.copyOf(messages);
            prunedToolCallIds = Set.copyOf(prunedToolCallIds);
        }

        public boolean changed() {
            return !prunedToolCallIds.isEmpty();
        }
    }

    private record Transaction(String id, int callIndex, int resultIndex, long payloadChars) {}

    /** Build the effective model context without mutating or persisting the source history. */
    public static Result prune(List<ConvoState.Msg> messages) {
        if (messages == null || messages.isEmpty()) return unchanged(messages);

        List<Transaction> eligible = eligibleTransactions(messages);
        if (eligible.isEmpty()) return unchanged(messages);

        // Retain the newest few consumed transactions while they fit the payload budget. Always
        // retain the newest one so the final assistant text has immediate procedural context.
        Set<String> retained = new LinkedHashSet<>();
        long retainedChars = 0;
        for (int i = eligible.size() - 1; i >= 0; i--) {
            Transaction tx = eligible.get(i);
            boolean roomByCount = retained.size() < RECENT_TRANSACTIONS_TO_KEEP;
            boolean roomByPayload = retained.isEmpty()
                    || retainedChars + tx.payloadChars() <= MAX_RETAINED_CHARS;
            if (roomByCount && roomByPayload) {
                retained.add(tx.id());
                retainedChars += tx.payloadChars();
            }
        }

        Set<String> candidates = new LinkedHashSet<>();
        long candidateChars = 0;
        for (Transaction tx : eligible) {
            if (retained.contains(tx.id())) continue;
            candidates.add(tx.id());
            candidateChars += tx.payloadChars();
        }
        // Batch small transactions to avoid invalidating a provider's prompt-cache prefix every
        // pulse. One or two very large outputs may still cross the payload threshold immediately.
        if (candidates.size() < MIN_BATCH_TRANSACTIONS && candidateChars < MIN_BATCH_CHARS) {
            return unchanged(messages);
        }

        List<ConvoState.Msg> pruned = new ArrayList<>(messages.size());
        int removedAssistants = 0;
        int removedResults = 0;
        for (ConvoState.Msg message : messages) {
            if (message instanceof ConvoState.Msg.Tool tool
                    && candidates.contains(tool.toolCallId())) {
                removedResults++;
                continue;
            }
            if (!(message instanceof ConvoState.Msg.Assistant assistant)) {
                pruned.add(message);
                continue;
            }

            List<LlmToolCall> calls = assistant.turn().toolCalls();
            List<LlmToolCall> remaining = calls.stream()
                    .filter(call -> !candidates.contains(call.id()))
                    .toList();
            if (remaining.size() == calls.size()) {
                pruned.add(message);
                continue;
            }
            if (remaining.isEmpty() && assistant.turn().content().isBlank()) {
                removedAssistants++;
                continue;
            }
            // Provider extras such as reasoning_content belong to the removed call rationale. Keep
            // them only when this assistant message still contains another live tool call.
            pruned.add(new ConvoState.Msg.Assistant(new AssistantTurn(
                    assistant.turn().content(), remaining,
                    remaining.isEmpty() ? null : assistant.turn().extras())));
        }

        return new Result(pruned, candidates, removedAssistants, removedResults, candidateChars);
    }

    private static List<Transaction> eligibleTransactions(List<ConvoState.Msg> messages) {
        Map<String, Integer> resultIndexes = new HashMap<>();
        Map<String, Long> resultChars = new HashMap<>();
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i) instanceof ConvoState.Msg.Tool tool
                    && tool.toolCallId() != null && !tool.toolCallId().isBlank()) {
                resultIndexes.put(tool.toolCallId(), i);
                resultChars.merge(tool.toolCallId(), length(tool.content()), Long::sum);
            }
        }

        // Last assistant at or after each index. A strictly later assistant proves the matching
        // result was included in a completed model request.
        int[] nextAssistant = new int[messages.size()];
        int nearest = -1;
        for (int i = messages.size() - 1; i >= 0; i--) {
            nextAssistant[i] = nearest;
            if (messages.get(i) instanceof ConvoState.Msg.Assistant) nearest = i;
        }

        Map<String, Transaction> byId = new LinkedHashMap<>();
        for (int i = 0; i < messages.size(); i++) {
            if (!(messages.get(i) instanceof ConvoState.Msg.Assistant assistant)) continue;
            for (LlmToolCall call : assistant.turn().toolCalls()) {
                String id = call.id();
                Integer resultIndex = id == null ? null : resultIndexes.get(id);
                if (id == null || id.isBlank() || resultIndex == null || resultIndex <= i
                        || nextAssistant[resultIndex] < 0) {
                    continue;
                }
                long chars = length(id) + length(call.name()) + length(call.arguments())
                        + resultChars.getOrDefault(id, 0L);
                byId.putIfAbsent(id, new Transaction(id, i, resultIndex, chars));
            }
        }
        return byId.values().stream()
                .sorted(Comparator.comparingInt(Transaction::resultIndex))
                .toList();
    }

    private static long length(String value) {
        return value == null ? 0L : value.length();
    }

    private static Result unchanged(List<ConvoState.Msg> messages) {
        return new Result(messages == null ? List.of() : messages, Set.of(), 0, 0, 0);
    }
}
