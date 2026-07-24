package com.dwinovo.numen.agent.goal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoalStateTest {

    @Test
    void onlyContinueReviewsConsumeTheAutonomousBudget() {
        GoalState start = new GoalState("g1", "verify ten iron", GoalState.Status.ACTIVE,
                2, 0, 0, "", "", 10, 10);
        GoalState continued = start.afterReview(GoalState.Status.ACTIVE,
                "not yet proven", "inspect inventory", 20);
        GoalState complete = continued.afterReview(GoalState.Status.COMPLETED,
                "inventory proves it", "", 30);

        assertEquals(1, continued.continuationsUsed());
        assertEquals(1, complete.continuationsUsed());
        assertEquals(2, complete.reviews());
        assertFalse(complete.isActive());
    }

    @Test
    void explicitResumeGrantsAFreshBoundedRun() {
        GoalState exhausted = new GoalState("g1", "goal", GoalState.Status.BUDGET_LIMITED,
                8, 8, 4, "budget", "inspect", 10, 20);
        GoalState resumed = exhausted.resume(30);

        assertTrue(resumed.isActive());
        assertTrue(resumed.canContinue());
        assertEquals(0, resumed.continuationsUsed());
        assertEquals(4, resumed.reviews());
    }
}
