package com.dwinovo.numen.mixin;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Mutable bridge used only by the loader-registration fallback. */
@Mixin(Options.class)
public interface OptionsAccessor {

    @Mutable
    @Accessor("keyMappings")
    void numen$setKeyMappings(KeyMapping[] mappings);
}
