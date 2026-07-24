package com.dwinovo.numen.agent.tool;

import com.dwinovo.numen.api.Internal;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Per-agent progressive-disclosure state for the global tool registry.
 *
 * <p>The stable system prompt receives only {@link #formatCatalogXml() layer-one
 * metadata}. The provider's request-local {@code tools} field receives a small
 * baseline plus tools explicitly loaded through {@code discover_tools}. Loaded
 * tools are bounded and expire after inactivity, so a long conversation does not
 * monotonically grow its active tool surface.
 *
 * <p>All methods are called on the client main thread. The registry supplier is
 * deliberately read afresh so MCP tools that connect or disconnect at runtime
 * appear in the catalogue without rebuilding an agent loop.
 */
@Internal
public final class ToolDisclosureSession {

    public static final String DISCOVERY_TOOL_NAME = "discover_tools";

    /** Maximum number of non-baseline schemas present in one request. */
    static final int MAX_EXPANDED_TOOLS = 8;
    /** Drop a disclosed tool after this many LLM turns without an invocation. */
    static final int ACTIVE_TURN_TTL = 6;
    /** Bound one discovery call so a model cannot defeat disclosure in one batch. */
    static final int MAX_DISCOVERY_NAMES = 8;

    /**
     * Small always-visible layer: discovery, task control, self perception,
     * planning, and Skill loading. Names absent from a deployment are ignored.
     */
    private static final Set<String> BASE_TOOL_NAMES = Set.of(
            "get_self_status",
            "look_around",
            "task_status",
            "task_stop",
            "todowrite",
            "load_skill");

    private static final Gson GSON = new Gson();

    private final Supplier<List<NumenTool>> catalogSupplier;
    /** accessOrder=true gives a bounded least-recently-used active set. */
    private final LinkedHashMap<String, Long> expanded =
            new LinkedHashMap<>(16, 0.75f, true);
    private final NumenTool discoveryTool = new DiscoveryTool();
    private long turn;

    /** Live production session backed by the mod-global registry. */
    public ToolDisclosureSession() {
        this(ToolRegistry::all);
    }

    /** Injectable catalogue constructor, primarily for deterministic tests and embedders. */
    public ToolDisclosureSession(Supplier<List<NumenTool>> catalogSupplier) {
        this.catalogSupplier = catalogSupplier == null ? List::of : catalogSupplier;
    }

    /** Start one new LLM pulse and expire schemas that have not been used recently. */
    public void beginTurn() {
        turn++;
        pruneExpired();
    }

    /** A new owner directive is a task boundary: converge back to the baseline. */
    public void resetForNewTask() {
        expanded.clear();
    }

    /**
     * Complete function definitions for this request only. The discovery tool is
     * always first, followed by baseline and currently disclosed tools in stable
     * registry order (important for provider prompt caching).
     */
    public List<NumenTool> toolsForRequest() {
        pruneExpired();
        List<NumenTool> out = new ArrayList<>(1 + BASE_TOOL_NAMES.size() + expanded.size());
        out.add(discoveryTool);
        for (NumenTool tool : catalog()) {
            String key = key(tool.name());
            if (DISCOVERY_TOOL_NAME.equals(key)) continue; // reserved virtual tool
            if (BASE_TOOL_NAMES.contains(key) || expanded.containsKey(key)) {
                out.add(tool);
            }
        }
        return out;
    }

    /**
     * Resolve only a currently visible tool for execution. If a model guesses a
     * known hidden tool despite not receiving its schema, return a safe repair
     * adapter: it reveals the schema for the next pulse but does not execute the
     * guessed arguments.
     */
    public NumenTool resolveForExecution(String requestedName) {
        String requestedKey = key(requestedName);
        if (DISCOVERY_TOOL_NAME.equals(requestedKey)) return discoveryTool;

        NumenTool actual = find(requestedName);
        if (actual == null) return null;
        String actualKey = key(actual.name());
        if (BASE_TOOL_NAMES.contains(actualKey)) return actual;
        if (expanded.containsKey(actualKey)) {
            expanded.put(actualKey, turn); // touch LRU + idle lease on real use
            return actual;
        }
        return new UndisclosedTool(actual);
    }

    /**
     * Layer-one catalogue: names and short summaries only. Full descriptions and
     * parameter schemas deliberately stay out of the conversation and are loaded
     * through the provider's request-local function list after discovery.
     */
    public String formatCatalogXml() {
        List<NumenTool> all = catalog();
        if (all.isEmpty()) return "";

        StringBuilder sb = new StringBuilder(512 + all.size() * 128);
        sb.append("<tool_disclosure>\n")
                .append("The catalogue below is metadata only. The complete schemas currently present in the API tool list are the only tools you may call. ")
                .append("When a catalogue entry is needed but its schema is absent, call discover_tools with its exact name first. ")
                .append("Never guess hidden parameters. Disclosures are request-local and may expire, so rediscover a tool if it is no longer present.\n")
                .append("<tool_catalog>\n");
        for (NumenTool tool : all) {
            if (DISCOVERY_TOOL_NAME.equals(key(tool.name()))) continue;
            sb.append("  <tool><name>").append(xml(tool.name())).append("</name><summary>")
                    .append(xml(tool.summary())).append("</summary></tool>\n");
        }
        sb.append("</tool_catalog>\n</tool_disclosure>");
        return sb.toString();
    }

