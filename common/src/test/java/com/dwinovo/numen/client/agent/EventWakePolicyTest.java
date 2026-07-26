package com.dwinovo.numen.client.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventWakePolicyTest {

    @Test
    void ambientIdleEventRidesUntilAnotherTurn() {
        assertFalse(EventWakePolicy.shouldStartTurn(false, false, false));
    }

    @Test
    void eachExplicitWakeSourceCanStartAnIdleTurn() {
        assertTrue(EventWakePolicy.shouldStartTurn(true, false, false));
        assertTrue(EventWakePolicy.shouldStartTurn(false, true, false));
        assertTrue(EventWakePolicy.shouldStartTurn(false, false, true));
    }
}
