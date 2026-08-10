/*
 * Copyright 2026 NeoMystiasIzakaya Team
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package icu.gensoukyo.neo_mystias_izakaya.compat.ae2;


import icu.gensoukyo.neo_mystias_izakaya.NeoMystiasIzakaya;
import icu.gensoukyo.neo_mystias_izakaya.compat.ae2.registry.NMIMEBlockEntities;
import icu.gensoukyo.neo_mystias_izakaya.compat.ae2.registry.NMIMEBlocks;
import icu.gensoukyo.neo_mystias_izakaya.compat.ae2.registry.NMIMEItems;
import icu.gensoukyo.neo_mystias_izakaya.registry.NMICreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

@Mod(value = NeoMystiasIzakaya.MODID, depends = "ae2")
public class NeoMystiasIzakayaAe2Compact {

    public NeoMystiasIzakayaAe2Compact(IEventBus modEventBus, ModContainer modContainer) {
        NMIMEBlocks.BLOCKS.register(modEventBus);
        NMIMEItems.ITEMS.register(modEventBus);
        NMIMEBlockEntities.BLOCK_ENTITY_TYPES.register(modEventBus);

        modEventBus.register(NeoMystiasIzakayaAe2Compact.class);
    }

    @SubscribeEvent
    private static void onCreativeModeTabBuild(BuildCreativeModeTabContentsEvent event) {
        if (event.getTab() == NMICreativeModeTabs.MAIN.get()) {
            NMIMEItems.ITEMS.getEntries().forEach(
                    item -> event.accept(item.get())
            );
        }
    }
}