    /** Visible for diagnostics/tests without exposing the mutable LRU map. */
    public Set<String> disclosedNames() {
        return Set.copyOf(expanded.keySet());
    }

    private String disclose(Collection<String> requestedNames) {
        JsonObject result = new JsonObject();
        JsonArray disclosed = new JsonArray();
        JsonArray already = new JsonArray();
        JsonArray missing = new JsonArray();
        JsonArray evicted = new JsonArray();

        if (requestedNames == null || requestedNames.isEmpty()) {
            result.addProperty("success", false);
            result.addProperty("message", "names must contain at least one exact tool name from <tool_catalog>");
            return GSON.toJson(result);
        }
        if (requestedNames.size() > MAX_DISCOVERY_NAMES) {
            result.addProperty("success", false);
            result.addProperty("message", "request at most " + MAX_DISCOVERY_NAMES + " tool names at a time");
            return GSON.toJson(result);
        }

        Set<String> seen = new LinkedHashSet<>();
        for (String requested : requestedNames) {
            if (requested == null || requested.isBlank()) continue;
            NumenTool tool = find(requested.strip());
            if (tool == null || DISCOVERY_TOOL_NAME.equals(key(tool.name()))) {
                missing.add(requested);
                continue;
            }
            String canonical = key(tool.name());
            if (!seen.add(canonical)) continue;
            if (BASE_TOOL_NAMES.contains(canonical)) {
                already.add(tool.name());
            } else {
                boolean wasActive = expanded.containsKey(canonical);
                expanded.put(canonical, turn);
                if (wasActive) already.add(tool.name());
                else disclosed.add(tool.name());
            }
        }

        while (expanded.size() > MAX_EXPANDED_TOOLS) {
            Map.Entry<String, Long> eldest = expanded.entrySet().iterator().next();
            expanded.remove(eldest.getKey());
            evicted.add(eldest.getKey());
        }

        boolean ok = disclosed.size() > 0 || already.size() > 0;
        result.addProperty("success", ok);
        result.addProperty("message", ok
                ? "Full definitions for the disclosed tools will be attached to the next request pulse."
                : "No matching tools were disclosed; use exact names from <tool_catalog>.");
        result.add("disclosed", disclosed);
        result.add("already_available", already);
        result.add("not_found", missing);
        result.add("evicted", evicted);
        return GSON.toJson(result);
    }

    private void pruneExpired() {
        expanded.entrySet().removeIf(e -> turn - e.getValue() > ACTIVE_TURN_TTL);
    }

    private List<NumenTool> catalog() {
        List<NumenTool> supplied = catalogSupplier.get();
        return supplied == null ? List.of() : supplied;
    }

    private NumenTool find(String requestedName) {
        if (requestedName == null) return null;
        for (NumenTool tool : catalog()) {
            if (tool.name().equals(requestedName)) return tool;
        }
        String wanted = key(requestedName);
        for (NumenTool tool : catalog()) {
            if (key(tool.name()).equals(wanted)) return tool;
        }
        return null;
    }

    private static String key(String name) {
        return name == null ? "" : name.strip().toLowerCase(Locale.ROOT);
    }

    private static String xml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private final class DiscoveryTool implements NumenTool {
        private record Args(List<String> names) {}

        @Override public String name() { return DISCOVERY_TOOL_NAME; }

        @Override
        public String description() {
            return "Reveal complete definitions and parameter schemas for up to "
                    + MAX_DISCOVERY_NAMES + " tools from <tool_catalog>. Use exact catalogue names. "
                    + "This only loads schemas for the next request; it does not perform the tools.";
        }

        @Override
        public String summary() {
            return "Load full schemas for selected tools from the metadata catalogue.";
        }

        @Override
        public Map<String, Object> parameterSchema() {
            return Schema.object().stringArray("names",
                    "Exact tool names from <tool_catalog> whose full schemas are needed.", 1).build();
        }

        @Override
        public void invoke(ToolCall call) {
            try {
                Args args = GSON.fromJson(call.rawArgs(), Args.class);
                call.complete(disclose(args == null ? null : args.names()));
            } catch (RuntimeException ex) {
                JsonObject result = new JsonObject();
                result.addProperty("success", false);
                result.addProperty("message", "invalid discover_tools arguments: " + ex.getMessage());
                call.complete(GSON.toJson(result));
            }
        }
    }

    /** Compatibility repair for a model that calls a catalogue name before loading its schema. */
    private final class UndisclosedTool implements NumenTool {
        private final NumenTool actual;

        private UndisclosedTool(NumenTool actual) {
            this.actual = actual;
        }

        @Override public String name() { return actual.name(); }
        @Override public String description() { return actual.description(); }
        @Override public String summary() { return actual.summary(); }
        @Override public Map<String, Object> parameterSchema() { return actual.parameterSchema(); }

        @Override
        public void invoke(ToolCall call) {
            disclose(List.of(actual.name()));
            JsonObject result = new JsonObject();
            result.addProperty("success", false);
            result.addProperty("code", "tool_not_disclosed");
            result.addProperty("message", "Tool '" + actual.name()
                    + "' was not active, so guessed arguments were not executed. Its full schema is now attached; call it again next turn using that schema.");
            call.complete(GSON.toJson(result));
        }
    }
}
