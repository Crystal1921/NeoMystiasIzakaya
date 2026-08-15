/*
 * Copyright 2026 NeoMystiasIzakaya Team
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package icu.gensoukyo.neo_mystias_izakaya.compat.tlm.task;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidCheckRateTask;
import com.github.tartaricacid.touhoulittlemaid.entity.item.EntitySit;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitBrains;
import com.google.common.collect.ImmutableMap;
import icu.gensoukyo.neo_mystias_izakaya.NeoMystiasIzakaya;
import icu.gensoukyo.neo_mystias_izakaya.api.dal.NMIDataAccessor;
import icu.gensoukyo.neo_mystias_izakaya.common.blockentity.CanteenControllerBlockEntity;
import icu.gensoukyo.neo_mystias_izakaya.common.blockentity.DiningTableBlockEntity;
import icu.gensoukyo.neo_mystias_izakaya.content.customer.CustomerMap;
import icu.gensoukyo.neo_mystias_izakaya.content.customer.RareCustomer;
import icu.gensoukyo.neo_mystias_izakaya.content.customer.RareCustomerHolder;
import icu.gensoukyo.neo_mystias_izakaya.content.customer.consts.RareCustomers;
import icu.gensoukyo.neo_mystias_izakaya.content.izakaya.IzakayaOrder;
import icu.gensoukyo.neo_mystias_izakaya.registry.NMIMemoryTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;

import java.util.List;
import java.util.Random;

public class MaidPlaceOrderTask extends MaidCheckRateTask {
    private static final Random random = new Random();
    private static final int checkRate = 50;
    private static final int showTime = 2000;

    public MaidPlaceOrderTask() {
        super(ImmutableMap.of(MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT,
                InitBrains.TARGET_POS.get(), MemoryStatus.VALUE_ABSENT));
        this.setMaxCheckRate(checkRate);
    }

    @Override
    protected void start(ServerLevel level, EntityMaid maid, long gameTimeIn) {
        maid.getBrain().getMemory(NMIMemoryTypes.TARGET_POS.get()).ifPresent(targetPos -> {
            if (level.getBlockEntity(targetPos) instanceof DiningTableBlockEntity diningTableBlock) {
                // 闭店时不得点餐
                if (!isCanteenOpen(level, diningTableBlock)) return;
                if (!diningTableBlock.isOccupied() && !diningTableBlock.isCD()) {
                    // TLM 命名彩蛋：女仆名为 moesumika 时使用特殊模型（与 getModelId 不同），
                    // 直接映射到对应的 MOESUMIKA 稀客
                    Identifier maidID;
                    if ("moesumika".equalsIgnoreCase(maid.getName().getString())) {
                        maidID = RareCustomers.MOESUMIKA;
                    } else {
                        Identifier maidModel = Identifier.parse(maid.getModelId());
                        maidID = NeoMystiasIzakaya.id("customer/" + maidModel.getPath());
                    }

                    CustomerMap customerMap = NMIDataAccessor.server().getCustomerMap();
                    // 尝试匹配特定稀客，匹配不到则随机选一个
                    RareCustomerHolder holder = customerMap.getRareCustomerMap().get(maidID);
                    if (holder == null) {
                        // 随机选取时排除没有对应模型的稀客（露易兹、爱莲、立空汐、蹦蹦跳跳三妖精）
                        List<RareCustomerHolder> rareList = customerMap.getRareCustomers().stream()
                                .filter(h -> !RareCustomers.NO_MODEL_CUSTOMERS.contains(h.key()))
                                .toList();
                        if (!rareList.isEmpty()) {
                            holder = rareList.get(level.getRandom().nextInt(rareList.size()));
                        }
                    }
                    if (holder != null) {
                        RareCustomer customer = holder.customer();
                        List<Identifier> likes = customer.likes();
                        List<Identifier> beverages = customer.beverage();
                        if (!likes.isEmpty() && !beverages.isEmpty()) {
                            Identifier cuisineId = likes.get(level.getRandom().nextInt(likes.size()));
                            Identifier beverageId = beverages.get(level.getRandom().nextInt(beverages.size()));
                            IzakayaOrder order = new IzakayaOrder(cuisineId, beverageId, holder.key(), true);
                            // 每次点单都重新写入女仆座位 UUID：
                            // 防止闭店清桌（clear 会置空 seatEntityId）后，女仆仍在座却再次点单时
                            // seatEntityId 丢失，导致渲染器误判为非女仆发起而额外渲染假女仆
                            if (maid.getVehicle() instanceof EntitySit sit) {
                                diningTableBlock.setSeatEntityId(sit.getUUID());
                            }
                            diningTableBlock.seatCustomer(order);
                        }
                    }
                }
            }
        });
    }

    private static boolean isCanteenOpen(ServerLevel level, DiningTableBlockEntity table) {
        BlockPos controllerPos = table.getControllerPos();
        if (level.getBlockEntity(controllerPos) instanceof CanteenControllerBlockEntity controller) {
            return controller.isOpen();
        }
        return false;
    }
}
