/*
 * Copyright 2026 NeoMystiasIzakaya Team
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package icu.gensoukyo.neo_mystias_izakaya.datagen;

import com.mojang.math.Quadrant;
import icu.gensoukyo.neo_mystias_izakaya.NeoMystiasIzakaya;
import icu.gensoukyo.neo_mystias_izakaya.compat.ae2.registry.NMIMEBlocks;
import icu.gensoukyo.neo_mystias_izakaya.registry.NMIBlocks;
import icu.gensoukyo.neo_mystias_izakaya.registry.item.NMIBeveragesItems;
import icu.gensoukyo.neo_mystias_izakaya.registry.item.NMICuisinesItems;
import icu.gensoukyo.neo_mystias_izakaya.registry.item.NMIIngredientItems;
import icu.gensoukyo.neo_mystias_izakaya.registry.item.NMIMainItems;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.blockstates.PropertyDispatch;
import net.minecraft.client.data.models.model.ModelLocationUtils;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.renderer.block.dispatch.Variant;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.registries.DeferredItem;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class NMIItemModelProvider extends ModelProvider {
    public NMIItemModelProvider(PackOutput output) {
        super(output, NeoMystiasIzakaya.MODID);
    }

    private static void normalBlock(BlockModelGenerators blockModels, Block block) {
        Variant variant = new Variant(ModelLocationUtils.getModelLocation(block));
        blockModels.blockStateOutput.accept(MultiVariantGenerator
                .dispatch(block, BlockModelGenerators.variant(variant)));
    }

    private static void horizontallyBlock(BlockModelGenerators blockModels, Block block) {
        horizontallyBlock(blockModels, block, ModelLocationUtils.getModelLocation(block));
    }
    private static void horizontallyBlock(BlockModelGenerators blockModels, Block block,Identifier identifier) {
        Variant variant = new Variant(identifier);
        blockModels.blockStateOutput.accept(MultiVariantGenerator
                .dispatch(block, BlockModelGenerators.variant(variant))
                .with(PropertyDispatch.modify(BlockStateProperties.HORIZONTAL_FACING)
                        .select(Direction.NORTH, v -> v.withYRot(Quadrant.R0))
                        .select(Direction.EAST, v -> v.withYRot(Quadrant.R90))
                        .select(Direction.SOUTH, v -> v.withYRot(Quadrant.R180))
                        .select(Direction.WEST, v -> v.withYRot(Quadrant.R270))
                ));
    }

    private static void horizontallyLitBlock(BlockModelGenerators blockModels, Block block) {
        Identifier key = BuiltInRegistries.BLOCK.getKey(block);
        horizontallyLitBlock(blockModels, block, key);
    }

    private static void horizontallyLitBlock(BlockModelGenerators blockModels, Block block,Identifier identifier) {
        Identifier lit = identifier.withSuffix("_lit").withPrefix("block/");
        Variant variant = new Variant(identifier.withPrefix("block/"));
        blockModels.blockStateOutput.accept(MultiVariantGenerator
                .dispatch(block, BlockModelGenerators.variant(variant))
                .with(PropertyDispatch.modify(BlockStateProperties.HORIZONTAL_FACING, BlockStateProperties.LIT)
                        .select(Direction.NORTH, false, v -> v.withYRot(Quadrant.R0))
                        .select(Direction.EAST, false, v -> v.withYRot(Quadrant.R90))
                        .select(Direction.SOUTH, false, v -> v.withYRot(Quadrant.R180))
                        .select(Direction.WEST, false, v -> v.withYRot(Quadrant.R270))
                        .select(Direction.NORTH, true, v -> v.withYRot(Quadrant.R0).withModel(lit))
                        .select(Direction.EAST, true, v -> v.withYRot(Quadrant.R90).withModel(lit))
                        .select(Direction.SOUTH, true, v -> v.withYRot(Quadrant.R180).withModel(lit))
                        .select(Direction.WEST, true, v -> v.withYRot(Quadrant.R270).withModel(lit))
                ));
    }

    @Override
    protected void registerModels(@NotNull BlockModelGenerators blockModels, @NotNull ItemModelGenerators itemModels) {
        itemModels.generateFlatItem(NMIMainItems.CHROME_BALL.asItem(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(NMIMainItems.RECIPE_BOOK.asItem(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(NMIMainItems.CANTEEN_CONFIG.asItem(), ModelTemplates.FLAT_ITEM);
        itemModels.declareCustomModelItem(NMIMainItems.MYSTIAS_HAT.asItem());

        this.registerBlockModels(blockModels);
        this.registerItemModels(itemModels);
    }

    private void registerBlockModels(BlockModelGenerators blockModels) {
        horizontallyBlock(blockModels, NMIBlocks.BOILING_POT.get());
        horizontallyLitBlock(blockModels, NMIBlocks.GRILL.get());
        horizontallyBlock(blockModels, NMIBlocks.FRYING_PAN.get());
        horizontallyBlock(blockModels, NMIBlocks.STEAMER.get());
        horizontallyBlock(blockModels, NMIBlocks.CUTTING_BOARD.get());
        horizontallyBlock(blockModels, NMIBlocks.CANTEEN.get());
        normalBlock(blockModels, NMIBlocks.DINING_TABLE.get());
        horizontallyBlock(blockModels, NMIBlocks.STORE.get());
        horizontallyBlock(blockModels, NMIBlocks.CUPBOARD.get());
        horizontallyBlock(blockModels, NMIBlocks.CREATIVE_CUPBOARD.get());
        horizontallyBlock(blockModels, NMIBlocks.INCUBATOR.get());
        horizontallyBlock(blockModels, NMIBlocks.CREATIVE_INCUBATOR.get());
        horizontallyBlock(blockModels, NMIMEBlocks.ME_CUPBOARD.get());
        horizontallyBlock(blockModels, NMIMEBlocks.ME_INCUBATOR.get());
    }

    private void registerItemModels(ItemModelGenerators itemModels) {
        for (List<DeferredItem<Item>> items : List.of(NMIBeveragesItems.ITEM_LIST, NMICuisinesItems.ITEM_LIST, NMIIngredientItems.ITEM_LIST)) {
            for (DeferredItem<Item> item : items) {
                itemModels.generateFlatItem(item.asItem(), ModelTemplates.FLAT_ITEM);
            }
        }
    }
}
