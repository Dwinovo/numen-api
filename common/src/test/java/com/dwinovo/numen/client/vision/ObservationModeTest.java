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

    @Test
    void usesModeSpecificImageBudgetsWithoutUpscaling() {
        VisionCaptureProfile hybrid = VisionCaptureProfile.forMode(ObservationMode.HYBRID);
        VisionCaptureProfile visual = VisionCaptureProfile.forMode(ObservationMode.VISUAL);
        assertEquals("low", hybrid.detail());
        assertEquals("high", visual.detail());
        assertEquals(960, hybrid.dimensionsFor(1920, 1080)[0]);
        assertEquals(720, visual.dimensionsFor(1920, 1080)[1]);
        assertEquals(640, visual.dimensionsFor(640, 360)[0]);
    }
}
