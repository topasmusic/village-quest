package de.quest.painting;

import de.quest.VillageQuest;
import de.quest.registry.ModItems;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.decoration.painting.PaintingVariant;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Map;

public final class PaintingStackFactory {
    private static final Map<String, PaintingMetadata> METADATA = Map.ofEntries(
            Map.entry("treuer_begleiter", new PaintingMetadata("item.village-quest.painting.treuer_begleiter", "item.village-quest.painting.companion.lore")),
            Map.entry("eure_majestaet", new PaintingMetadata("item.village-quest.painting.eure_majestaet", "item.village-quest.painting.companion.lore")),
            Map.entry("pepe_the_almighty", new PaintingMetadata("item.village-quest.painting.pepe_the_almighty", "item.village-quest.painting.square_large.lore")),
            Map.entry("over_there", new PaintingMetadata("item.village-quest.painting.over_there", "item.village-quest.painting.square_large.lore")),
            Map.entry("good_doge", new PaintingMetadata("item.village-quest.painting.good_doge", "item.village-quest.painting.square_small.lore")),
            Map.entry("something_is_sus", new PaintingMetadata("item.village-quest.painting.something_is_sus", "item.village-quest.painting.square_small.lore")),
            Map.entry("the_legend", new PaintingMetadata("item.village-quest.painting.the_legend", "item.village-quest.painting.square_large.lore")),
            Map.entry("ancient_warrior", new PaintingMetadata("item.village-quest.painting.ancient_warrior", "item.village-quest.painting.square_small.lore")),
            Map.entry("happy_doge", new PaintingMetadata("item.village-quest.painting.happy_doge", "item.village-quest.painting.landscape.lore"))
    );

    private PaintingStackFactory() {}

    public static ItemStack create(ServerWorld world, String variantPath) {
        if (world == null || variantPath == null || variantPath.isBlank()) {
            return ItemStack.EMPTY;
        }

        RegistryKey<PaintingVariant> variantKey = RegistryKey.of(
                RegistryKeys.PAINTING_VARIANT,
                Identifier.of(VillageQuest.MOD_ID, variantPath)
        );
        RegistryEntry<PaintingVariant> variantEntry = world.getRegistryManager()
                .getOrThrow(RegistryKeys.PAINTING_VARIANT)
                .getOrThrow(variantKey);
        return create(variantEntry);
    }

    public static ItemStack create(RegistryEntry<PaintingVariant> variantEntry) {
        if (variantEntry == null) {
            return ItemStack.EMPTY;
        }

        RegistryKey<PaintingVariant> variantKey = variantEntry.getKey().orElse(null);
        if (isApiaryCharterPlaque(variantKey)) {
            return new ItemStack(ModItems.APIARY_CHARTER_PLAQUE);
        }

        ItemStack stack = new ItemStack(Items.PAINTING);
        stack.set(DataComponentTypes.PAINTING_VARIANT, variantEntry);
        if (variantKey == null || !VillageQuest.MOD_ID.equals(variantKey.getValue().getNamespace())) {
            return stack;
        }

        PaintingMetadata metadata = METADATA.get(variantKey.getValue().getPath());
        if (metadata == null) {
            return stack;
        }

        stack.set(DataComponentTypes.ITEM_NAME, Text.translatable(metadata.titleKey()).formatted(Formatting.GREEN));
        stack.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                Text.translatable(metadata.loreKey()).formatted(Formatting.GRAY)
        )));
        return stack;
    }

    public static boolean isVillageQuestPainting(RegistryEntry<PaintingVariant> variantEntry) {
        if (variantEntry == null) {
            return false;
        }
        RegistryKey<PaintingVariant> variantKey = variantEntry.getKey().orElse(null);
        return variantKey != null && VillageQuest.MOD_ID.equals(variantKey.getValue().getNamespace());
    }

    private static boolean isApiaryCharterPlaque(RegistryKey<PaintingVariant> variantKey) {
        return variantKey != null
                && VillageQuest.MOD_ID.equals(variantKey.getValue().getNamespace())
                && "apiary_charter_plaque".equals(variantKey.getValue().getPath());
    }

    private record PaintingMetadata(String titleKey, String loreKey) {}
}
