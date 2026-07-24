package com.dwinovo.numen.client.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ToolCallFingerprintTest {

    @Test
    void objectOrderAndEquivalentNumbersProduceSameIdentity() {
        assertEquals(
                ToolCallFingerprint.of("goto", "{\"x\":-3,\"y\":52.0,\"z\":-25}"),
                ToolCallFingerprint.of(" GOTO ", "{\"z\":-25.0,\"x\":-3.00,\"y\":52}"));
    }

    @Test
    void numericStringsRemainDifferentFromSchemaNumbers() {
        assertNotEquals(
                ToolCallFingerprint.of("goto", "{\"x\":-3}"),
                ToolCallFingerprint.of("goto", "{\"x\":\"-3\"}"));
    }

    @Test
    void differentArgumentsDoNotCollide() {
        assertNotEquals(
                ToolCallFingerprint.of("goto", "{\"x\":-3,\"z\":-25}"),
                ToolCallFingerprint.of("goto", "{\"x\":-3,\"z\":-26}"));
    }
}
