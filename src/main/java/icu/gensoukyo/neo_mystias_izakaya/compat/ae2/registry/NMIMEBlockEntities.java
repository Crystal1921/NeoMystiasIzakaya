/*
 * Copyright 2026 NeoMystiasIzakaya Team
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package icu.gensoukyo.neo_mystias_izakaya.compat.ae2.registry;

import appeng.block.AEBaseEntityBlock;
import appeng.blockentity.AEBaseBlockEntity;
import appeng.blockentity.ClientTickingBlockEntity;
import appeng.blockentity.ServerTickingBlockEntity;
import com.google.common.base.Preconditions;
import icu.gensoukyo.neo_mystias_izakaya.NeoMystiasIzakaya;
import icu.gensoukyo.neo_mystias_izakaya.compat.ae2.blockentity.MECupboardBlockEntity;
import icu.gensoukyo.neo_mystias_izakaya.compat.ae2.blockentity.MEIncubatorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class NMIMEBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, NeoMystiasIzakaya.MODID);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MECupboardBlockEntity>> ME_CUPBOARD = create("me_cupboard",MECupboardBlockEntity.class, MECupboardBlockEntity::new, NMIMEBlocks.ME_CUPBOARD);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MEIncubatorBlockEntity>> ME_INCUBATOR = create("me_incubator",MEIncubatorBlockEntity.class, MEIncubatorBlockEntity::new, NMIMEBlocks.ME_INCUBATOR);

    @SafeVarargs
    private static <T extends AEBaseBlockEntity> DeferredHolder<BlockEntityType<?>, BlockEntityType<T>> create(String shortId,
                                                                                                               Class<T> entityClass,
                                                                                                               BlockEntityFactory<T> factory,
                                                                                                               DeferredBlock<? extends AEBaseEntityBlock<?>>... blockDefinitions) {
        Preconditions.checkArgument(blockDefinitions.length > 0);

        var deferred = BLOCK_ENTITY_TYPES.register(shortId, () -> {
            AtomicReference<BlockEntityType<T>> typeHolder = new AtomicReference<>();
            BlockEntityType.BlockEntitySupplier<T> supplier = (blockPos, blockState) -> factory.create(typeHolder.get(),
                    blockPos, blockState);

            var blocks = Arrays.stream(blockDefinitions)
                    .map(DeferredBlock::get)
                    .toArray(AEBaseEntityBlock[]::new);

            var type = new BlockEntityType<>(supplier, blocks);
            typeHolder.setPlain(type); // Makes it available to the supplier used above

            AEBaseBlockEntity.registerBlockEntityItem(type, blockDefinitions[0].asItem());

            // If the block entity classes implement specific interfaces, automatically register them
            // as tickers with the blocks that create that entity.
            BlockEntityTicker<T> serverTicker = null;
            if (ServerTickingBlockEntity.class.isAssignableFrom(entityClass)) {
                serverTicker = (level, pos, state, entity) -> {
                    ((ServerTickingBlockEntity) entity).serverTick();
                };
            }
            BlockEntityTicker<T> clientTicker = null;
            if (ClientTickingBlockEntity.class.isAssignableFrom(entityClass)) {
                clientTicker = (level, pos, state, entity) -> {
                    ((ClientTickingBlockEntity) entity).clientTick();
                };
            }

            for (var block : blocks) {
                AEBaseEntityBlock<T> baseBlock = (AEBaseEntityBlock<T>) block;
                baseBlock.setBlockEntity(entityClass, type, clientTicker, serverTicker);
            }

            return type;
        });

        return deferred;
    }

    @FunctionalInterface
    interface BlockEntityFactory<T extends AEBaseBlockEntity> {
        T create(BlockEntityType<T> type, BlockPos pos, BlockState state);
    }
}
