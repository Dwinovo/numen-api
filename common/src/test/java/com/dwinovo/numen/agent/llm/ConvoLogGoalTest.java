package com.dwinovo.numen.agent.llm;

import com.dwinovo.numen.agent.goal.GoalState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ConvoLogGoalTest {

    @TempDir
    Path temp;

    @Test
    void latestGoalStateSurvivesCompactionEventsAndRelaunch() {
        ConvoLog log = ConvoLog.forEntity(temp, UUID.randomUUID());
        GoalState active = new GoalState("goal-1", "collect ten iron", GoalState.Status.ACTIVE,
                8, 2, 3, "six proven", "collect four", 100, 200);
        log.appendGoalState(active);
        log.appendCompactSummary("summary", java.util.List.of(), null);
        GoalState paused = active.pause("owner paused", 300);
        log.appendGoalState(paused);

        assertEquals(paused, log.loadCurrentGoal());
        assertEquals(1, log.loadDisplay(100).size()); // compact divider only; Goal events stay out of chat
    }

    @Test
    void clearTombstonePreventsAnOlderGoalFromReturning() {
        ConvoLog log = ConvoLog.forEntity(temp, UUID.randomUUID());
        log.appendGoalState(new GoalState("goal-1", "goal", GoalState.Status.ACTIVE,
                8, 0, 0, "", "", 100, 100));
        log.appendGoalCleared();

        assertNull(log.loadCurrentGoal());
    }
}
