package com.dwinovo.numen.agent.goal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GoalSupervisorReviewTest {

    @Test
    void parsesAContinueVerdictFromFencedOutput() {
        GoalSupervisorReview review = GoalSupervisorReview.parse("""
                ```json
                {"verdict":"continue","reason":"Only six ingots are proven.","next_step":"Inspect inventory, then collect four more."}
                ```
                """);
        assertEquals(GoalSupervisorReview.Verdict.CONTINUE, review.verdict());
        assertEquals("Inspect inventory, then collect four more.", review.nextStep());
    }

    @Test
    void acceptsEvidenceBasedCompletionWithoutANextStep() {
        GoalSupervisorReview review = GoalSupervisorReview.parse(
                "{\"verdict\":\"complete\",\"reason\":\"The latest inventory result proves ten ingots.\"}");
        assertEquals(GoalSupervisorReview.Verdict.COMPLETE, review.verdict());
    }

    @Test
    void rejectsMalformedOrUnderSpecifiedPermissionToContinue() {
        assertNull(GoalSupervisorReview.parse("continue"));
        assertNull(GoalSupervisorReview.parse(
                "{\"verdict\":\"continue\",\"reason\":\"not done\"}"));
        assertNull(GoalSupervisorReview.parse(
                "{\"verdict\":\"maybe\",\"reason\":\"uncertain\",\"next_step\":\"look\"}"));
    }
}
