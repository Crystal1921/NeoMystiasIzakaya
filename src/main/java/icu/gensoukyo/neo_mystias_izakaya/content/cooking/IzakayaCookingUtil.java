/*
 * Copyright 2026 NeoMystiasIzakaya Team
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package icu.gensoukyo.neo_mystias_izakaya.content.cooking;

import com.mojang.logging.LogUtils;
import icu.gensoukyo.neo_mystias_izakaya.api.event.server.cooking.GetAdditionalItemsEvent;
import icu.gensoukyo.neo_mystias_izakaya.api.event.server.cooking.IzakayaCookingEvent;
import icu.gensoukyo.neo_mystias_izakaya.api.event.server.cooking.IzakayaCookingTagEvent;
import icu.gensoukyo.neo_mystias_izakaya.common.blockentity.KitchenwareBlockEntity;
import icu.gensoukyo.neo_mystias_izakaya.common.util.NMIServerItemTagUtil;
import icu.gensoukyo.neo_mystias_izakaya.common.util.NMIServerRecipeUtil;
import icu.gensoukyo.neo_mystias_izakaya.content.recipe.NMIRecipe;
import icu.gensoukyo.neo_mystias_izakaya.content.recipe.NMIRecipeHolder;
import icu.gensoukyo.neo_mystias_izakaya.content.tag.ItemTagList;
import icu.gensoukyo.neo_mystias_izakaya.registry.NMIKitchenware;
import icu.gensoukyo.neo_mystias_izakaya.registry.item.NMICuisinesItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

public final class IzakayaCookingUtil {
    public static final Logger LOGGER = LogUtils.getLogger();

    public static List<ItemStack> getAdditionalItems(LivingEntity player, KitchenwareBlockEntity kitchenwareBE, NMIRecipe recipe, List<ItemStack> inputs) {

        NeoForge.EVENT_BUS.post(new GetAdditionalItemsEvent.Pre(player, kitchenwareBE, recipe, inputs));

        List<ItemStack> list = inputs.stream().filter(stack -> recipe.input().stream()
                .noneMatch(ingredient -> ingredient.test(stack))).toList();

        NeoForge.EVENT_BUS.post(new GetAdditionalItemsEvent.Post(player, kitchenwareBE, recipe, inputs, list));

        return list;
    }

    public static List<Identifier> collectTag(LivingEntity player, KitchenwareBlockEntity kitchenwareBE, ItemTagList cuisine, List<ItemTagList> additional) {
        // 没有冲突，计算两者的 positiveTags 的并集
        List<Identifier> resultPositiveTags = new ArrayList<>(cuisine.positiveTags());
        for (ItemTagList tagList : additional) {
            for (Identifier tag : tagList.positiveTags()) {
                if (!resultPositiveTags.contains(tag)) {
                    resultPositiveTags.add(tag);
                }
            }
        }
        resultPositiveTags.add(NMIKitchenware.REGISTRY.getValue(kitchenwareBE.getKitchenwareTypeId()).kitchenwareTag());

        IzakayaCookingTagEvent.Collect post = NeoForge.EVENT_BUS.post(new IzakayaCookingTagEvent.Collect(player, kitchenwareBE, cuisine, additional, resultPositiveTags));

        return post.getResult();
    }

    public static boolean hasConflictTag(LivingEntity player, KitchenwareBlockEntity kitchenwareBE, ItemTagList cuisine, List<ItemTagList> additional) {
        // 检查 cuisineList 的 negativeTags 与 additionalList 中的 positiveTags 是否有交集
        boolean hasConflict = additional.stream()
                .anyMatch(tagList ->
                        tagList.positiveTags().stream().anyMatch(cuisine.negativeTags()::contains));

        IzakayaCookingTagEvent.Check post = NeoForge.EVENT_BUS.post(new IzakayaCookingTagEvent.Check(player, kitchenwareBE, cuisine, additional, hasConflict));

        return post.isHasConflict();
    }

    public static void spawnDarkMatter(LivingEntity player, KitchenwareBlockEntity kitchenwareBE) {
        IzakayaCookingEvent.SpawnDarkMatter.Pre post = NeoForge.EVENT_BUS.post(new IzakayaCookingEvent.SpawnDarkMatter.Pre(player, kitchenwareBE));
        if (post.isCanceled()) return;
        ItemStack stack = NMICuisinesItems.DARK_MATTER.toStack();
        // 黑暗料理等同于河豚
        stack.set(DataComponents.FOOD, (new FoodProperties.Builder().nutrition(1).saturationModifier(0.1F).alwaysEdible().build()));
        stack.set(DataComponents.CONSUMABLE, Consumables.PUFFERFISH);
        spawnResult(player, kitchenwareBE, stack);
        NeoForge.EVENT_BUS.post(new IzakayaCookingEvent.SpawnDarkMatter.Post(player, kitchenwareBE));
    }

    public static void spawnResult(LivingEntity player, KitchenwareBlockEntity kitchenwareBE, ItemStack result) {
        NonNullList<ItemStack> inputs = NonNullList.create();
        inputs.addAll(List.copyOf(kitchenwareBE.getItems().subList(0, 4)));
        NonNullList<ItemStack> consumed = NonNullList.create();
        List<ItemStack> stacks = List.copyOf(kitchenwareBE.getItems().subList(0, 4));
        stacks.forEach(s -> s.setCount(s.count() - 1));
        consumed.addAll(stacks);
        IzakayaCookingEvent.ConsumeIngredients.Pre ingredients = NeoForge.EVENT_BUS.post(new IzakayaCookingEvent.ConsumeIngredients.Pre(player, kitchenwareBE, inputs, consumed));
        if (ingredients.isCanceled()) return;
        kitchenwareBE.setIngredients(ingredients.getResultIngredients());
        NeoForge.EVENT_BUS.post(new IzakayaCookingEvent.ConsumeIngredients.Post(player, kitchenwareBE, inputs, ingredients.getResultIngredients()));

        IzakayaCookingEvent.SpawnResult.Pre post = NeoForge.EVENT_BUS.post(new IzakayaCookingEvent.SpawnResult.Pre(player, kitchenwareBE, result));
        if (post.isCanceled()) return;
        kitchenwareBE.setTargetItem(post.getResult());
        NeoForge.EVENT_BUS.post(new IzakayaCookingEvent.SpawnResult.Post(player, kitchenwareBE, post.getResult()));
    }

    public static void setCookingTime(LivingEntity player, KitchenwareBlockEntity kitchenwareBE, int cookingTime) {
        IzakayaCookingEvent.SetCookingTime post = NeoForge.EVENT_BUS.post(new IzakayaCookingEvent.SetCookingTime(player, kitchenwareBE, cookingTime));
        kitchenwareBE.setCookingTime(post.getCookingTimeTick());
        kitchenwareBE.setTotalCookingTime(post.getCookingTotalTimeTick());
        if (kitchenwareBE.getLevel() != null) {
            kitchenwareBE.getLevel().setBlock(kitchenwareBE.getBlockPos(), kitchenwareBE.getBlockState().setValue(BlockStateProperties.LIT, true), Block.UPDATE_CLIENTS);
        }
    }

    public static void processCooking(LivingEntity player, Identifier recipe, BlockPos pos) {
        if (!player.level().isLoaded(pos)) {
            LOGGER.error("Entity {} attempted to cook at position {}, but the chunk is not loaded. This may be caused by a desync between the client and server. Ignoring the cooking attempt to prevent potential issues.", player.getName().getString(), pos);
            return;
        }

        BlockEntity blockEntity = player.level().getBlockEntity(pos);
        if (blockEntity instanceof KitchenwareBlockEntity kitchenware) {
            IzakayaCookingEvent.Trigger post = NeoForge.EVENT_BUS.post(new IzakayaCookingEvent.Trigger(player, kitchenware));
            if (post.isCanceled()) return;

            List<NMIRecipeHolder> recipes = NMIServerRecipeUtil.getRecipesByInputAndKitchenwareMatchAllInput(player, kitchenware.getIngredientItems(), NMIKitchenware.REGISTRY.getValue(kitchenware.getKitchenwareTypeId()).blockTagKey());

            boolean valid = recipes.stream().anyMatch(holder -> holder.key().equals(recipe));

            if (!valid) return;

            NMIRecipe cuisine = NMIServerRecipeUtil.getRecipe(recipe).recipe();

            List<ItemTagList> additionalList = IzakayaCookingUtil.getAdditionalItems(player, kitchenware, cuisine, kitchenware.getIngredientItems()).stream()
                    .map(NMIServerItemTagUtil::get)
                    .toList();
            ItemStack stack = cuisine.output().create();
            ItemTagList cuisineList = NMIServerItemTagUtil.get(stack);

            boolean hasConflict = IzakayaCookingUtil.hasConflictTag(player, kitchenware, cuisineList, additionalList);

            if (hasConflict) {
                IzakayaCookingUtil.spawnDarkMatter(player, kitchenware);
            } else {
                List<Identifier> resultPositiveTags = IzakayaCookingUtil.collectTag(player, kitchenware, cuisineList, additionalList);
                NMIServerItemTagUtil.set(stack, new ItemTagList(resultPositiveTags, cuisineList.negativeTags()));
                // 按烹饪时间给饱食度
                stack.set(DataComponents.FOOD, new FoodProperties.Builder().nutrition(cuisine.time()).saturationModifier(0.3F).alwaysEdible().build());
                stack.set(DataComponents.CONSUMABLE, Consumables.DEFAULT_FOOD);
                IzakayaCookingUtil.spawnResult(player, kitchenware, stack);
            }

            IzakayaCookingUtil.setCookingTime(player, kitchenware, cuisine.time());
        }
    }
}
