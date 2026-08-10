/*
 * Copyright 2026 NeoMystiasIzakaya Team
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package icu.gensoukyo.neo_mystias_izakaya.common.blockentity;

import icu.gensoukyo.neo_mystias_izakaya.NeoMystiasIzakaya;
import icu.gensoukyo.neo_mystias_izakaya.api.dal.NMIDataAccessor;
import icu.gensoukyo.neo_mystias_izakaya.common.network.ServerPayloadSender;
import icu.gensoukyo.neo_mystias_izakaya.common.util.NMICommonBalanceUtil;
import icu.gensoukyo.neo_mystias_izakaya.common.util.NMIServerEconomyUtil;
import icu.gensoukyo.neo_mystias_izakaya.common.util.NMIServerItemTagUtil;
import icu.gensoukyo.neo_mystias_izakaya.compat.tlm.TLMUtil;
import icu.gensoukyo.neo_mystias_izakaya.content.economy.transaction.NMIBalanceTransactionReasons;
import icu.gensoukyo.neo_mystias_izakaya.content.izakaya.IzakayaOrder;
import icu.gensoukyo.neo_mystias_izakaya.content.customer.Customer;
import icu.gensoukyo.neo_mystias_izakaya.content.customer.CustomerHolder;
import icu.gensoukyo.neo_mystias_izakaya.content.customer.CustomerMap;
import icu.gensoukyo.neo_mystias_izakaya.content.customer.consts.CustomerEvaluation;
import icu.gensoukyo.neo_mystias_izakaya.content.recipe.NMIRecipeHolder;
import icu.gensoukyo.neo_mystias_izakaya.content.tag.ItemTagList;
import icu.gensoukyo.neo_mystias_izakaya.registry.NMIAttachmentTypes;
import icu.gensoukyo.neo_mystias_izakaya.registry.NMIBlockEntities;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.fml.ModList;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

public class DiningTableBlockEntity extends RandomizableContainerBlockEntity {

    /**
     * 菜品槽位
     */
    public static final int SLOT_CUISINE = 0;
    /**
     * 饮品槽位
     */
    public static final int SLOT_BEVERAGE = 1;
    /**
     * 总槽位数
     */
    public static final int CONTAINER_SIZE = 2;

    NonNullList<ItemStack> items = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);

    /**
     * 是否有顾客入座
     */
    @Getter
    private boolean isOccupied = false;
    /**
     * 当前入座顾客的ID
     */
    @Getter
    private Identifier customerId = IzakayaOrder.EMPTY_RARE_CUSTOMER;
    /**
     * 当前顾客的订单
     */
    @Getter
    private IzakayaOrder currentOrder = IzakayaOrder.EMPTY;
    /**
     * 村民渲染随机种子（每次入座新订单时重新生成，确保每一单都不同）
     */
    @Getter
    private long villagerSeed;
    /**
     * 餐桌序号（由控制器分配，从 1 开始；-1 表示未绑定）
     */
    @Getter
    private int tableIndex = -1;
    /**
     * 所属控制器坐标
     */
    @Getter
    private BlockPos controllerPos = BlockPos.ZERO;
    /**
     * 冷却时间（tick），菜单完成后随机 10~20 秒（200~400 tick）
     */
    private int cooldownTicks = 0;
    /**
     * 女仆用餐时所坐的 EntitySit 的 UUID（用于检测女仆是否离席）
     */
    private UUID seatEntityId = null;

    private static final RandomSource random = RandomSource.create(943);

    public DiningTableBlockEntity(BlockPos blockPos, BlockState blockState) {
        super(NMIBlockEntities.DINING_TABLE.get(), blockPos, blockState);
    }

    public static void serverTick(Level pLevel, BlockPos pPos, BlockState pState, DiningTableBlockEntity pBlockEntity) {
        // 女仆离席检测：座位实体消失则自动清桌
        Entity entity = pLevel.getEntity(pBlockEntity.seatEntityId);
        if (pBlockEntity.seatEntityId != null && entity == null) {
            pBlockEntity.clear();
            pBlockEntity.seatEntityId = null;
            return;
        }

        // CD 中，递减并跳过
        if (pBlockEntity.isCD()) {
            pBlockEntity.cooldownTicks--;
            return;
        }

        if (!pBlockEntity.isFull() || !pBlockEntity.isOccupied()) {
            return;
        }

        IzakayaOrder order = pBlockEntity.getCurrentOrder();

        // 计算顾客评价
        CustomerEvaluation evaluation = evaluateCustomer(pBlockEntity, order);
        // 解析文本键：稀客用 evaluation 键，普客随机挑选 chat
        String textKey = resolveTextKey(pBlockEntity, order, evaluation);

        int price = 0;

        if (order.isRare()) {
            // 稀客：cuisine 和 beverage 均为 Tag ID，检查物品正面 Tag 是否包含订单 Tag
            ItemTagList cuisineTags = NMIServerItemTagUtil.get(pBlockEntity.getCuisine());
            if (cuisineTags.positiveTags().contains(order.cuisine())) {
                price += NMIServerEconomyUtil.getItemStackPrice(pBlockEntity.getCuisine());
            }

            ItemTagList beverageTags = NMIServerItemTagUtil.get(pBlockEntity.getBeverage());
            if (beverageTags.positiveTags().contains(order.beverage())) {
                price += NMIServerEconomyUtil.getItemStackPrice(pBlockEntity.getBeverage());
            }
        } else {
            // 普客：cuisine 为配方 ID，beverage 为物品 ID，检查物品是否匹配
            NMIRecipeHolder recipeHolder = NMIDataAccessor.server().getRecipeMap().getRecipeMap().get(order.cuisine());
            if (recipeHolder != null) {
                Item expectedCuisine = recipeHolder.recipe().output().item().value();
                if (pBlockEntity.getCuisine().is(expectedCuisine)) {
                    price += NMIServerEconomyUtil.getItemStackPrice(pBlockEntity.getCuisine());
                }
            }

            Optional<Holder.Reference<Item>> itemRef = BuiltInRegistries.ITEM.get(order.beverage());
            if (itemRef.isPresent()) {
                Item expectedBeverage = itemRef.get().value();
                if (pBlockEntity.getBeverage().is(expectedBeverage)) {
                    price += NMIServerEconomyUtil.getItemStackPrice(pBlockEntity.getBeverage());
                }
            }
        }
        // 应用评价倍率
        int finalPrice = evaluation.apply(price);

        if (pBlockEntity.level != null
                && pBlockEntity.level.getBlockEntity(pBlockEntity.controllerPos) instanceof CanteenControllerBlockEntity canteen
                && canteen.getOwner() != null
                && pLevel.getEntity(canteen.getOwner()) instanceof ServerPlayer serverPlayer) {
            // 应用 Combo 加乘因子
            int combo = serverPlayer.getData(NMIAttachmentTypes.COMBO);
            int comboBonus = Math.min(combo, 25);
            finalPrice = (int) (finalPrice * (1.0 + comboBonus * 0.03));

            // 更新 Combo：非负面评价则 +1，否则清零
            if (evaluation.isNotNegative()) {
                serverPlayer.setData(NMIAttachmentTypes.COMBO, combo + 1);
            } else {
                serverPlayer.setData(NMIAttachmentTypes.COMBO, 0);
            }

            NMICommonBalanceUtil.insertEn(serverPlayer, finalPrice, false, NMIBalanceTransactionReasons.ORDER_PAY, "DiningTable", serverPlayer.getPlainTextName());

            if (pBlockEntity.seatEntityId != null && entity != null && ModList.get().isLoaded("touhou_little_maid")) {
                TLMUtil.addMaidChatBauble(entity, textKey);
            } else {
                ServerPayloadSender.sendDiningTableSaleMessage(serverPlayer, finalPrice, evaluation, textKey);
            }
        }
        // 完成后进入随机 10~20 秒 CD
        pBlockEntity.cooldownTicks = pLevel.getRandom().nextInt(200, 401);
        // 女仆仍在座则只清物品和订单状态，否则完全重置
        if (pBlockEntity.seatEntityId != null) {
            pBlockEntity.items.clear();
            pBlockEntity.isOccupied = false;
            pBlockEntity.currentOrder = IzakayaOrder.EMPTY;
            pBlockEntity.customerId = IzakayaOrder.EMPTY_RARE_CUSTOMER;
            pBlockEntity.markUpdated();
        } else {
            pBlockEntity.clear();
        }
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.neo_mystias_izakaya.dining_table");
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return this.items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> itemStacks) {
        this.items = NonNullList.copyOf(itemStacks);
    }

    @Override
    protected @Nullable AbstractContainerMenu createMenu(int i, Inventory inventory) {
        return null;
    }

    // === NBT ===

    @Override
    public int getContainerSize() {
        return CONTAINER_SIZE;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, this.items);
        output.putBoolean("IsOccupied", this.isOccupied);
        output.putString("CustomerId", this.customerId.toString());
        output.putInt("TableIndex", this.tableIndex);
        output.putInt("CooldownTicks", this.cooldownTicks);
        output.store("ControllerPos", BlockPos.CODEC, this.controllerPos);
        if (!this.currentOrder.equals(IzakayaOrder.EMPTY)) {
            output.store("Order", IzakayaOrder.CODEC, this.currentOrder);
        }
        output.putLong("VillagerSeed", this.villagerSeed);
    }

    // === 网络同步 ===

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.items.clear();
        ContainerHelper.loadAllItems(input, this.items);
        this.isOccupied = input.getBooleanOr("IsOccupied", false);
        this.customerId = input.getString("CustomerId")
                .map(Identifier::parse)
                .orElse(IzakayaOrder.EMPTY_RARE_CUSTOMER);
        this.tableIndex = input.getIntOr("TableIndex", -1);
        this.cooldownTicks = input.getIntOr("CooldownTicks", 0);
        this.controllerPos = input.read("ControllerPos", BlockPos.CODEC).orElse(BlockPos.ZERO);
        this.currentOrder = input.read("Order", IzakayaOrder.CODEC).orElse(IzakayaOrder.EMPTY);
        this.villagerSeed = input.getLongOr("VillagerSeed", 0L);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // === 业务方法 ===

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(this.problemPath(), NeoMystiasIzakaya.LOGGER)) {
            TagValueOutput output = TagValueOutput.createWithContext(reporter, registries);
            ContainerHelper.saveAllItems(output, this.items);
            output.putBoolean("IsOccupied", this.isOccupied);
            output.putString("CustomerId", this.customerId.toString());
            output.putInt("TableIndex", this.tableIndex);
            output.putInt("CooldownTicks", this.cooldownTicks);
            output.store("ControllerPos", BlockPos.CODEC, this.controllerPos);
            if (!this.currentOrder.equals(IzakayaOrder.EMPTY)) {
                output.store("Order", IzakayaOrder.CODEC, this.currentOrder);
            }
            output.putLong("VillagerSeed", this.villagerSeed);
            return output.buildResult();
        }
    }

    /**
     * 顾客入座并派发订单
     *
     * @param order 订单
     */
    public void seatCustomer(IzakayaOrder order) {
        this.isOccupied = true;
        this.customerId = order.rareCustomer();
        this.currentOrder = order;
        // 为每一单生成新的随机种子，客户端据此渲染不同的村民（职业/变种/朝向）
        this.villagerSeed = this.level != null ? this.level.getRandom().nextLong() : RandomSource.create().nextLong();
        markUpdated();
    }

    /**
     * 记录女仆座位实体并标记占用（由 MaidTakeSeatTask 调用）
     */
    public void setSeatEntityId(UUID id) {
        this.seatEntityId = id;
        markUpdated();
    }

    /**
     * 餐桌是否空闲（无顾客且无物品，无女仆占用）
     */
    public boolean isIdle() {
        return !this.isOccupied && this.items.stream().allMatch(ItemStack::isEmpty) && this.cooldownTicks == 0 && this.seatEntityId == null;
    }

    /**
     * 设置餐桌序号与所属控制器（由控制器调用）
     */
    public void bindToController(int index, BlockPos controller) {
        this.tableIndex = index;
        this.controllerPos = controller;
        markUpdated();
    }

    public void resetIndex() {
        this.tableIndex = -1;
    }

    public void resetController() {
        this.controllerPos = BlockPos.ZERO;
    }

    /**
     * 获取菜品
     */
    public ItemStack getCuisine() {
        return this.items.get(SLOT_CUISINE);
    }

    /**
     * 设置菜品
     */
    public void setCuisine(ItemStack cuisine) {
        this.items.set(SLOT_CUISINE, cuisine);
        markUpdated();
    }

    public void clearCuisine() {
        this.setCuisine(ItemStack.EMPTY);
    }

    /**
     * 获取饮品
     */
    public ItemStack getBeverage() {
        return this.items.get(SLOT_BEVERAGE);
    }

    // === Tick ===

    /**
     * 设置饮品
     */
    public void setBeverage(ItemStack beverage) {
        this.items.set(SLOT_BEVERAGE, beverage);
        markUpdated();
    }

    public void clearBeverage() {
        this.setBeverage(ItemStack.EMPTY);
    }

    public boolean isFull() {
        return this.items.stream().noneMatch(ItemStack::isEmpty);
    }

    private void markUpdated() {
        this.setChanged();
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    /**
     * 根据顾客喜好与上菜匹配度计算评价等级。
     *
     * <h3>评分规则</h3>
     * <table>
     * <tr><th>条件</th><th>分值</th></tr>
     * <tr><td>菜品 tag 命中 customer.likes</td><td>+1 per match</td></tr>
     * <tr><td>饮品 tag 命中 customer.likes</td><td>+1 per match</td></tr>
     * <tr><td>饮品 tag 命中 customer.beverage</td><td>+1 per match（独立于 likes 额外加分）</td></tr>
     * <tr><td>命中 customer.dislikes（菜品或饮品）</td><td>-2 per match</td></tr>
     * </table>
     *
     * <h3>评价映射</h3>
     * <table>
     * <tr><th>总分</th><th>评价</th></tr>
     * <tr><td>≥ 3</td><td>{@link CustomerEvaluation#EX_GOOD}</td></tr>
     * <tr><td>1 ~ 2</td><td>{@link CustomerEvaluation#GOOD}</td></tr>
     * <tr><td>0</td><td>{@link CustomerEvaluation#NORM}</td></tr>
     * <tr><td>-1 ~ -2</td><td>{@link CustomerEvaluation#BAD}</td></tr>
     * <tr><td>≤ -3</td><td>{@link CustomerEvaluation#EX_BAD}</td></tr>
     * </table>
     *
     * <h3>特殊规则</h3>
     * <ul>
     * <li><b>普通客人</b>：上菜物品必须与订单指定食谱的输出物品完全一致，
     *     不匹配则直接返回 {@link CustomerEvaluation#BAD}，不检查标签。</li>
     * <li><b>稀客</b>：若任何标签命中 customer.dislikes，
     *     则最高只能获得 {@link CustomerEvaluation#GOOD}，即使总分 ≥ 3。</li>
     * </ul>
     *
     * @param table 餐桌方块实体（需已上菜）
     * @param order 当前订单
     * @return 评价枚举，附带价格倍率
     */
    private static CustomerEvaluation evaluateCustomer(DiningTableBlockEntity table, IzakayaOrder order) {
        CustomerMap customerMap = NMIDataAccessor.server().getCustomerMap();
        CustomerHolder holder = customerMap.getAllCustomerMap().get(table.customerId);
        if (holder == null) return CustomerEvaluation.NORM;
        Customer customer = holder.customer();

        // 计算评分
        int score = 0;
        boolean hitDislike = false;

        // 菜品标签匹配
        ItemTagList cuisineTags = NMIServerItemTagUtil.get(table.getCuisine());
        for (Identifier tag : cuisineTags.positiveTags()) {
            if (customer.likes().contains(tag)) score++;
            if (customer.dislikes().contains(tag)) { score -= 2; hitDislike = true; }
        }

        // 饮品标签匹配
        ItemTagList beverageTags = NMIServerItemTagUtil.get(table.getBeverage());
        for (Identifier tag : beverageTags.positiveTags()) {
            if (customer.likes().contains(tag)) score++;
            if (customer.beverage().contains(tag)) score++; // 饮品偏好额外加分
            if (customer.dislikes().contains(tag)) { score -= 2; hitDislike = true; }
        }

        // 普通客人：菜品必须匹配订单
        if (!order.isRare()) {
            NMIRecipeHolder recipeHolder = NMIDataAccessor.server().getRecipeMap().getRecipeMap().get(order.cuisine());
            if (recipeHolder == null) return CustomerEvaluation.BAD;
            Item expectedItem = recipeHolder.recipe().output().item().value();
            if (!table.getCuisine().is(expectedItem)) {
                return CustomerEvaluation.BAD;
            }
        }

        // 稀客：踩中 dislike 则最高 GOOD
        if (order.isRare() && hitDislike) {
            return score >= 1 ? CustomerEvaluation.GOOD : mapScore(score);
        }

        return mapScore(score);
    }

    private static CustomerEvaluation mapScore(int score) {
        if (score >= 3) return CustomerEvaluation.EX_GOOD;
        if (score >= 1) return CustomerEvaluation.GOOD;
        if (score == 0) return CustomerEvaluation.NORM;
        if (score >= -2) return CustomerEvaluation.BAD;
        return CustomerEvaluation.EX_BAD;
    }

    /**
     * 解析客户端显示的翻译键。
     * <ul>
     * <li><b>稀客</b>：使用 {@code customerId.toLanguageKey("evaluation", level)}，
     *     对应 {@code NMILanguageProvider#addEvaluation} 的键。</li>
     * <li><b>普客</b>：从 {@code customer.chats()} 随机抽取一个 chat ID，
     *     使用 {@code chatId.toLanguageKey("customer")} 转换为翻译键。</li>
     * </ul>
     */
    private static String resolveTextKey(DiningTableBlockEntity table, IzakayaOrder order, CustomerEvaluation evaluation) {
        if (order.isRare()) {
            return table.customerId.toLanguageKey("evaluation", evaluation.getLevel());
        }
        CustomerMap customerMap = NMIDataAccessor.server().getCustomerMap();
        CustomerHolder holder = customerMap.getAllCustomerMap().get(table.customerId);
        if (holder == null) return "";
        var chats = holder.customer().chats();
        if (chats.isEmpty()) return "";
        var chatId = chats.get(random.nextInt(chats.size()));
        return chatId.toLanguageKey("customer");
    }

    /**
     * 完全重置餐桌状态
     */
    public void clear() {
        this.items.clear();
        this.isOccupied = false;
        this.customerId = IzakayaOrder.EMPTY_RARE_CUSTOMER;
        this.currentOrder = IzakayaOrder.EMPTY;
        this.seatEntityId = null;
        markUpdated();
    }

    public boolean isCD() {
        return this.cooldownTicks > 0;
    }
}
