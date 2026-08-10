/*
 * Copyright 2026 NeoMystiasIzakaya Team
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package icu.gensoukyo.neo_mystias_izakaya.common.block;

import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * 支持通过方块状态控制是否渲染模型的抽象基类。
 * <p>
 * 新增 {@link #INVISIBLE} 方块状态（默认为 {@code false}）：当其为 {@code true} 时
 * {@link #getRenderShape} 返回 {@link RenderShape#INVISIBLE}，即不渲染方块模型。
 */
public abstract class NMIInvisibleBlock extends BaseEntityBlock {

    /**
     * 是否隐藏模型（true = 不渲染方块模型）
     */
    public static final BooleanProperty INVISIBLE = BooleanProperty.create("invisible");

    protected NMIInvisibleBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultState());
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return state.getValue(INVISIBLE) ? RenderShape.INVISIBLE : RenderShape.MODEL;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> pBuilder) {
        pBuilder.add(INVISIBLE);
        this.appendBlockStateDefinition(pBuilder);
    }

    /**
     * 构建默认方块状态（INVISIBLE 为 false）。
     * <p>
     * 注意：{@link BooleanProperty} 在 {@link StateDefinition#any()} 中的默认值是 {@code true}，
     * 因此子类注册默认状态时务必基于此方法，例如：
     * <pre>{@code registerDefaultState(this.defaultState().setValue(...));}</pre>
     */
    protected final BlockState defaultState() {
        return this.stateDefinition.any().setValue(INVISIBLE, false);
    }

    /**
     * 子类在此追加各自的方块状态属性
     */
    protected abstract void appendBlockStateDefinition(StateDefinition.Builder<Block, BlockState> pBuilder);
}
