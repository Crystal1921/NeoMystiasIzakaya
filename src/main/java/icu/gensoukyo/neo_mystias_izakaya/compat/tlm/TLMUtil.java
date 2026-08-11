/*
 * Copyright 2026 NeoMystiasIzakaya Team
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package icu.gensoukyo.neo_mystias_izakaya.compat.tlm;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.client.render.MaidRenderState;
import com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.implement.TextChatBubbleData;
import com.github.tartaricacid.touhoulittlemaid.entity.item.EntitySit;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import icu.gensoukyo.neo_mystias_izakaya.NeoMystiasIzakaya;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.neoforged.fml.ModList;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static com.github.tartaricacid.touhoulittlemaid.util.EntityCacheUtil.clearMaidDataResidue;

public class TLMUtil {
    public static final Cache<String, EntityMaid> SCREEN_CACHE = CacheBuilder.newBuilder().expireAfterAccess(10, TimeUnit.SECONDS).build();

    public static void renderEntityPart(String modelID, GuiGraphicsExtractor graphics, int mouseX, int mouseY, int middleX, int middleY, float renderItemScale) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel world = mc.level;
        if (world == null) return;
        EntityMaid entity = null;
        try {
            entity = SCREEN_CACHE.get(modelID, () -> new EntityMaid(world));
        } catch (ExecutionException e) {
            NeoMystiasIzakaya.LOGGER.error("Error while getting entity maid", e);
        }

        if (entity instanceof EntityMaid maid) {
            clearMaidDataResidue(maid, true);
            maid.setModelId(modelID);
            maid.renderState = MaidRenderState.GARAGE_KIT;
            maid.tickCount = 0;
            int centerX = middleX + 100;
            int yOffset = (int) (45 * (renderItemScale - 1));

            InventoryScreen.extractEntityInInventoryFollowsMouse(
                    graphics,
                    centerX - 100,
                    middleY - 100,
                    centerX + 100,
                    middleY + 200 - yOffset,
                    (int) (45 * renderItemScale),
                    0.1F,
                    mouseX,
                    mouseY,
                    entity);
        }
    }

    public static void renderMaid(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, Identifier key, int width, int height, int imageWidth, int imageHeight) {
        int middleX = (width - imageWidth) / 2;
        int middleY = (height - imageHeight) / 2;
        Identifier customerID = Identifier.fromNamespaceAndPath(TouhouLittleMaid.MOD_ID, key.getPath().replaceFirst("^customer/", ""));
        renderEntityPart(customerID.toString(), guiGraphics, mouseX, mouseY, middleX + 170, middleY - 10, 0.7F);
    }

    public static void addMaidChatBauble(Entity entity, String textKey) {
        if (entity instanceof EntitySit entitySit
                && entitySit.getFirstPassenger() instanceof EntityMaid entityMaid) {
            entityMaid.getChatBubbleManager().addChatBubble(TextChatBubbleData.type2(Component.translatable(textKey)));
        }
    }

    /**
     * 提取稀客对应的女仆渲染状态，用于在世界中渲染女仆。
     * 未安装 TLM 时返回 {@code null}。
     *
     * @param level                客户端世界
     * @param rareCustomer         稀客 ID（形如 {@code <modid>:customer/<model>}）
     * @param partialTicks         部分 tick
     * @param worldX/worldY/worldZ 女仆世界坐标
     * @param yaw                  女仆朝向（Minecraft 朝向，0=南，顺时针）
     */
    public static @Nullable EntityRenderState extractMaidRenderState(ClientLevel level, Identifier rareCustomer, float partialTicks, double worldX, double worldY, double worldZ, float yaw) {
        if (!ModList.get().isLoaded("touhou_little_maid")) {
            return null;
        }
        String modelId = toMaidModelId(rareCustomer);
        EntityMaid maid = getOrCreateMaid(level, modelId);
        clearMaidDataResidue(maid, true);
        maid.setModelId(modelId);
        maid.renderState = MaidRenderState.GARAGE_KIT;
        maid.tickCount = 0;
        maid.setPos(worldX, worldY, worldZ);
        maid.setYRot(yaw);
        maid.setYBodyRot(yaw);
        maid.setYHeadRot(yaw);
        // 同步 O 值，避免插值导致朝向逐帧漂移
        maid.yRotO = yaw;
        maid.yBodyRotO = yaw;
        maid.yHeadRotO = yaw;
        EntityRenderState renderState = Minecraft.getInstance().getEntityRenderDispatcher().extractEntity(maid, partialTicks);
        // 身体旋转改由渲染器侧通过 poseStack 控制（TLM 的 MaidRenderer 不读取标准 bodyRot），
        // 这里重置为 0，避免 TLM 若读取时造成双重旋转
        if (renderState instanceof LivingEntityRenderState living) {
            living.bodyRot = 0.0F;
            living.yRot = 0.0F;
            living.xRot = 0.0F;
        }
        return renderState;
    }

    /**
     * 将稀客 ID（{@code <modid>:customer/<model>}）转换为 TLM 模型 ID（{@code touhou_little_maid:<model>}）
     */
    private static String toMaidModelId(Identifier rareCustomer) {
        return Identifier.fromNamespaceAndPath(TouhouLittleMaid.MOD_ID, rareCustomer.getPath().replaceFirst("^customer/", "")).toString();
    }

    private static EntityMaid getOrCreateMaid(ClientLevel level, String modelId) {
        try {
            return SCREEN_CACHE.get(modelId, () -> new EntityMaid(level));
        } catch (ExecutionException e) {
            NeoMystiasIzakaya.LOGGER.error("Error while getting entity maid", e);
            return new EntityMaid(level);
        }
    }

    public static boolean isTouhouLittleMaid() {
        return ModList.get().isLoaded("touhou_little_maid");
    }
}
