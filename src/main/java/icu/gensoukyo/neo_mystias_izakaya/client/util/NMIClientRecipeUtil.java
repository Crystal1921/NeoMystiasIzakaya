/*
 * Copyright 2026 NeoMystiasIzakaya Team
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package icu.gensoukyo.neo_mystias_izakaya.client.util;

import icu.gensoukyo.neo_mystias_izakaya.api.dal.NMIDataAccessor;
import icu.gensoukyo.neo_mystias_izakaya.api.event.server.cooking.IzakayaRecipeEvent;
import icu.gensoukyo.neo_mystias_izakaya.common.util.NMICommonItemStackUtil;
import icu.gensoukyo.neo_mystias_izakaya.content.recipe.NMIRecipeHolder;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public final class NMIClientRecipeUtil {

    public static List<NMIRecipeHolder> getRecipesByInput(@Nullable Player player, List<ItemStack> input) {
        Set<Identifier> recipesIds = new HashSet<>();
        for (ItemStack stack : input) {
            List<Identifier> identifiers = getDataAccessor().getRecipeMap().getInputItemToRecipeMap().get(NMICommonItemStackUtil.get(stack));
            if (identifiers != null) {
                recipesIds.addAll(identifiers);
            }
        }
        IzakayaRecipeEvent.Collect post = NeoForge.EVENT_BUS.post(new IzakayaRecipeEvent.Collect(player, new ArrayList<>(recipesIds), null, input));;
        return getRecipes(new ArrayList<>(post.getRecipes()));
    }

    private static NMIDataAccessor getDataAccessor() {
        return NMIDataAccessor.client();
    }

    public static List<NMIRecipeHolder> getRecipesByOutput(@Nullable Player player, Identifier output) {
        List<Identifier> recipesIds = getDataAccessor().getRecipeMap().getOutputItemToRecipeMap().get(output);
        return getRecipes(recipesIds);
    }

    public static List<NMIRecipeHolder> getRecipesByOutputAndKitchenware(@Nullable Player player, Identifier output, TagKey<Block> kitchenware) {
        List<Identifier> byOutput = getDataAccessor().getRecipeMap().getOutputItemToRecipeMap().get(output);
        List<Identifier> byKitchenware = getDataAccessor().getRecipeMap().getKitchenwareToRecipeMap().get(kitchenware);

        // 取交集
        List<Identifier> recipesIds = new ArrayList<>();
        if (byOutput != null && byKitchenware != null) {
            for (Identifier id : byOutput) {
                if (byKitchenware.contains(id)) {
                    recipesIds.add(id);
                }
            }
        }

        return getRecipes(recipesIds);
    }


    public static List<NMIRecipeHolder> getRecipesByKitchenware(@Nullable Player player, TagKey<Block> kitchenware) {
        List<Identifier> recipesIds = getDataAccessor().getRecipeMap().getKitchenwareToRecipeMap().get(kitchenware);
        IzakayaRecipeEvent.Collect post = NeoForge.EVENT_BUS.post(new IzakayaRecipeEvent.Collect(player, recipesIds, kitchenware, List.of()));
        return getRecipes(post.getRecipes());
    }

    public static List<NMIRecipeHolder> getRecipesByInputAndKitchenwareMatchAllInput(@Nullable Player player, List<ItemStack> input, TagKey<Block> kitchenware) {

        Set<Identifier> inputRecipeIds = new HashSet<>();
        for (ItemStack stack : input) {
            List<Identifier> identifiers = getDataAccessor().getRecipeMap().getInputItemToRecipeMap().get(NMICommonItemStackUtil.get(stack));
            if (identifiers != null) {
                inputRecipeIds.addAll(identifiers);
            }
        }

        inputRecipeIds.removeIf(e->{
            List<Ingredient> ingredients = getDataAccessor().getRecipeMap().getRecipeMap().get(e).recipe().input();
            AtomicBoolean matchedAll = new AtomicBoolean(true);
            for (Ingredient ingredient : ingredients) {
                AtomicBoolean currentMatch = new AtomicBoolean(false);
                input.forEach(i -> {
                    if (ingredient.test(i)) {
                        currentMatch.set(true);
                    }
                });
                if (!currentMatch.get()) {
                    matchedAll.set(false);
                }
            }
            return !matchedAll.get();
        });


        List<Identifier> kitchenwareRecipeIds = getDataAccessor().getRecipeMap().getKitchenwareToRecipeMap().get(kitchenware);

        if (kitchenwareRecipeIds != null) {
            inputRecipeIds.retainAll(kitchenwareRecipeIds);
        } else {
            inputRecipeIds.clear();
        }

        IzakayaRecipeEvent.Collect post = NeoForge.EVENT_BUS.post(new IzakayaRecipeEvent.Collect(player, new ArrayList<>(inputRecipeIds), kitchenware, input));

        return getRecipes(new ArrayList<>(post.getRecipes()));
    }

    public static List<NMIRecipeHolder> getRecipesByInputAndKitchenwareMatchAnyInput(@Nullable Player player, List<ItemStack> input, TagKey<Block> kitchenware) {

        Set<Identifier> inputRecipeIds = new HashSet<>();
        for (ItemStack stack : input) {
            List<Identifier> identifiers = getDataAccessor().getRecipeMap().getInputItemToRecipeMap().get(NMICommonItemStackUtil.get(stack));
            if (identifiers != null) {
                inputRecipeIds.addAll(identifiers);
            }
        }

        List<Identifier> kitchenwareRecipeIds = getDataAccessor().getRecipeMap().getKitchenwareToRecipeMap().get(kitchenware);

        // 取交集
        if (kitchenwareRecipeIds != null) {
            inputRecipeIds.retainAll(kitchenwareRecipeIds);
        } else {
            inputRecipeIds.clear();
        }

        IzakayaRecipeEvent.Collect post = NeoForge.EVENT_BUS.post(new IzakayaRecipeEvent.Collect(player, new ArrayList<>(inputRecipeIds), kitchenware, input));

        return getRecipes(new ArrayList<>(post.getRecipes()));
    }

    public static List<NMIRecipeHolder> getRecipes(List<Identifier> recipesIds) {
        if (recipesIds == null) {
            return List.of();
        }
        List<NMIRecipeHolder> recipes = new ArrayList<>(recipesIds.size());
        recipesIds.forEach(id -> {
            NMIRecipeHolder recipe = getDataAccessor().getRecipeMap().getRecipeMap().get(id);
            if (recipe != null) {
                recipes.add(recipe);
            }
        });
        return recipes;
    }

    public static NMIRecipeHolder getRecipe(Identifier id) {
        return getDataAccessor().getRecipeMap().getRecipeMap().get(id);
    }

}
