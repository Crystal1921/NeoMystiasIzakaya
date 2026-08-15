/*
 * Copyright 2026 NeoMystiasIzakaya Team
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package icu.gensoukyo.neo_mystias_izakaya.content.izakaya;

import com.mojang.logging.LogUtils;
import icu.gensoukyo.neo_mystias_izakaya.api.dal.NMIDataAccessor;
import icu.gensoukyo.neo_mystias_izakaya.api.event.server.izakaya.CanteenDispatchEvent;
import icu.gensoukyo.neo_mystias_izakaya.common.blockentity.CanteenControllerBlockEntity;
import icu.gensoukyo.neo_mystias_izakaya.common.blockentity.DiningTableBlockEntity;
import icu.gensoukyo.neo_mystias_izakaya.common.util.NMICommonIzakayaUtil;
import icu.gensoukyo.neo_mystias_izakaya.content.customer.Customer;
import icu.gensoukyo.neo_mystias_izakaya.content.customer.CustomerHolder;
import icu.gensoukyo.neo_mystias_izakaya.content.customer.CustomerMap;
import icu.gensoukyo.neo_mystias_izakaya.content.customer.RareCustomerHolder;
import icu.gensoukyo.neo_mystias_izakaya.content.customer.consts.RareCustomers;
import icu.gensoukyo.neo_mystias_izakaya.content.recipe.NMIRecipeMap;
import icu.gensoukyo.neo_mystias_izakaya.content.tag.ItemTagList;
import icu.gensoukyo.neo_mystias_izakaya.content.tag.TagItemListMap;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.List;
import java.util.UUID;

/**
 * 食堂控制器订单派发流水线工具类。
 * <p>从 {@link CanteenControllerBlockEntity} 抽出的业务逻辑，每个步骤触发
 * {@link CanteenDispatchEvent} 系列事件作为扩展点。控制器仅在 {@code serverTick} 中遍历餐桌并委托
 * {@link #dispatch} 处理单桌派发。
 */
public final class CanteenOrderUtil {
    public static final Logger LOGGER = LogUtils.getLogger();

    /** 每轮每张空闲餐桌有客入座的概率（0.0 ~ 1.0） */
    private static final float SEAT_CHANCE = 0.3F;
    /** 稀客出现概率 */
    private static final float RARE_CUSTOMER_CHANCE = 0.15F;

    private CanteenOrderUtil() {
    }

    /**
     * 向单张餐桌派发订单：空桌判定 → 概率入座 → 生成订单 → 入座。
     * 触发 {@link CanteenDispatchEvent.Dispatch.Pre}/{@code .Post}。
     */
    public static void dispatch(Level level, CanteenControllerBlockEntity controller, DiningTableBlockEntity table) {
        if (!table.isIdle()) return;
        // 按概率派发：只有命中概率才入座
        if (level.getRandom().nextFloat() >= SEAT_CHANCE) return;

        CanteenDispatchEvent.Dispatch.Pre pre = NeoForge.EVENT_BUS.post(new CanteenDispatchEvent.Dispatch.Pre(level, controller, table));
        if (pre.isCanceled()) return;

        IzakayaOrder order = generateOrder(level, controller, table);
        if (IzakayaOrder.isEmpty(order)) return;

        table.seatCustomer(order);
        NeoForge.EVENT_BUS.post(new CanteenDispatchEvent.Dispatch.Post(level, controller, table, order));
    }

    /**
     * 生成新订单：选顾客 → 选菜品/饮品 → 构造订单。
     * 触发 {@link CanteenDispatchEvent.GenerateOrder}（可改写或否决）。
     */
    public static @Nullable IzakayaOrder generateOrder(Level level, CanteenControllerBlockEntity controller, DiningTableBlockEntity table) {
        CustomerMap customerMap = NMIDataAccessor.server().getCustomerMap();
        NMIRecipeMap recipeMap = NMIDataAccessor.server().getRecipeMap();
        TagItemListMap tagItemListMap = NMIDataAccessor.server().getTagItemListMap();
        RandomSource random = level.getRandom();

        // 确保标签索引已构建（首次调用时构建，后续命中缓存）
        if (recipeMap.getOutputTagToItemMap().isEmpty()) {
            recipeMap.buildOutputTagToItemMap(tagItemListMap);
        }

        CustomerHolder holder = pickCustomer(level, controller, table, customerMap, random);
        if (holder == null) return null;
        Customer customer = holder.customer();
        boolean isRare = holder instanceof RareCustomerHolder;

        Identifier cuisineId;
        Identifier beverageId;

        if (isRare) {
            // 稀客：cuisine 与 beverage 均为 Tag，从喜好/饮品列表均匀选取（传空 customerTags）
            cuisineId = pickMenuItem(level, controller, table, true, customer.likes(), List.of(), tagItemListMap, random);
            if (cuisineId == null) return null;
            beverageId = pickMenuItem(level, controller, table, false, customer.beverage(), List.of(), tagItemListMap, random);
        } else {
            // 普客：从菜单中根据标签匹配度加权随机选取
            UUID ownerId = controller.getOwner();
            if (ownerId == null) return null;
            Player player = level.getPlayerByUUID(ownerId);
            if (player == null) return null;
            IzakayaMenu menu = NMICommonIzakayaUtil.getMenu(player);

            cuisineId = pickMenuItem(level, controller, table, true, menu.cuisines(), customer.likes(), tagItemListMap, random);
            if (cuisineId == null) return null;
            beverageId = pickMenuItem(level, controller, table, false, menu.beverages(), customer.beverage(), tagItemListMap, random);
        }
        if (beverageId == null) return null;

        IzakayaOrder order = new IzakayaOrder(cuisineId, beverageId, holder.key(), isRare);
        CanteenDispatchEvent.GenerateOrder event = NeoForge.EVENT_BUS.post(new CanteenDispatchEvent.GenerateOrder(level, controller, table, holder, order));
        return event.getOrder();
    }

