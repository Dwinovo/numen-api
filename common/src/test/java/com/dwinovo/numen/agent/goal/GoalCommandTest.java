package com.dwinovo.numen.agent.goal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GoalCommandTest {

    @Test
    void parsesLifecycleCommandsCaseInsensitively() {
        assertEquals(GoalCommand.Action.SHOW, GoalCommand.parse(" /goal ").action());
        assertEquals(GoalCommand.Action.PAUSE, GoalCommand.parse("/GOAL pause").action());
        assertEquals(GoalCommand.Action.RESUME, GoalCommand.parse("/goal resume").action());
        assertEquals(GoalCommand.Action.CLEAR, GoalCommand.parse("/goal clear").action());
        assertEquals(GoalCommand.Action.ENABLE, GoalCommand.parse("/goal on").action());
        assertEquals(GoalCommand.Action.DISABLE, GoalCommand.parse("/goal off").action());
    }

    @Test
    void keepsTheWholeRemainingTextAsTheObjective() {
        GoalCommand command = GoalCommand.parse(
                "/goal 收集十块铁，确认背包数量，同时不要破坏基地");
        assertEquals(GoalCommand.Action.SET, command.action());
        assertEquals("收集十块铁，确认背包数量，同时不要破坏基地", command.objective());
    }

    @Test
    void doesNotCaptureSimilarSlashCommandsOrNormalChat() {
        assertNull(GoalCommand.parse("/goals test"));
        assertNull(GoalCommand.parse("please use /goal later"));
        assertNull(GoalCommand.parse("goal test"));
    }
}
