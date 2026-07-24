package com.dwinovo.numen.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NumenKeysTest {

    @Test
    void rosterKeyUsesDedicatedMinecraftControlsCategoryAndCanBeRebound() {
        KeyMapping mapping = NumenKeys.OPEN_ROSTER;
        assertEquals("key.categories.numen", mapping.getCategory());
        assertEquals(GLFW.GLFW_KEY_G, mapping.getDefaultKey().getValue());
        assertTrue(mapping.isDefault());

        try {
            mapping.setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_N));
            KeyMapping.resetMapping();
            assertFalse(mapping.isDefault());
            assertEquals("key.keyboard.n", mapping.saveString());
        } finally {
            mapping.setKey(mapping.getDefaultKey());
            KeyMapping.resetMapping();
        }
        assertTrue(mapping.isDefault());
    }

    @Test
    void optionsFallbackAppendsExactlyOnce() {
        KeyMapping other = new KeyMapping("key.test.other", InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_H, KeyMapping.CATEGORY_MISC);
        KeyMapping[] original = {other};
        KeyMapping[] appended = NumenKeys.ensurePresent(original);

        assertEquals(2, appended.length);
        assertSame(NumenKeys.OPEN_ROSTER, appended[1]);
        assertSame(appended, NumenKeys.ensurePresent(appended));
    }

    @Test
    void fallbackReadsVanillaAndNeoForgeSavedBindings() {
        assertEquals("key.keyboard.n", NumenKeys.savedKeyName(java.util.List.of(
                "version:3955", "key_key.numen.open_roster:key.keyboard.n")));
        assertEquals("key.keyboard.p", NumenKeys.savedKeyName(java.util.List.of(
                "key_key.numen.open_roster:key.keyboard.p:NONE")));
        assertNull(NumenKeys.savedKeyName(java.util.List.of("key_key.jump:key.keyboard.space")));
    }
}
