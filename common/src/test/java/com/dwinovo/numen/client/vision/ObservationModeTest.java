package com.dwinovo.numen.client.vision;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ObservationModeTest {

    @Test
    void parsesAliasesAndDefaultsSafelyToStructured() {
        assertEquals(ObservationMode.STRUCTURED, ObservationMode.parse(null));
        assertEquals(ObservationMode.STRUCTURED, ObservationMode.parse("unexpected"));
        assertEquals(ObservationMode.STRUCTURED, ObservationMode.parse("off"));
        assertEquals(ObservationMode.HYBRID, ObservationMode.parse("hybrid"));
        assertEquals(ObservationMode.VISUAL, ObservationMode.parse("pure-visual"));
    }
}
