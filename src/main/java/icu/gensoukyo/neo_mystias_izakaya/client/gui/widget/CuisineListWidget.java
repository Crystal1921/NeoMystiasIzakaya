/*
 * Copyright 2026 NeoMystiasIzakaya Team
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package icu.gensoukyo.neo_mystias_izakaya.client.gui.widget;

import icu.gensoukyo.neo_mystias_izakaya.client.gui.screen.RecipeScreen;
import icu.gensoukyo.neo_mystias_izakaya.client.gui.screen.RecipeScreen.ScreenMode;
import icu.gensoukyo.neo_mystias_izakaya.content.customer.CustomerHolder;
import icu.gensoukyo.neo_mystias_izakaya.content.recipe.NMIRecipeHolder;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class CuisineListWidget extends ObjectSelectionList<CuisineListWidget.DisplayEntry> {
    private final RecipeScreen parent;
    private final int listWidth;

    public CuisineListWidget(RecipeScreen parent, Minecraft minecraft, int width, int height, int y, int itemHeight) {
        super(minecraft, width, height, y, itemHeight);
        this.parent = parent;
        this.listWidth = width;
    }

    @Override
    public int getRowWidth() {
        return this.listWidth - 12;
    }

    @Override
    protected int scrollBarX() {
        return (super.scrollBarX() - 8);
    }

    @Override
    protected void extractListBackground(GuiGraphicsExtractor graphics) {

    }

    @Override
    protected void extractListSeparators(GuiGraphicsExtractor graphics) {

    }

    @Override
    protected void extractSelection(GuiGraphicsExtractor graphics, DisplayEntry entry, int outlineColor) {
        int outlineX0 = entry.getX() + 16;
        int outlineY0 = entry.getY();
        int outlineX1 = outlineX0 + entry.getWidth() - 16;
        int outlineY1 = outlineY0 + entry.getHeight();
        graphics.fill(outlineX0, outlineY0, outlineX1, outlineY1, outlineColor);
        graphics.fill(outlineX0 + 1, outlineY0 + 1, outlineX1 - 1, outlineY1 - 1, -16777216);
    }

    public void refreshList() {
        this.clearEntries();
        parent.buildImageList(this::addEntry);
    }

    /**
     * 通用展示条目，同时支持食谱/酒水(NMIRecipeHolder)和顾客(CustomerHolder)
     */
    public static class DisplayEntry extends ObjectSelectionList.Entry<DisplayEntry> {
        private final RecipeScreen parent;
        @Getter
        private final ScreenMode screenMode;
        /** NMIRecipeHolder、CustomerHolder 或 ItemStack */
        @Getter
        private final Object data;

        public DisplayEntry(NMIRecipeHolder recipeHolder, RecipeScreen parent) {
            this.data = recipeHolder;
            this.parent = parent;
            this.screenMode = ScreenMode.RECIPE;
        }

        public DisplayEntry(NMIRecipeHolder recipeHolder, RecipeScreen parent, ScreenMode mode) {
            this.data = recipeHolder;
            this.parent = parent;
            this.screenMode = mode;
        }

        public DisplayEntry(CustomerHolder customerHolder, RecipeScreen parent, ScreenMode mode) {
            this.data = customerHolder;
            this.parent = parent;
            this.screenMode = mode;
        }

        public DisplayEntry(ItemStack itemStack, RecipeScreen parent, ScreenMode mode) {
            this.data = itemStack;
            this.parent = parent;
            this.screenMode = mode;
        }

        public NMIRecipeHolder getRecipe() {
            return (NMIRecipeHolder) data;
        }

        public CustomerHolder getCustomer() {
            return (CustomerHolder) data;
        }

        public ItemStack getItemStack() {
            return (ItemStack) data;
        }

        public boolean isRecipe() {
            return data instanceof NMIRecipeHolder;
        }

        public boolean isCustomer() {
            return data instanceof CustomerHolder;
        }

        public boolean isItem() {
            return data instanceof ItemStack;
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            this.parent.setSelected(this);
            return super.mouseClicked(event, doubleClick);
        }

        @Override
        public Component getNarration() {
            return Component.empty();
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
            Font font = Minecraft.getInstance().font;
            if (isRecipe()) {
                NMIRecipeHolder recipe = getRecipe();
                Item value = recipe.recipe().output().item().value();
                graphics.text(font, Component.translatable(value.getDescriptionId()), getX() + 18, getY() + 4, 0xFFFFFFFF);
                graphics.item(value.getDefaultInstance(), getX(), getY());
            } else if (isCustomer()) {
                CustomerHolder customer = getCustomer();
                Identifier key = customer.key();
                graphics.text(font, Component.translatable("customer.neo_mystias_izakaya." + key.getPath()), getX() + 18, getY() + 4, 0xFFFFFFFF);
            } else if (isItem()) {
                ItemStack stack = getItemStack();
                graphics.text(font, Component.translatable(stack.getItem().getDescriptionId()), getX() + 18, getY() + 4, 0xFFFFFFFF);
                graphics.item(stack, getX(), getY());
            }
        }
    }
}
