/*
 * Copyright 2026 NeoMystiasIzakaya Team
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package icu.gensoukyo.neo_mystias_izakaya.common.block;

import com.mojang.serialization.MapCodec;
import icu.gensoukyo.neo_mystias_izakaya.NeoMystiasIzakaya;
import icu.gensoukyo.neo_mystias_izakaya.common.blockentity.DiningTableBlockEntity;
import icu.gensoukyo.neo_mystias_izakaya.common.util.NMICommonItemTagUtil;
import icu.gensoukyo.neo_mystias_izakaya.common.util.NMIServerRecipeUtil;
import icu.gensoukyo.neo_mystias_izakaya.content.tag.ItemTagList;
import icu.gensoukyo.neo_mystias_izakaya.registry.NMIBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class DiningTableBlock extends BaseEntityBlock {

    public static final MapCodec<DiningTableBlock> CODEC = simpleCodec(DiningTableBlock::new);

    /** 桌面 (高 14-16) + 四条桌腿 */
    public static final VoxelShape SHAPE = makeShape();

    public DiningTableBlock(Properties properties) {
        super(properties
                .setId(ResourceKey.create(Registries.BLOCK, NeoMystiasIzakaya.id("dining_table")))
                .noOcclusion()
                .destroyTime(2.0F)
                .sound(SoundType.WOOD)
                .requiresCorrectToolForDrops());
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos blockPos, BlockState blockState) {
        return new DiningTableBlockEntity(blockPos, blockState);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState blockState, BlockEntityType<T> type) {
        return createDiningTableTicker(level, type, NMIBlockEntities.DINING_TABLE.get());
    }

    @Nullable
    protected static <T extends BlockEntity> BlockEntityTicker<T> createDiningTableTicker(
            Level pLevel, BlockEntityType<T> pServerType, BlockEntityType<? extends DiningTableBlockEntity> pClientType
    ) {
        return pLevel.isClientSide() ? null : createTickerHelper(pServerType, pClientType, DiningTableBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack itemStack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (level.isClientSide() || hand != InteractionHand.MAIN_HAND) {
            return super.useItemOn(itemStack, state, level, pos, player, hand, hitResult);
        }

        if (level.getBlockEntity(pos) instanceof DiningTableBlockEntity diningTableBlockEntity) {
            // 无顾客时不允许交互物品
            if (!diningTableBlockEntity.isOccupied()) {
                return InteractionResult.FAIL;
            }

            if (!itemStack.isEmpty()) {
                // 手持物品 → 尝试放入（若槽位已有则交换给玩家）
                ItemTagList itemTagList = NMICommonItemTagUtil.get(itemStack);
                if (itemTagList != null && !itemTagList.isEmpty()) {
                    if (itemTagList.hasBeveragesTag()) {
                        swapOrPlace(player, itemStack, diningTableBlockEntity, true);
                        return InteractionResult.SUCCESS;
                    }
                    if (NMIServerRecipeUtil.isCuisine(itemStack)) {
                        swapOrPlace(player, itemStack, diningTableBlockEntity, false);
                        return InteractionResult.SUCCESS;
                    }
                }
            } else {
                // 空手 → 先取菜品，后取酒水
                if (!diningTableBlockEntity.getCuisine().isEmpty()) {
                    player.setItemInHand(hand, diningTableBlockEntity.getCuisine().copy());
                    diningTableBlockEntity.clearCuisine();
                    return InteractionResult.SUCCESS;
                }
                if (!diningTableBlockEntity.getBeverage().isEmpty()) {
                    player.setItemInHand(hand, diningTableBlockEntity.getBeverage().copy());
                    diningTableBlockEntity.clearBeverage();
                    return InteractionResult.SUCCESS;
                }
            }
        }

        return super.useItemOn(itemStack, state, level, pos, player, hand, hitResult);
    }

    /**
     * 将手持物品放入餐桌指定槽位，若槽位已有物品则交换给玩家
     * @param isBeverage true=饮品槽, false=菜品槽
     */
    private void swapOrPlace(Player player, ItemStack heldItem, DiningTableBlockEntity table, boolean isBeverage) {
        ItemStack existing = isBeverage ? table.getBeverage() : table.getCuisine();
        ItemStack copy = heldItem.copy();
        copy.setCount(1);
        if (isBeverage) {
            table.setBeverage(copy);
        } else {
            table.setCuisine(copy);
        }
        heldItem.shrink(1);
        if (!existing.isEmpty() && !player.getInventory().add(existing)) {
            player.drop(existing, false);
        }
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext pContext) {
        return defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, pContext.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> pBuilder) {
        pBuilder.add(BlockStateProperties.HORIZONTAL_FACING);
    }

    @Override
    @SuppressWarnings("all")
    protected RenderShape getRenderShape(BlockState pState) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    public static VoxelShape makeShape(){
        VoxelShape shape = Shapes.empty();
        shape = Shapes.join(shape, Shapes.box(0.375, 0, 0.375, 0.625, 0.25, 0.625), BooleanOp.OR);
        shape = Shapes.join(shape, Shapes.box(0.4375, 0.25, 0.4375, 0.5625, 0.8125, 0.5625), BooleanOp.OR);
        shape = Shapes.join(shape, Shapes.box(0.3125, 0.8125, 0.3125, 0.6875, 0.875, 0.6875), BooleanOp.OR);
        shape = Shapes.join(shape, Shapes.box(0, 0.875, 0, 1, 1, 1), BooleanOp.OR);

        return shape;
    }
}
