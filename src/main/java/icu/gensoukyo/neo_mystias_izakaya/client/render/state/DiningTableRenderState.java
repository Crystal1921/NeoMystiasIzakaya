/*
 * Copyright 2026 NeoMystiasIzakaya Team
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package icu.gensoukyo.neo_mystias_izakaya.client.render.state;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.npc.villager.VillagerData;
import org.jspecify.annotations.Nullable;

public class DiningTableRenderState extends BlockEntityRenderState {
    public ItemStackRenderState cuisineRenderState;
    public ItemStackRenderState beverageRenderState;
    public int index;
    /** 普通客人对应的村民数据（随机职业 + 生物群系变种），null 表示不渲染村民 */
    public @Nullable VillagerData villagerData;
    /** 稀客对应的女仆渲染状态，null 表示不渲染女仆 */
    public @Nullable EntityRenderState maidRenderState;
    /** 村民/女仆围绕餐桌中心的随机放置角度（弧度） */
    public float villagerAngle;
}
