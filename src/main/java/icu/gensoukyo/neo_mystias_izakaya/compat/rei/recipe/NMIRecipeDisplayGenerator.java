package icu.gensoukyo.neo_mystias_izakaya.compat.rei.recipe;

import icu.gensoukyo.neo_mystias_izakaya.client.util.NMIClientRecipeUtil;
import icu.gensoukyo.neo_mystias_izakaya.common.util.NMICommonItemStackUtil;
import icu.gensoukyo.neo_mystias_izakaya.common.util.NMICommonItemUtil;
import icu.gensoukyo.neo_mystias_izakaya.content.cooking.Kitchenware;
import icu.gensoukyo.neo_mystias_izakaya.content.recipe.NMIRecipe;
import icu.gensoukyo.neo_mystias_izakaya.content.recipe.NMIRecipeHolder;
import icu.gensoukyo.neo_mystias_izakaya.registry.NMIKitchenware;
import me.shedaniel.rei.api.client.registry.display.DynamicDisplayGenerator;
import me.shedaniel.rei.api.common.display.Display;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.entry.type.VanillaEntryTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public class NMIRecipeDisplayGenerator<T extends Display> implements DynamicDisplayGenerator<T> {
    private final Kitchenware kitchenware;
    private final Function<NMIRecipe,T> builder;

    public NMIRecipeDisplayGenerator(Kitchenware kitchenware, Function<NMIRecipe, T> builder) {
        this.kitchenware = kitchenware;
        this.builder = builder;
    }

    @Override
    public Optional<List<T>> getRecipeFor(EntryStack<?> entry) {
        if (entry.getType() == VanillaEntryTypes.ITEM) {
            Identifier identifier = entry.getIdentifier();
            List<NMIRecipeHolder> byOutput = NMIClientRecipeUtil.getRecipesByOutputAndKitchenware(Minecraft.getInstance().player, identifier,kitchenware.blockTagKey());
            List<T> display = new ArrayList<>(byOutput.stream().map(NMIRecipeHolder::recipe).map(builder).toList());
            return Optional.of(display);
        }
        return Optional.empty();
    }

    @Override
    public Optional<List<T>> getUsageFor(EntryStack<?> entry) {
        if (entry.getType() == VanillaEntryTypes.ITEM) {
            Identifier identifier = entry.getIdentifier();
            List<NMIRecipeHolder> byInput = NMIClientRecipeUtil.getRecipesByInputAndKitchenwareMatchAnyInput(Minecraft.getInstance().player, List.of(NMICommonItemStackUtil.get(identifier)),kitchenware.blockTagKey());
            List<T> display = new ArrayList<>(byInput.stream().map(NMIRecipeHolder::recipe).map(builder).toList());
            if (NMICommonItemUtil.get(kitchenware.kitchenwareItem()).equals(identifier)) {
                NMIClientRecipeUtil.getRecipesByKitchenware(Minecraft.getInstance().player, kitchenware.blockTagKey()).stream().map(NMIRecipeHolder::recipe).map(builder).forEach(display::add);
            }
            return Optional.of(display);
        }
        return Optional.empty();
    }

    public static class BoilingPot extends NMIRecipeDisplayGenerator<NMIRecipeReiDisplay.BoilingPot> {
        public BoilingPot() {
            super(NMIKitchenware.BOILING_POT.get(),NMIRecipeReiDisplay.BoilingPot::new);
        }
    }

    public static class CuttingBoard extends NMIRecipeDisplayGenerator<NMIRecipeReiDisplay.CuttingBoard> {
        public CuttingBoard() {
            super(NMIKitchenware.CUTTING_BOARD.get(),NMIRecipeReiDisplay.CuttingBoard::new);
        }
    }

    public static class FryingPan extends NMIRecipeDisplayGenerator<NMIRecipeReiDisplay.FryingPan> {
        public FryingPan() {
            super(NMIKitchenware.FRYING_PAN.get(),NMIRecipeReiDisplay.FryingPan::new);
        }
    }
    public static class Steamer extends NMIRecipeDisplayGenerator<NMIRecipeReiDisplay.Steamer> {
        public Steamer() {
            super(NMIKitchenware.STEAMER.get(),NMIRecipeReiDisplay.Steamer::new);
        }
    }
    public static class Grill extends NMIRecipeDisplayGenerator<NMIRecipeReiDisplay.Grill> {
        public Grill() {
            super(NMIKitchenware.GRILL.get(),NMIRecipeReiDisplay.Grill::new);
        }
    }
}
