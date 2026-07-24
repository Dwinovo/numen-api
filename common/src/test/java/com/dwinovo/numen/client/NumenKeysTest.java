package com.dwinovo.numen.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
}
