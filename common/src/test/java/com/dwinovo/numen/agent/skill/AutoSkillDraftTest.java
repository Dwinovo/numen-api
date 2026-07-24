package com.dwinovo.numen.agent.skill;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoSkillDraftTest {

    private static final String DRAFT = """
            <skill>
            ---
            name: ../Mekanism Crusher Setup!!
            description: Configure a crusher safely after inspecting its GUI.
            ---
            # Goal
            Configure a modded crusher without assuming slot indices.
            # Procedure
            1. Inspect the machine and its GUI.
            2. Transfer input only after identifying the real input slot.
            # Verification and recovery
            Inspect storage again and verify that processing started.
            </skill>
            """;

    @Test
    void normalizesUnsafeModelNameAndRendersFrontmatter() {
        AutoSkillDraft.Draft draft = AutoSkillDraft.parse(DRAFT).orElseThrow();
        assertEquals("mekanism_crusher_setup", draft.name());
        assertTrue(draft.markdown().contains("generated: true"));
        assertTrue(draft.markdown().contains("# Verification and recovery"));
    }

    @Test
    void skipAndMalformedDraftsAreRejected() {
        assertTrue(AutoSkillDraft.parse("<skip>ordinary primitive action</skip>").isEmpty());
        assertTrue(AutoSkillDraft.parse("---\nname: x\n---\ntoo short").isEmpty());
    }

    @Test
    void registryInstallsAtomicallyAndNeverOverwrites(@TempDir Path root) throws Exception {
        SkillRegistry registry = SkillRegistry.instance();
        registry.scan(root);
        SkillInfo installed = registry.installGenerated(DRAFT).orElseThrow();
        Path file = root.resolve("mekanism_crusher_setup").resolve("SKILL.md");
        assertTrue(Files.isRegularFile(file));
        assertEquals(file.toAbsolutePath(), installed.location());

        String before = Files.readString(file);
        assertFalse(registry.installGenerated(DRAFT.replace("processing started", "different content")).isPresent());
        assertEquals(before, Files.readString(file));
    }
}
