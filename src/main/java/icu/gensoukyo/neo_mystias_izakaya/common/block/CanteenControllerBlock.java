/*
 * Copyright 2026 NeoMystiasIzakaya Team
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package icu.gensoukyo.neo_mystias_izakaya.common.block;

import com.mojang.serialization.MapCodec;
import icu.gensoukyo.neo_mystias_izakaya.client.util.NMIClientUtil;
import icu.gensoukyo.neo_mystias_izakaya.common.blockentity.CanteenControllerBlockEntity;
import icu.gensoukyo.neo_mystias_izakaya.registry.NMIBlockEntities;
import icu.gensoukyo.neo_mystias_izakaya.registry.item.NMIMainItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

public class CanteenControllerBlock extends NMIInvisibleBlock {
    public static final EnumProperty<CanteenPart> PART = EnumProperty.create("part", CanteenPart.class);
    public static final MapCodec<CanteenControllerBlock> CODEC = simpleCodec(CanteenControllerBlock::new);

    public CanteenControllerBlock(Properties properties) {
        super(properties.noOcclusion());
        this.registerDefaultState(this.defaultState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
                .setValue(PART, CanteenPart.MAIN)
        );
    }

    @Nullable
    protected static <T extends BlockEntity> BlockEntityTicker<T> createCounterTicker(
            Level pLevel, BlockEntityType<T> pServerType, BlockEntityType<? extends CanteenControllerBlockEntity> pClientType
    ) {
        return pLevel.isClientSide() ? null : createTickerHelper(pServerType, pClientType, CanteenControllerBlockEntity::serverTick);
    }

    private static Direction getNeighbourDirection(CanteenPart part, Direction facing) {
        return part == CanteenPart.MAIN ? facing.getCounterClockWise() : facing.getClockWise();
    }

    @Override
    protected void appendBlockStateDefinition(StateDefinition.Builder<Block, BlockState> pBuilder) {
        pBuilder.add(BlockStateProperties.HORIZONTAL_FACING, PART);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext pContext) {
        Direction facing = pContext.getHorizontalDirection();
        BlockPos pos = pContext.getClickedPos();
        BlockPos relative = pos.relative(facing.getCounterClockWise());
        Level level = pContext.getLevel();
        return level.getBlockState(relative).canBeReplaced(pContext) && level.getWorldBorder().isWithinBounds(relative)
                ? defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing)
                : null;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos blockPos, BlockState blockState) {
        // MAIN 和 EXTENSION 均创建 BE，EXTENSION 内部自动代理到 MAIN
        return new CanteenControllerBlockEntity(blockPos, blockState);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState blockState, BlockEntityType<T> type) {
        // 仅 MAIN 执行 tick
        return blockState.getValue(PART) == CanteenPart.MAIN
                ? createCounterTicker(level, type, NMIBlockEntities.COUNTER.get())
                : null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack itemStack) {
        super.setPlacedBy(level, pos, state, by, itemStack);
        if (!level.isClientSide()) {
            BlockPos otherPos = pos.relative(state.getValue(BlockStateProperties.HORIZONTAL_FACING).getCounterClockWise());
            level.setBlock(otherPos, state.setValue(PART, CanteenPart.EXTENSION).setValue(INVISIBLE, true), 3);
            level.updateNeighborsAt(pos, Blocks.AIR);
            state.updateNeighbourShapes(level, pos, 3);
        }
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && player.preventsBlockDrops()) {
            CanteenPart part = state.getValue(PART);
            if (part == CanteenPart.MAIN) {
                BlockPos otherPos = pos.relative(getNeighbourDirection(part, state.getValue(BlockStateProperties.HORIZONTAL_FACING)));
                BlockState otherState = level.getBlockState(otherPos);
                if (otherState.is(this) && otherState.getValue(PART) == CanteenPart.EXTENSION) {
                    level.setBlock(otherPos, Blocks.AIR.defaultBlockState(), 35);
                    level.levelEvent(player, 2001, otherPos, Block.getId(otherState));
                }
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        if (directionToNeighbour != getNeighbourDirection(state.getValue(PART), state.getValue(BlockStateProperties.HORIZONTAL_FACING))) {
            return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
        } else {
            return neighbourState.is(this) && neighbourState.getValue(PART) != state.getValue(PART)
                    ? state
                    : Blocks.AIR.defaultBlockState();
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!player.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            NMIClientUtil.openCanteenScreen(pos);
            return InteractionResult.SUCCESS;
        }
        // 服务端：手持配置器时显示状态
        if (level.getBlockEntity(pos) instanceof CanteenControllerBlockEntity controller) {
            if (player.getMainHandItem().is(NMIMainItems.CANTEEN_CONFIG)) {
                player.sendSystemMessage(
                        Component.translatable("block.neo_mystias_izakaya.canteen_controller.status",
                                controller.getKitchenwareList().size(),
                                controller.getDiningTableList().size())
                );
            }
        }
        return InteractionResult.SUCCESS;
    }

    public enum CanteenPart implements StringRepresentable {
        MAIN("main"),
        EXTENSION("extension");

        private final String name;

        CanteenPart(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }
}
