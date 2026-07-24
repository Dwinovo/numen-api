package com.dwinovo.numen.agent.tool;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolDisclosureSessionTest {

    @Test
    void firstPulseContainsOnlyDiscoveryAndSmallBaseline() {
        var session = session(
                tool("get_self_status", "Read self status. FULL CORE DETAIL", "core_secret"),
                tool("build", "Build explicit blocks. FULL BUILD DETAIL", "cells"),
                tool("auto_mine", "Mine matching blocks. FULL MINE DETAIL", "blocks"));

        session.beginTurn();
        assertEquals(List.of("discover_tools", "get_self_status"), names(session.toolsForRequest()));

        String catalog = session.formatCatalogXml();
        assertTrue(catalog.contains("<name>build</name>"));
        assertTrue(catalog.contains("Build explicit blocks."));
        assertFalse(catalog.contains("FULL BUILD DETAIL"));
        assertFalse(catalog.contains("cells"), "parameter schemas must not leak into layer-one metadata");
    }

    @Test
    void discoveryLoadsFullSchemaOnlyForFollowingPulses() {
        var session = session(
                tool("get_self_status", "Read self status.", "core_secret"),
                tool("build", "Build explicit blocks.", "cells"),
                tool("auto_mine", "Mine matching blocks.", "blocks"));
        session.beginTurn();

        String result = invoke(session.resolveForExecution("discover_tools"),
                "{\"names\":[\"build\"]}");
        assertTrue(result.contains("\"success\":true"));
        assertTrue(result.contains("\"disclosed\":[\"build\"]"));
        assertFalse(result.contains("cells"), "full schema belongs to the request tool field, not history");

        assertEquals(List.of("discover_tools", "get_self_status", "build"),
                names(session.toolsForRequest()));
        assertNotNull(session.resolveForExecution("BUILD"), "lookup remains case-insensitive");
        assertNull(session.resolveForExecution("does_not_exist"));
    }

    @Test
    void guessedHiddenToolIsNotExecutedButIsSafelyDisclosed() {
        CountingTool hidden = new CountingTool("build");
        var session = session(hidden);
        session.beginTurn();

        NumenTool repair = session.resolveForExecution("build");
        assertNotNull(repair);
        String result = invoke(repair, "{\"guessed\":true}");

        assertEquals(0, hidden.calls);
        assertTrue(result.contains("tool_not_disclosed"));
        assertTrue(names(session.toolsForRequest()).contains("build"));
    }

    @Test
    void activeSchemasConvergeByTaskBoundaryAndIdleLease() {
        var session = session(tool("build", "Build.", "cells"));
        session.beginTurn();
        invoke(session.resolveForExecution("discover_tools"), "{\"names\":[\"build\"]}");
        assertTrue(names(session.toolsForRequest()).contains("build"));

        for (int i = 0; i <= ToolDisclosureSession.ACTIVE_TURN_TTL; i++) {
            session.beginTurn();
        }
        assertFalse(names(session.toolsForRequest()).contains("build"));

        invoke(session.resolveForExecution("discover_tools"), "{\"names\":[\"build\"]}");
        assertTrue(names(session.toolsForRequest()).contains("build"));
        session.resetForNewTask();
        assertFalse(names(session.toolsForRequest()).contains("build"));
    }

    @Test
    void expandedSetIsBoundedAndEvictsLeastRecentlyUsed() {
        List<NumenTool> tools = new ArrayList<>();
        for (int i = 0; i < 10; i++) tools.add(tool("tool_" + i, "Tool " + i + ".", "arg"));
        var session = new ToolDisclosureSession(() -> tools);
        session.beginTurn();

        invoke(session.resolveForExecution("discover_tools"),
                "{\"names\":[\"tool_0\",\"tool_1\",\"tool_2\",\"tool_3\",\"tool_4\",\"tool_5\",\"tool_6\",\"tool_7\"]}");
        // Touch tool_0 so tool_1 becomes the least-recently-used entry.
        assertNotNull(session.resolveForExecution("tool_0"));
        invoke(session.resolveForExecution("discover_tools"),
                "{\"names\":[\"tool_8\",\"tool_9\"]}");

        assertEquals(ToolDisclosureSession.MAX_EXPANDED_TOOLS, session.disclosedNames().size());
        assertTrue(session.disclosedNames().contains("tool_0"));
        assertFalse(session.disclosedNames().contains("tool_1"));
        assertFalse(session.disclosedNames().contains("tool_2"));
    }

    private static ToolDisclosureSession session(NumenTool... tools) {
        return new ToolDisclosureSession(() -> List.of(tools));
    }

    private static List<String> names(List<NumenTool> tools) {
        return tools.stream().map(NumenTool::name).toList();
    }

    private static String invoke(NumenTool tool, String args) {
        AtomicReference<String> result = new AtomicReference<>();
        tool.invoke(new ToolCall("call-1", tool.name(), args,
                new ClientToolContext(null, UUID.randomUUID()), result::set));
        return result.get();
    }

    private static NumenTool tool(String name, String description, String parameter) {
        return new NumenTool() {
            @Override public String name() { return name; }
            @Override public String description() { return description; }
            @Override public Map<String, Object> parameterSchema() {
                return Schema.object().string(parameter, "FULL PARAMETER DETAIL").build();
            }
            @Override public void invoke(ToolCall call) { call.complete("{\"success\":true}"); }
        };
    }

    private static final class CountingTool implements NumenTool {
        private final String name;
        private int calls;

        private CountingTool(String name) { this.name = name; }
        @Override public String name() { return name; }
        @Override public String description() { return "A hidden destructive tool."; }
        @Override public Map<String, Object> parameterSchema() { return Schema.none(); }
        @Override public void invoke(ToolCall call) {
            calls++;
            call.complete("{\"success\":true}");
        }
    }
}