    /**
     * 从顾客池中按概率选一位（稀客 15%，普客 85%）。
     * 触发 {@link CanteenDispatchEvent.PickCustomer}（可改写）。
     */
    public static @Nullable CustomerHolder pickCustomer(Level level, CanteenControllerBlockEntity controller, DiningTableBlockEntity table,
                                                        CustomerMap customerMap, RandomSource random) {
        CustomerHolder picked = pickCustomerImpl(customerMap, random);
        CanteenDispatchEvent.PickCustomer event = NeoForge.EVENT_BUS.post(
                new CanteenDispatchEvent.PickCustomer(level, controller, table, picked instanceof RareCustomerHolder, picked));
        return event.getHolder();
    }

    private static @Nullable CustomerHolder pickCustomerImpl(CustomerMap customerMap, RandomSource random) {
        boolean isRare = random.nextFloat() < RARE_CUSTOMER_CHANCE;
        List<? extends CustomerHolder> pool;

        if (isRare) {
            // 排除没有对应模型的稀客（露易兹、爱莲、立空汐、蹦蹦跳跳三妖精）
            pool = customerMap.getRareCustomers().stream()
                    .filter(h -> !RareCustomers.NO_MODEL_CUSTOMERS.contains(h.key()))
                    .toList();
            if (pool.isEmpty()) pool = customerMap.getCommonCustomers();
        } else {
            pool = customerMap.getCommonCustomers();
            if (pool.isEmpty()) pool = customerMap.getRareCustomers();
        }

        if (pool.isEmpty()) return null;
        return pool.get(random.nextInt(pool.size()));
    }

    /**
     * 从菜单项中按标签匹配数加权随机选取（匹配越多权重越高，未匹配也有基础权重 1）；
     * {@code customerTags} 为空时退化为均匀随机（用于稀客）。
     * 触发 {@link CanteenDispatchEvent.PickMenuItem}（可改写）。
     */
    public static @Nullable Identifier pickMenuItem(Level level, CanteenControllerBlockEntity controller, DiningTableBlockEntity table,
                                                    boolean isCuisine, List<Identifier> menuItems, List<Identifier> customerTags,
                                                    TagItemListMap tagItemListMap, RandomSource random) {
        Identifier picked = pickWeighted(menuItems, customerTags, tagItemListMap, random);
        CanteenDispatchEvent.PickMenuItem event = NeoForge.EVENT_BUS.post(
                new CanteenDispatchEvent.PickMenuItem(level, controller, table, isCuisine, menuItems, customerTags, picked));
        return event.getPicked();
    }

    private static @Nullable Identifier pickWeighted(List<Identifier> menuItems, List<Identifier> customerTags,
                                                     TagItemListMap tagItemListMap, RandomSource random) {
        if (menuItems.isEmpty() || customerTags.isEmpty()) {
            return menuItems.isEmpty() ? null : menuItems.get(random.nextInt(menuItems.size()));
        }

        // 计算每个菜单项的权重：匹配标签数 + 1
        int[] weights = new int[menuItems.size()];
        int totalWeight = 0;
        for (int i = 0; i < menuItems.size(); i++) {
            ItemTagList itemTags = tagItemListMap.getItemToTagMap().get(menuItems.get(i));
            int matches = 0;
            if (itemTags != null) {
                for (Identifier tag : itemTags.positiveTags()) {
                    if (customerTags.contains(tag)) matches++;
                }
            }
            weights[i] = matches + 1; // 基础权重 1，确保未匹配的也有机会
            totalWeight += weights[i];
        }

        int roll = random.nextInt(totalWeight);
        int cumulative = 0;
        for (int i = 0; i < menuItems.size(); i++) {
            cumulative += weights[i];
            if (roll < cumulative) {
                return menuItems.get(i);
            }
        }
        return menuItems.get(random.nextInt(menuItems.size()));
    }
}
