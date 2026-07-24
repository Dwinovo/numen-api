package com.dwinovo.numen.client.agent;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolExecutionGuardTest {

    @Test
    void eventArrivingDuringPlanningMakesReturnedToolBatchStale() {
        assertTrue(ToolExecutionGuard.hasNewInput(0, 1));
        assertTrue(ToolExecutionGuard.hasNewInput(1, 0));
        assertFalse(ToolExecutionGuard.hasNewInput(0, 0));
    }

    @Test
    void identicalGotoAfterSuccessfulCompletionIsRejectedWithinSameDirective() {
        String completed = ToolCallFingerprint.of("goto", "{\"x\":-3,\"y\":52,\"z\":-25}");

        assertTrue(ToolExecutionGuard.duplicatesCompleted(
                completed, 7, 7, 1_000, 2_000, 60_000, Set.of("wait"),
                "goto", "{\"z\":-25.0,\"x\":-3,\"y\":52.0}"));
    }

    @Test
    void newOwnerDirectiveDifferentTargetExpiryAndWaitRemainAllowed() {
        String completed = ToolCallFingerprint.of("goto", "{\"x\":-3,\"z\":-25}");

        assertFalse(ToolExecutionGuard.duplicatesCompleted(
                completed, 7, 8, 1_000, 2_000, 60_000, Set.of("wait"),
                "goto", "{\"x\":-3,\"z\":-25}"));
        assertFalse(ToolExecutionGuard.duplicatesCompleted(
                completed, 7, 7, 1_000, 2_000, 60_000, Set.of("wait"),
                "goto", "{\"x\":-3,\"z\":-26}"));
        assertFalse(ToolExecutionGuard.duplicatesCompleted(
                completed, 7, 7, 1_000, 100_000, 60_000, Set.of("wait"),
                "goto", "{\"x\":-3,\"z\":-25}"));

        String wait = ToolCallFingerprint.of("wait", "{\"seconds\":10}");
        assertFalse(ToolExecutionGuard.duplicatesCompleted(
                wait, 7, 7, 1_000, 2_000, 60_000, Set.of("wait"),
                "wait", "{\"seconds\":10}"));
    }
}
