package com.dwinovo.numen.client.stt;

import com.dwinovo.numen.client.agent.NumenRoster;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GlobalPushToTalkTest {

    @Test
    void usesRememberedLiveCompanion() {
        UUID first = UUID.randomUUID();
        UUID remembered = UUID.randomUUID();
        var entries = List.of(
                new NumenRoster.Entry(first, "First"),
                new NumenRoster.Entry(remembered, "Companion"));

        assertEquals(remembered, GlobalPushToTalk.selectTarget(entries, remembered));
    }

    @Test
    void fallsBackToFirstLiveCompanion() {
        UUID first = UUID.randomUUID();
        var entries = List.of(new NumenRoster.Entry(first, "Companion"));

        assertEquals(first,
                GlobalPushToTalk.selectTarget(entries, UUID.randomUUID()));
    }

    @Test
    void returnsNullForEmptyRoster() {
        assertNull(GlobalPushToTalk.selectTarget(List.of(), UUID.randomUUID()));
    }
}
