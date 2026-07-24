package com.dwinovo.numen.mixin;

import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/** Accesses vanilla's fixed category-order table so the Numen category is sortable on 1.21.1. */
@Mixin(KeyMapping.class)
public interface KeyMappingAccessor {

    @Accessor("CATEGORY_SORT_ORDER")
    static Map<String, Integer> numen$getCategorySortOrder() {
        throw new AssertionError("mixin accessor was not transformed");
    }
}
