/*
 * Copyright 2026 NeoMystiasIzakaya Team
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package icu.gensoukyo.neo_mystias_izakaya.datagen;

import icu.gensoukyo.neo_mystias_izakaya.registry.NMIVanillaTags;
import icu.gensoukyo.neo_mystias_izakaya.registry.item.NMIBeveragesItems;
import icu.gensoukyo.neo_mystias_izakaya.registry.item.NMICuisinesItems;
import icu.gensoukyo.neo_mystias_izakaya.registry.item.NMIIngredientItems;
import icu.gensoukyo.neo_mystias_izakaya.registry.item.NMIMainItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.data.ItemTagsProvider;
import org.jspecify.annotations.NonNull;

import java.util.concurrent.CompletableFuture;

public class NMIVanillaItemTagProvider extends ItemTagsProvider {
    public NMIVanillaItemTagProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider, String modId) {
        super(output, lookupProvider, modId);
    }

    @Override
    protected void addTags(HolderLookup.@NonNull Provider provider) {
        NMIIngredientItems.ITEM_LIST.forEach(item -> tag(NMIVanillaTags.INGREDIENT).add(item.get()));
        tag(NMIVanillaTags.INGREDIENT).add(
                Items.PUFFERFISH,
                Items.PUMPKIN,
                Items.KELP,
                Items.BROWN_MUSHROOM,
                Items.POTATO,
                Items.PORKCHOP,
                Items.BEEF,
                Items.EGG,
                Items.ICE,
                Items.HONEY_BOTTLE,
                Items.COCOA_BEANS,
                NMIMainItems.CHROME_BALL.get()
        );
        NMIBeveragesItems.ITEM_LIST.forEach(item -> tag(NMIVanillaTags.BEVERAGES).add(item.get()));
        NMICuisinesItems.ITEM_LIST.forEach(item -> tag(NMIVanillaTags.CUISINES).add(item.get()));

        tag(ItemTags.HEAD_ARMOR).add(NMIMainItems.MYSTIAS_HAT.get());
        tag(ItemTags.HEAD_ARMOR_ENCHANTABLE).add(NMIMainItems.MYSTIAS_HAT.get());
        tag(ItemTags.FREEZE_IMMUNE_WEARABLES).add(NMIMainItems.MYSTIAS_HAT.get());

        tag(NMIVanillaTags.CHROME_BALLS).add(NMIMainItems.CHROME_BALL.get());

    }
}
