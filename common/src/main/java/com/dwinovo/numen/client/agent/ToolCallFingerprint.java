package com.dwinovo.numen.client.agent;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Canonical identity for detecting an identical tool call after it already completed. */
final class ToolCallFingerprint {

    private static final Gson GSON = new Gson();

    private ToolCallFingerprint() {}

    static String of(String toolName, String arguments) {
        String name = toolName == null ? "" : toolName.strip().toLowerCase(Locale.ROOT);
        try {
            JsonElement parsed = JsonParser.parseString(arguments == null || arguments.isBlank()
                    ? "{}" : arguments);
            StringBuilder canonical = new StringBuilder(name.length() + 64);
            canonical.append(name).append(':');
            append(parsed, canonical);
            return canonical.toString();
        } catch (RuntimeException malformed) {
            return name + ":raw:" + (arguments == null ? "" : arguments.strip());
        }
    }

    private static void append(JsonElement element, StringBuilder out) {
        if (element == null || element.isJsonNull()) {
            out.append("null");
            return;
        }
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            List<String> keys = new ArrayList<>(object.keySet());
            keys.sort(Comparator.naturalOrder());
            out.append('{');
            for (int i = 0; i < keys.size(); i++) {
                if (i > 0) out.append(',');
                String key = keys.get(i);
                out.append(GSON.toJson(key)).append(':');
                append(object.get(key), out);
            }
            out.append('}');
            return;
        }
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            out.append('[');
            for (int i = 0; i < array.size(); i++) {
                if (i > 0) out.append(',');
                append(array.get(i), out);
            }
            out.append(']');
            return;
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (primitive.isNumber()) {
            try {
                BigDecimal number = new BigDecimal(primitive.getAsString()).stripTrailingZeros();
                out.append(number.signum() == 0 ? "0" : number.toPlainString());
            } catch (NumberFormatException badNumber) {
                out.append(primitive);
            }
        } else if (primitive.isBoolean()) {
            out.append(primitive.getAsBoolean());
        } else {
            out.append(GSON.toJson(primitive.getAsString()));
        }
    }
}
