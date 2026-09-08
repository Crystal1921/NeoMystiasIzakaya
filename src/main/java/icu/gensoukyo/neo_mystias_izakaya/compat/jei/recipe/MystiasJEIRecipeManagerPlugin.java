package icu.gensoukyo.neo_mystias_izakaya.compat.jei.recipe;

import icu.gensoukyo.neo_mystias_izakaya.client.util.NMIClientRecipeUtil;
import icu.gensoukyo.neo_mystias_izakaya.common.util.NMICommonItemStackUtil;
import icu.gensoukyo.neo_mystias_izakaya.common.util.NMICommonRecipeUtil;
import icu.gensoukyo.neo_mystias_izakaya.content.cooking.Kitchenware;
import icu.gensoukyo.neo_mystias_izakaya.content.recipe.NMIRecipe;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.advanced.ISimpleRecipeManagerPlugin;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class MystiasJEIRecipeManagerPlugin implements ISimpleRecipeManagerPlugin<NMIRecipe> {

    private final Kitchenware kitchenware;

    public MystiasJEIRecipeManagerPlugin(Kitchenware kitchenware) {
        this.kitchenware = kitchenware;
    }

    public static MystiasJEIRecipeManagerPlugin of(Kitchenware kitchenware){
        return new MystiasJEIRecipeManagerPlugin(kitchenware);
    }

    @Override
    public boolean isHandledInput(ITypedIngredient<?> input) {
        ItemStack itemStack = input.getIngredient(VanillaTypes.ITEM_STACK)
                .orElse(null);
        if(itemStack==null)return false;
        return !NMIClientRecipeUtil.getRecipesByInputAndKitchenwareMatchAnyInput(null,List.of(itemStack), kitchenware.blockTagKey()).isEmpty();
    }

    @Override
    public boolean isHandledOutput(ITypedIngredient<?> output) {
        ItemStack itemStack = output.getIngredient(VanillaTypes.ITEM_STACK)
                .orElse(null);
        if(itemStack==null)return false;
        return !NMIClientRecipeUtil.getRecipesByOutputAndKitchenware(null,NMICommonItemStackUtil.get(itemStack), kitchenware.blockTagKey()).isEmpty();
    }

    @Override
    public List<NMIRecipe> getRecipesForInput(ITypedIngredient<?> input) {
        ItemStack itemStack = input.getIngredient(VanillaTypes.ITEM_STACK)
                .orElse(null);
        if(itemStack==null)return List.of();
        return NMICommonRecipeUtil.unWarp(NMIClientRecipeUtil.getRecipesByInputAndKitchenwareMatchAnyInput(null,List.of(itemStack), kitchenware.blockTagKey()));
    }

    @Override
    public List<NMIRecipe> getRecipesForOutput(ITypedIngredient<?> output) {
        ItemStack itemStack = output.getIngredient(VanillaTypes.ITEM_STACK)
                .orElse(null);
        if(itemStack==null)return List.of();
        return NMICommonRecipeUtil.unWarp(NMIClientRecipeUtil.getRecipesByOutputAndKitchenware(null, NMICommonItemStackUtil.get(itemStack), kitchenware.blockTagKey()));
    }

    @Override
    public List<NMIRecipe> getAllRecipes() {
        return NMICommonRecipeUtil.unWarp(NMIClientRecipeUtil.getRecipesByKitchenware(null,  kitchenware.blockTagKey()));
    }
}
