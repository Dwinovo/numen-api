package com.dwinovo.numen.agent.goal;

/** Pure safety gate between a supervisor CONTINUE verdict and another autonomous worker pulse. */
public final class GoalContinuationPolicy {

    public enum Decision { CONTINUE, STOP_NO_TOOL_PROGRESS, STOP_BUDGET }

    private GoalContinuationPolicy() {}

    public static Decision decide(GoalState goal, boolean automaticContinuationTurn,
                                  boolean cycleHadToolCall) {
        if (automaticContinuationTurn && !cycleHadToolCall) {
            return Decision.STOP_NO_TOOL_PROGRESS;
        }
        if (goal == null || !goal.canContinue()) {
            return Decision.STOP_BUDGET;
        }
        return Decision.CONTINUE;
    }
}
