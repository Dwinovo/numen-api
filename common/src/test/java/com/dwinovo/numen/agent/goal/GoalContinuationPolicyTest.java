package com.dwinovo.numen.agent.goal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GoalContinuationPolicyTest {

    private static GoalState goal(int used, int budget) {
        return new GoalState("g", "objective", GoalState.Status.ACTIVE,
                budget, used, 0, "", "", 1, 1);
    }

    @Test
    void initialWorkerTurnMayReceiveOneConcreteContinuation() {
        assertEquals(GoalContinuationPolicy.Decision.CONTINUE,
                GoalContinuationPolicy.decide(goal(0, 8), false, false));
    }

    @Test
    void automaticTurnWithoutAnyToolCallNeverSpinsAgain() {
        assertEquals(GoalContinuationPolicy.Decision.STOP_NO_TOOL_PROGRESS,
                GoalContinuationPolicy.decide(goal(1, 8), true, false));
    }

    @Test
    void aProgressingTurnStillStopsAtTheExplicitBudget() {
        assertEquals(GoalContinuationPolicy.Decision.STOP_BUDGET,
                GoalContinuationPolicy.decide(goal(8, 8), true, true));
        assertEquals(GoalContinuationPolicy.Decision.CONTINUE,
                GoalContinuationPolicy.decide(goal(7, 8), true, true));
    }
}
