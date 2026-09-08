/*
 * Copyright 2026 NeoMystiasIzakaya Team
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package icu.gensoukyo.neo_mystias_izakaya.compat.ae2.blockentity;

import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.util.AECableType;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.core.settings.TickRates;
import icu.gensoukyo.neo_mystias_izakaya.compat.ae2.resource.MEStorageInvHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.transfer.EmptyResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

import java.util.EnumSet;

public abstract class MEBaseIzakayaBlockEntity extends AENetworkedBlockEntity  {


    public MEBaseIzakayaBlockEntity(BlockEntityType<?> blockEntityType, BlockPos pos, BlockState blockState) {
        super(blockEntityType, pos, blockState);
        getMainNode()
                .setIdlePowerUsage(0);
    }

    @Override
    public void onReady() {
        super.onReady();
        getMainNode().setExposedOnSides(EnumSet.allOf(Direction.class));
    }

    public ResourceHandler<ItemResource> getItemHandler() {
        if(getMainNode().getGrid() == null){
            return EmptyResourceHandler.instance();
        }
        return new MEStorageInvHandler(getMainNode().getGrid().getStorageService().getInventory());
    }

}
