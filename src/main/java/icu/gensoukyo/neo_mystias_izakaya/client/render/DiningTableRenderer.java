/*
 * Copyright 2026 NeoMystiasIzakaya Team
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package icu.gensoukyo.neo_mystias_izakaya.client.render;

import com.github.tartaricacid.touhoulittlemaid.util.RenderHelper;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import icu.gensoukyo.neo_mystias_izakaya.client.render.state.DiningTableRenderState;
import icu.gensoukyo.neo_mystias_izakaya.common.blockentity.DiningTableBlockEntity;
import icu.gensoukyo.neo_mystias_izakaya.compat.tlm.TLMUtil;
import icu.gensoukyo.neo_mystias_izakaya.content.izakaya.IzakayaOrder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.npc.VillagerModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.layers.VillagerProfessionLayer;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.npc.villager.VillagerType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class DiningTableRenderer implements BlockEntityRenderer<DiningTableBlockEntity, DiningTableRenderState> {

    private static final Identifier VILLAGER_BASE_TEXTURE = Identifier.withDefaultNamespace("textures/entity/villager/villager.png");
    /**
     * 村民距离餐桌中心的水平距离
     */
    private static final double VILLAGER_DISTANCE = 1.0;

    private final ItemModelResolver itemModelResolver;
    private final VillagerModel villagerModel;
    private final VillagerProfessionLayer<VillagerRenderState, VillagerModel> villagerProfessionLayer;
    private final EntityRenderDispatcher entityRenderDispatcher;

    public DiningTableRenderer(BlockEntityRendererProvider.Context context) {
        itemModelResolver = context.itemModelResolver();
        entityRenderDispatcher = context.entityRenderer();
        villagerModel = new VillagerModel(context.bakeLayer(ModelLayers.VILLAGER));
        VillagerModel noHatModel = new VillagerModel(context.bakeLayer(ModelLayers.VILLAGER_NO_HAT));
        VillagerModel noHatBabyModel = new VillagerModel(context.bakeLayer(ModelLayers.VILLAGER_BABY_NO_HAT));
        villagerProfessionLayer = new VillagerProfessionLayer<>(
                () -> villagerModel,
                Minecraft.getInstance().getResourceManager(),
                "villager",
                noHatModel,
                noHatBabyModel
        );
    }

    @Override
    public DiningTableRenderState createRenderState() {
        return new DiningTableRenderState();
    }

    @Override
    public void extractRenderState(DiningTableBlockEntity blockEntity, DiningTableRenderState state, float partialTicks, Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress);
        ItemStack cuisine = blockEntity.getCuisine();
        ItemStack beverage = blockEntity.getBeverage();
        ItemStackRenderState cuisineRenderState = new ItemStackRenderState();
        ItemStackRenderState beverageRenderState = new ItemStackRenderState();
        itemModelResolver.updateForTopItem(cuisineRenderState, cuisine, ItemDisplayContext.NONE, blockEntity.getLevel(), null, (int) blockEntity.getBlockPos().asLong());
        itemModelResolver.updateForTopItem(beverageRenderState, beverage, ItemDisplayContext.NONE, blockEntity.getLevel(), null, (int) blockEntity.getBlockPos().asLong());
        state.cuisineRenderState = cuisineRenderState;
        state.beverageRenderState = beverageRenderState;
        state.index = blockEntity.getTableIndex();
        IzakayaOrder order = blockEntity.getCurrentOrder();
        if (IzakayaOrder.isEmpty(order)) {
            state.villagerData = null;
            state.maidRenderState = null;
        } else if (order.isRare()) {
            // 稀客：渲染女仆模型（随机放置角度与朝向）。
            // 仅当 TLM 已安装 且 非已入座女仆发起的订单（seatEntityId 为空）时才自行渲染女仆模型；
            // 否则不渲染（未装 TLM 或无实体女仆在场时防止出现问题）
            state.villagerData = null;
            if (blockEntity.getSeatEntityId() == null && TLMUtil.isTouhouLittleMaid()) {
                RandomSource random = RandomSource.create(blockEntity.getVillagerSeed());
                state.villagerAngle = random.nextFloat() * 2.0F * (float) Math.PI;
                float angle = state.villagerAngle;
                double px = 0.5D + VILLAGER_DISTANCE * Math.cos(angle);
                double pz = 0.5D + VILLAGER_DISTANCE * Math.sin(angle);
                float yaw = (float) Math.toDegrees(Math.atan2(px - 0.5D, 0.5D - pz));
                ClientLevel level = Minecraft.getInstance().level;
                if (level != null) {
                    BlockPos pos = blockEntity.getBlockPos();
                    state.maidRenderState = TLMUtil.extractMaidRenderState(level, order.rareCustomer(), partialTicks, pos.getX() + px, pos.getY(), pos.getZ() + pz, yaw);
                } else {
                    state.maidRenderState = null;
                }
            } else {
                state.maidRenderState = null;
            }
        } else {
            // 普客：渲染村民（随机职业 + 生物群系变种）
            state.maidRenderState = null;
            RandomSource random = RandomSource.create(blockEntity.getVillagerSeed());
            Holder<VillagerType> type = BuiltInRegistries.VILLAGER_TYPE.getRandom(random)
                    .orElseGet(() -> BuiltInRegistries.VILLAGER_TYPE.getOrThrow(VillagerType.PLAINS));
            Holder<VillagerProfession> profession = BuiltInRegistries.VILLAGER_PROFESSION.getRandom(random)
                    .orElseGet(() -> BuiltInRegistries.VILLAGER_PROFESSION.getOrThrow(VillagerProfession.NONE));
            state.villagerData = new VillagerData(type, profession, 1 + random.nextInt(5));
            state.villagerAngle = random.nextFloat() * 2.0F * (float) Math.PI;
        }
    }

    @Override
    public void submit(DiningTableRenderState diningTableRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState) {
        Font font = Minecraft.getInstance().font;
        poseStack.pushPose();
        poseStack.scale(0.5F, 0.5F, 0.5F);
        poseStack.translate(0.5D, 2.5D, 1D);
        diningTableRenderState.beverageRenderState.submit(poseStack, submitNodeCollector, diningTableRenderState.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        poseStack.translate(1D, 0D, 0D);
        diningTableRenderState.cuisineRenderState.submit(poseStack, submitNodeCollector, diningTableRenderState.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        poseStack.translate(-0.5D, 2D, 0D);
        poseStack.scale(0.1F, 0.1F, 0.1F);

        int index = diningTableRenderState.index;
        if (index >= 0) {
            MutableComponent literal = Component.literal(String.valueOf(index));
            // 面向玩家摄像机的 billboard 渲染（参照 vanilla 名牌 NameTagFeatureRenderer）
            poseStack.mulPose(cameraRenderState.orientation);
            poseStack.scale(1.0F, -1.0F, 1.0F);
            submitNodeCollector.submitText(poseStack, -font.width(literal) / 2.0F, 0.0F, literal.getVisualOrderText(), false, Font.DisplayMode.NORMAL, diningTableRenderState.lightCoords, 0xFFFFFFFF, 0, 0);
        }
        poseStack.popPose();

        // 稀客：在餐桌旁渲染女仆模型（EntityRenderDispatcher 内部处理朝向/模型变换）
        if (diningTableRenderState.maidRenderState != null) {
            renderMaid(diningTableRenderState, poseStack, submitNodeCollector, cameraRenderState);
        } else if (diningTableRenderState.villagerData != null) {
            // 普通客人（isRare=false）时，在餐桌旁渲染一个村民（随机职业 + 生物群系变种）
            renderVillager(diningTableRenderState, poseStack, submitNodeCollector, diningTableRenderState.villagerData);
        }
    }

    private void renderMaid(DiningTableRenderState diningTableRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState) {
        float angle = diningTableRenderState.villagerAngle;
        double px = 0.5D + VILLAGER_DISTANCE * Math.cos(angle);
        double pz = 0.5D + VILLAGER_DISTANCE * Math.sin(angle);
        // 从女仆指向餐桌中心的方向 → Minecraft 朝向（0=南，顺时针）
        float yaw = (float) Math.toDegrees(Math.atan2(px - 0.5D, 0.5D - pz));
        poseStack.pushPose();
        poseStack.translate(px, 0.0D, pz);
        // 因为 TLM 的 MaidRenderer 不读取标准 bodyRot，需直接旋转 poseStack
        poseStack.mulPose(Axis.YP.rotationDegrees(-yaw));
        if (diningTableRenderState.maidRenderState != null) {
            entityRenderDispatcher.submit(diningTableRenderState.maidRenderState, cameraRenderState, 0.0D, 0.0D, 0.0D, poseStack, submitNodeCollector);
        }
        poseStack.popPose();
    }

    private void renderVillager(DiningTableRenderState diningTableRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, VillagerData villagerData) {
        poseStack.pushPose();
        // 随机朝向：围绕餐桌中心、距离 VILLAGER_DISTANCE 放置，并面向餐桌
        float angle = diningTableRenderState.villagerAngle;
        double px = 0.5D + VILLAGER_DISTANCE * Math.cos(angle);
        double pz = 0.5D + VILLAGER_DISTANCE * Math.sin(angle);
        poseStack.translate(px, 0.0D, pz);
        // 从村民指向餐桌中心的方向 → Minecraft 朝向（0=南，顺时针）
        float yaw = (float) Math.toDegrees(Math.atan2(px - 0.5D, 0.5D - pz));
        // 标准实体模型变换（参照 LivingEntityRenderer.submit）：转向餐桌 + 立正 + 脚踩地面
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0F, -1.501F, 0.0F);
        VillagerRenderState villagerState = new VillagerRenderState();
        villagerState.lightCoords = diningTableRenderState.lightCoords;
        villagerState.villagerData = villagerData;
        // 本体基础贴图（含头部/面部），参照 VillagerRenderer.getTextureLocation。
        // 职业层内部的基础变种贴图会使用无头模型（createNoHatModel 移除了 head），
        // 所以必须先渲染带头的完整本体，否则面部/头部皮肤会缺失。
        submitNodeCollector.submitModel(villagerModel, villagerState, poseStack, VILLAGER_BASE_TEXTURE, diningTableRenderState.lightCoords, OverlayTexture.NO_OVERLAY, 0, null);
        // 职业层内部会依次渲染：生物群系变种 + 职业 + 职业等级
        villagerProfessionLayer.submit(poseStack, submitNodeCollector, diningTableRenderState.lightCoords, villagerState, 0.0F, 0.0F);
        poseStack.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(DiningTableBlockEntity blockEntity) {
        return RenderHelper.getAABB(
                blockEntity.getBlockPos().offset(-5, -5, -5),
                blockEntity.getBlockPos().offset(5, 5, 5)
        );
    }
}
