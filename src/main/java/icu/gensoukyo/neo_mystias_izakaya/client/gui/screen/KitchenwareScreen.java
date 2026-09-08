/*
 * Copyright 2026 NeoMystiasIzakaya Team
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package icu.gensoukyo.neo_mystias_izakaya.client.gui.screen;

import icu.gensoukyo.neo_mystias_izakaya.api.event.server.cooking.IzakayaCookingEvent;
import icu.gensoukyo.neo_mystias_izakaya.client.dal.ClientNMIDataAccessor;
import icu.gensoukyo.neo_mystias_izakaya.client.gui.widget.CupboardHud;
import icu.gensoukyo.neo_mystias_izakaya.client.network.ClientPayloadSender;
import icu.gensoukyo.neo_mystias_izakaya.client.util.NMIClientEconomyUtil;
import icu.gensoukyo.neo_mystias_izakaya.client.util.NMIClientRecipeUtil;
import icu.gensoukyo.neo_mystias_izakaya.client.util.NMIClientUtil;
import icu.gensoukyo.neo_mystias_izakaya.common.blockentity.KitchenwareBlockEntity;
import icu.gensoukyo.neo_mystias_izakaya.common.menu.KitchenwareMenu;
import icu.gensoukyo.neo_mystias_izakaya.common.resource.ItemResourceWithCount;
import icu.gensoukyo.neo_mystias_izakaya.common.util.NMICommonComponentUtil;
import icu.gensoukyo.neo_mystias_izakaya.common.util.NMICommonIzakayaUtil;
import icu.gensoukyo.neo_mystias_izakaya.content.izakaya.IzakayaMenu;
import icu.gensoukyo.neo_mystias_izakaya.content.recipe.NMIRecipe;
import icu.gensoukyo.neo_mystias_izakaya.content.recipe.NMIRecipeHolder;
import icu.gensoukyo.neo_mystias_izakaya.content.tag.ItemTagList;
import icu.gensoukyo.neo_mystias_izakaya.registry.NMIKitchenware;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.common.NeoForge;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static icu.gensoukyo.neo_mystias_izakaya.NeoMystiasIzakaya.id;

public class KitchenwareScreen extends AbstractContainerScreen<KitchenwareMenu> {
    private static final Identifier BACKGROUND = id("textures/gui/kitchenware_bg.png");
    private static final int YELLOW = Color.YELLOW.getRGB();
    private static final int BLACK = Color.BLACK.getRGB();
    private final static int GREEN = Color.GREEN.getRGB();
    private final static int RED = Color.RED.getRGB();
    private final int MIN_X = 120;
    private final int MIN_Y = 10;
    private final int MAX_X = 215;
    private final int MAX_Y = 90;
    private final KitchenwareBlockEntity kitchenwareBE;
    private final List<Identifier> menuCuisineIds = new ArrayList<>();
    List<NMIRecipeHolder> possibleRecipes;

    private CupboardHud cupBoardHud;

    public KitchenwareScreen(KitchenwareMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, 230, 219);
        this.kitchenwareBE = this.getMenu().getKitchenwareBE();
        this.possibleRecipes = NMIClientRecipeUtil.getRecipesByInputAndKitchenwareMatchAllInput(NMIClientUtil.getPlayer(), List.copyOf(kitchenwareBE.getItems()), NMIKitchenware.REGISTRY.getValue(kitchenwareBE.getKitchenwareTypeId()).blockTagKey());

    }

    public static void renderCuisineInfo(GuiGraphicsExtractor guiGraphics, Font font, NMIRecipeHolder recipeHolder, int i, int j) {
        NMIRecipe recipe = recipeHolder.recipe();
        Item cuisineItem = recipe.output().item().value();
        Integer itemStackPriceBase = NMIClientEconomyUtil.getItemStackPriceBase(recipe.output().create());
        int price = 0;
        if (itemStackPriceBase != null) {
            price = itemStackPriceBase;
        }
        guiGraphics.text(font, Component.translatable(cuisineItem.getDescriptionId()), i + 15, j + 10, BLACK, false);
        guiGraphics.text(font, Component.translatable("gui.neo_mystias_izakaya.time").append(": " + recipe.time()), i + 15, j + 20, BLACK, false);
        guiGraphics.text(font, Component.translatable("gui.neo_mystias_izakaya.price").append(": " + price + " ").append(NMICommonComponentUtil.unitEn()), i + 50, j + 20, BLACK, false);

        FormattedCharSequence description = Component.translatable(cuisineItem.getDescriptionId() + ".desc").getVisualOrderText();
        StringBuilder builder = getTranslatedString(description);
        String substring = builder.subSequence(0, 10).toString();

        guiGraphics.text(font, Component.literal(substring + "..."), i + 15, j + 30, BLACK, false);
        ItemTagList itemTagList = ClientNMIDataAccessor.INSTANCE.getTagItemListMap().getItemToTagMap().get(recipeHolder.key());
        if (itemTagList != null) {
            renderTags(guiGraphics, font, i, j, itemTagList);
        }
    }

    public static StringBuilder getTranslatedString(FormattedCharSequence description) {
        StringBuilder builder = new StringBuilder();
        description.accept((index, style, charPoint) -> {
            builder.append(Character.toChars(charPoint));
            return true;
        });
        return builder;
    }

    public static void renderTags(GuiGraphicsExtractor guiGraphics, Font font, int i, int j, ItemTagList itemTagList) {
        TagRenderState state = new TagRenderState(0, j + 40);
        state = renderTags(font, itemTagList.positiveTags(), GREEN, state, guiGraphics, i + 15);
        state = renderTags(font, itemTagList.negativeTags(), RED, state, guiGraphics, i + 15);
    }

    public static TagRenderState renderTags(Font font, List<Identifier> tags, int color, TagRenderState state, GuiGraphicsExtractor guiGraphics, int baseX) {
        int currentX = state.currentX();
        int startY = state.startY();
        int lineHeight = font.lineHeight;
        int maxWidth = 100;

        for (Identifier identifier : tags) {
            MutableComponent tag = Component.translatable(identifier.toLanguageKey("tag"));
            FormattedCharSequence visualOrderText = tag.getVisualOrderText();
            int fontWidth = font.width(visualOrderText);
            if (currentX + fontWidth > maxWidth) {
                currentX = 0;
                startY += lineHeight;
            }
            guiGraphics.text(font, visualOrderText, baseX + currentX, startY, color, false);
            currentX += (fontWidth + 4);
        }
        return new TagRenderState(currentX, startY);
    }

    @Override
    protected void init() {
        super.init();
        menuCuisineIds.clear();

        if (minecraft.player == null) return;
        IzakayaMenu menu = NMICommonIzakayaUtil.getMenu(minecraft.player);

        // 只保留当前厨具能制作的菜品
        var kw = NMIKitchenware.REGISTRY.getValue(kitchenwareBE.getKitchenwareTypeId());
        if (kw == null) return;
        var blockTag = kw.blockTagKey();

        for (Identifier cuisineId : menu.cuisines()) {
            var holder = ClientNMIDataAccessor.INSTANCE.getRecipeMap().getRecipeMap().get(cuisineId);
            if (holder != null && holder.recipe().kitchenware().equals(blockTag)) {
                menuCuisineIds.add(cuisineId);
            }
        }

        int x2 = Math.max(getLeftPos()-130,10);
        cupBoardHud = new CupboardHud(x2,getTopPos(),getLeftPos()-x2-10,getImageHeight(),this::onHudItemResourceClick);
        addRenderableWidget(cupBoardHud);
        ClientPayloadSender.sendRequestCupboardIngredientInfoMessage();
    }

    private void onHudItemResourceClick(ItemResourceWithCount resource) {
        if (menu.getCarried().isEmpty()) {
            ClientPayloadSender.sendRequestExtractItemToKitchenwareMessage(resource.itemResource(),kitchenwareBE.getBlockPos());
        } else {
            ClientPayloadSender.sendRequestCupboardInsertItemFromPlayerHandMessage();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);

        // 右侧菜单物品渲染
        renderMenuCuisines(graphics, mouseX, mouseY);

        boolean isLit = kitchenwareBE.getBlockState().getValue(BlockStateProperties.LIT);
        if (isLit) {
            renderProgress(graphics, mouseX, mouseY);
            return;
        }
        if (kitchenwareBE.getResultItem().isEmpty()) {
            this.renderRecipes(graphics, mouseX, mouseY);
        } else {
            this.renderResult(graphics, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        // 右侧菜单点击 → 自动放入食材并开始烹饪
        int clickedMenuIndex = getMenuCuisineIndex((int) event.x(), (int) event.y());
        if (clickedMenuIndex >= 0 && clickedMenuIndex < menuCuisineIds.size() && kitchenwareBE.canStartCooking()) {
            ClientPayloadSender.sendRequestExtractToKitchenwareMessage(clickedMenuIndex,kitchenwareBE.getBlockPos());
            return true;
        }

        int hoveredRecipeIndex = getHoveredRecipeIndex((int) event.x(), (int) event.y());
        if (hoveredRecipeIndex >= 0 && hoveredRecipeIndex < possibleRecipes.size() && kitchenwareBE.canStartCooking()) {
            Identifier key = this.possibleRecipes.get(hoveredRecipeIndex).key();
            IzakayaCookingEvent.Trigger post = NeoForge.EVENT_BUS.post(new IzakayaCookingEvent.Trigger(NMIClientUtil.getPlayer(), kitchenwareBE));
            if (post.isCanceled()) {
                return true;
            }
            ClientPayloadSender.sendKitchenwareCookMessage(key, kitchenwareBE.getBlockPos());
        }
        return super.mouseClicked(event, doubleClick);
    }

    private int getHoveredRecipeIndex(int pMouseX, int pMouseY) {
        int i = (this.width - this.imageWidth) / 2;
        int j = (this.height - this.imageHeight) / 2;
        if (pMouseX > (i + MIN_X) && pMouseX < (i + MAX_X) && pMouseY > (j + MIN_Y) && pMouseY < (j + MAX_Y)) {
            int dx = pMouseX - i;
            int dy = pMouseY - j;
            int x = dx - (dx) % 20 + i;
            int y = dy - (dy - 11) % 20 + j;
            return (x - i - 120) / 20 + (y - j - 10) / 20 * 5;
        }
        return -1;
    }

    private void renderHighlight(GuiGraphicsExtractor guiGraphics, int index, int i, int j) {
        if (index >= 0) {
            int x = i + 120 + index % 5 * 20;
            int y = j + 11 + index / 5 * 20;
            guiGraphics.fill(x, y, x + 20, y + 2, YELLOW);
            guiGraphics.fill(x, y, x + 2, y + 20, YELLOW);
            guiGraphics.fill(x + 18, y, x + 20, y + 20, YELLOW);
            guiGraphics.fill(x, y + 18, x + 20, y + 20, YELLOW);
        }
    }

    protected void renderRecipes(GuiGraphicsExtractor guiGraphics, int pMouseX, int pMouseY) {
        int i = (this.width - this.imageWidth) / 2;
        int j = (this.height - this.imageHeight) / 2;

        int hoveredIndex = getHoveredRecipeIndex(pMouseX, pMouseY);
        renderHighlight(guiGraphics, hoveredIndex, i, j);

        for (int k = 0; k < possibleRecipes.size(); k++) {
            guiGraphics.item(possibleRecipes.get(k).recipe().output().create(), i + 122 + k % 5 * 20, j + 13 + k / 5 * 20);
        }

        int hoveredRecipeIndex = getHoveredRecipeIndex(pMouseX, pMouseY);
        if (hoveredRecipeIndex >= 0 && hoveredRecipeIndex < possibleRecipes.size()) {
            NMIRecipeHolder recipeHolder = possibleRecipes.get(hoveredRecipeIndex);
            renderCuisineInfo(guiGraphics, font, recipeHolder, i, j);
        }
    }

    protected void renderResult(GuiGraphicsExtractor guiGraphics, int pMouseX, int pMouseY) {

    }

    protected void renderProgress(GuiGraphicsExtractor guiGraphics, int pMouseX, int pMouseY) {
        int i = (this.width - this.imageWidth) / 2;
        int j = (this.height - this.imageHeight) / 2;
        float cookTime = this.menu.getData().get(0);
        float totalTime = this.menu.getData().get(1);
        float process = (totalTime - cookTime) / 20;
        if (cookTime > 0) {
            guiGraphics.text(font, Component.translatable("gui.neo_mystias_izakaya.progress").append(Component.literal(" : " + String.format("%.2f / %.2f s", process, (totalTime / 20)))), i + 120, j + 10, BLACK, false);
            guiGraphics.text(font, Component.literal(String.format("%.2f", (1 - cookTime / totalTime) * 100) + " %"), i + 120, j + 25, BLACK, false);
        }
    }

    public void updateRecipes() {
        this.possibleRecipes = NMIClientRecipeUtil.getRecipesByInputAndKitchenwareMatchAllInput(NMIClientUtil.getPlayer(), List.copyOf(kitchenwareBE.getItems()), NMIKitchenware.REGISTRY.getValue(kitchenwareBE.getKitchenwareTypeId()).blockTagKey());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        int i = (this.width - this.imageWidth) / 2;
        int j = (this.height - this.imageHeight) / 2;
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, i, j, 0.0F, 0.0F, 256, 256, 256, 256);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int xm, int ym) {
    }

    // ========== 右侧菜单渲染 ==========

    private void renderMenuCuisines(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (menuCuisineIds.isEmpty()) return;

        int leftPos = (this.width - this.imageWidth) / 2;
        int topPos = (this.height - this.imageHeight) / 2;
        int iconX = leftPos + this.imageWidth + 2;
        int hovered = getMenuCuisineIndex(mouseX, mouseY);

        for (int k = 0; k < menuCuisineIds.size(); k++) {
            int iconY = topPos + 4 + k * 18;
            Identifier cuisineId = menuCuisineIds.get(k);
            var holder = ClientNMIDataAccessor.INSTANCE.getRecipeMap().getRecipeMap().get(cuisineId);
            if (holder == null) continue;
            Item item = holder.recipe().output().item().value();

            if (k == hovered) {
                graphics.fill(iconX, iconY, iconX + 16, iconY + 16, 0x44FFD700);
            }
            graphics.item(item.getDefaultInstance(), iconX, iconY);
        }
    }

    private int getMenuCuisineIndex(int mouseX, int mouseY) {
        if (menuCuisineIds.isEmpty()) return -1;
        int leftPos = (this.width - this.imageWidth) / 2;
        int topPos = (this.height - this.imageHeight) / 2;
        int iconX = leftPos + this.imageWidth + 2;
        int iconY = topPos + 4;

        if (mouseX >= iconX && mouseX < iconX + 16) {
            int index = (mouseY - iconY) / 18;
            if (index >= 0 && index < menuCuisineIds.size()) {
                return index;
            }
        }
        return -1;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        boolean b = super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        if (cupBoardHud.isMouseOver(mouseX, mouseY)) {
            return cupBoardHud.mouseScrolled(mouseX, mouseY, scrollX, scrollY) || b;
        }
        return b;
    }


    public record TagRenderState(int currentX, int startY) {
    }
}
