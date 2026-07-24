package com.dwinovo.numen.agent.goal;

import java.util.Locale;
import java.util.UUID;

/**
 * Durable, thread-scoped completion contract for one companion conversation.
 *
 * <p>The worker model never owns lifecycle authority: it may work toward an active Goal, while
 * owner commands and the independent supervisor move the state through this immutable value.
 */
public record GoalState(String id, String objective, Status status,
                        int continuationBudget, int continuationsUsed, int reviews,
                        String lastReason, String nextStep,
                        long createdAt, long updatedAt) {

    /** Conservative default: normal agent chains remain uncapped, but autonomous Goal pulses do not. */
    public static final int DEFAULT_CONTINUATION_BUDGET = 8;

    public enum Status {
        ACTIVE, PAUSED, COMPLETED, BLOCKED, BUDGET_LIMITED;

        public String wireName() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Status parse(String value) {
            if (value == null) return PAUSED;
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                return PAUSED;
            }
        }
    }

    public GoalState {
        id = clean(id);
        objective = clean(objective);
        status = status == null ? Status.PAUSED : status;
        continuationBudget = Math.max(1, continuationBudget);
        continuationsUsed = Math.max(0, continuationsUsed);
        reviews = Math.max(0, reviews);
        lastReason = clean(lastReason);
        nextStep = clean(nextStep);
        createdAt = Math.max(0, createdAt);
        updatedAt = Math.max(createdAt, updatedAt);
    }

    public static GoalState start(String objective, long now) {
        return new GoalState(UUID.randomUUID().toString(), objective, Status.ACTIVE,
                DEFAULT_CONTINUATION_BUDGET, 0, 0, "", "", now, now);
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }

    public boolean canContinue() {
        return isActive() && continuationsUsed < continuationBudget;
    }

    /** Record one supervisor verdict; CONTINUE consumes one autonomous continuation slot. */
    public GoalState afterReview(Status verdict, String reason, String next, long now) {
        boolean continuing = verdict == Status.ACTIVE;
        return new GoalState(id, objective, verdict, continuationBudget,
                continuationsUsed + (continuing ? 1 : 0), reviews + 1,
                reason, next, createdAt, now);
    }

    public GoalState pause(String reason, long now) {
        return new GoalState(id, objective, Status.PAUSED, continuationBudget,
                continuationsUsed, reviews, reason, nextStep, createdAt, now);
    }

    /** An explicit owner resume grants a fresh bounded autonomous run. */
    public GoalState resume(long now) {
        return new GoalState(id, objective, Status.ACTIVE, continuationBudget,
                0, reviews, "", nextStep, createdAt, now);
    }

    public GoalState budgetLimited(String reason, long now) {
        return new GoalState(id, objective, Status.BUDGET_LIMITED, continuationBudget,
                continuationsUsed, reviews + 1, reason, nextStep, createdAt, now);
    }

    private static String clean(String value) {
        return value == null ? "" : value.strip();
    }
}
