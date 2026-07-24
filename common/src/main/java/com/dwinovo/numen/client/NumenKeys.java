package com.dwinovo.numen.client;

import com.dwinovo.numen.Constants;
import com.dwinovo.numen.client.screen.NumenScreen;
import com.dwinovo.numen.mixin.KeyMappingAccessor;
import com.dwinovo.numen.mixin.OptionsAccessor;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Shared key mappings: defined once here, registered by each loader's client
 * init (Fabric {@code KeyMappingHelper} / NeoForge {@code RegisterKeyMappingsEvent}),
 * polled once per client tick via {@link #tick()}.
 */
public final class NumenKeys {

    /** Dedicated vanilla Controls category so the binding is easy to discover and rebind. */
    public static final String CATEGORY =
            com.dwinovo.numen.data.ModLanguageData.Keys.KEY_CATEGORY_NUMEN;

    /**
     * Open the companion roster panel (or straight into chat with a single pet).
     * G is only the default: Minecraft owns persistence and rebinding through
     * Options → Controls → Key Binds → Numen.
     */
    public static final KeyMapping OPEN_ROSTER = new KeyMapping(
            com.dwinovo.numen.data.ModLanguageData.Keys.KEY_OPEN_ROSTER,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY);

    /** The one Options instance whose native key array has already been verified. */
    private static Options verifiedOptions;

    private NumenKeys() {}

    /**
     * Loader APIs are the primary registration path. This common fallback verifies their observable
     * result after Minecraft construction, so an event-ordering or embedded-mod edge case cannot
     * leave a working default key absent from Options → Controls. Idempotent per Options instance.
     */
    public static void ensureRegisteredInOptions(Minecraft minecraft) {
        if (minecraft == null || minecraft.options == null || verifiedOptions == minecraft.options) return;
        Options options = minecraft.options;
        ensureCategoryOrder();
        KeyMapping[] updated = ensurePresent(options.keyMappings);
        if (updated != options.keyMappings) {
            restoreSavedBinding(options);
            ((OptionsAccessor) (Object) options).numen$setKeyMappings(updated);
            KeyMapping.resetMapping();
            Constants.LOG.warn("Numen key mapping was missing from Minecraft Options; restored it via common fallback");
        }
        verifiedOptions = options;
    }

    /** Visible for tests: append the canonical mapping once, preserving the original array if found. */
    static KeyMapping[] ensurePresent(KeyMapping[] current) {
        KeyMapping[] safe = current == null ? new KeyMapping[0] : current;
        for (int i = 0; i < safe.length; i++) {
            KeyMapping mapping = safe[i];
            if (mapping == OPEN_ROSTER) return safe;
            if (mapping != null && OPEN_ROSTER.getName().equals(mapping.getName())) {
                KeyMapping[] replaced = Arrays.copyOf(safe, safe.length);
                replaced[i] = OPEN_ROSTER;
                return replaced;
            }
        }
        KeyMapping[] appended = Arrays.copyOf(safe, safe.length + 1);
        appended[safe.length] = OPEN_ROSTER;
        return appended;
    }

    /** Parse the vanilla/NeoForge key line, dropping an optional NeoForge modifier suffix. */
    static String savedKeyName(List<String> lines) {
        if (lines == null) return null;
        String prefix = "key_" + OPEN_ROSTER.getName() + ":";
        for (String line : lines) {
            if (line == null || !line.startsWith(prefix)) continue;
            String value = line.substring(prefix.length()).trim();
            int modifier = value.indexOf(':');
            if (modifier >= 0) value = value.substring(0, modifier);
            return value.isBlank() ? null : value;
        }
        return null;
    }

    private static void restoreSavedBinding(Options options) {
        try {
            if (!Files.isRegularFile(options.getFile().toPath())) return;
            String saved = savedKeyName(Files.readAllLines(options.getFile().toPath()));
            if (saved != null) OPEN_ROSTER.setKey(InputConstants.getKey(saved));
        } catch (Exception ex) {
            Constants.LOG.warn("Could not restore Numen key from options.txt: {}", ex.toString());
        }
    }

    /** Vanilla 1.21.1's category comparator needs an explicit order for custom categories. */
    private static void ensureCategoryOrder() {
        try {
            Map<String, Integer> order = KeyMappingAccessor.numen$getCategorySortOrder();
            if (order.containsKey(CATEGORY)) return;
            int last = order.values().stream().mapToInt(Integer::intValue).max().orElse(0);
            order.put(CATEGORY, last + 1);
        } catch (RuntimeException | LinkageError | AssertionError ex) {
            // Fabric's KeyBindingHelper and NeoForge's patched comparator already handle this on
            // their normal paths. The accessor is an extra guard, never a reason to break startup.
            Constants.LOG.warn("Could not verify Numen key category ordering: {}", ex.toString());
        }
    }

    /** Per-client-tick poll; key presses only register while no screen is open. */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ensureRegisteredInOptions(mc);
        while (OPEN_ROSTER.consumeClick()) {
            if (mc.player != null && mc.screen == null) {
                NumenScreen.openWorkspace();
            }
        }
    }
}
