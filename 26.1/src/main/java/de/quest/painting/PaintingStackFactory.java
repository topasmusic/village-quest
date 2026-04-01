package de.quest.painting;

import de.quest.VillageQuest;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.decoration.painting.PaintingVariant;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

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

    public static ItemStack create(ServerLevel world, String variantPath) {
        if (world == null || variantPath == null || variantPath.isBlank()) {
            return ItemStack.EMPTY;
        }

        ResourceKey<PaintingVariant> variantKey = ResourceKey.create(
                Registries.PAINTING_VARIANT,
                Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID, variantPath)
        );
        Holder<PaintingVariant> variantEntry = world.registryAccess()
                .lookupOrThrow(Registries.PAINTING_VARIANT)
                .getOrThrow(variantKey);
        return create(variantEntry);
    }

    public static ItemStack create(Holder<PaintingVariant> variantEntry) {
        if (variantEntry == null) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = new ItemStack(Items.PAINTING);
        stack.set(DataComponents.PAINTING_VARIANT, variantEntry);

        ResourceKey<PaintingVariant> variantKey = variantEntry.unwrapKey().orElse(null);
        if (variantKey == null || !VillageQuest.MOD_ID.equals(variantKey.identifier().getNamespace())) {
            return stack;
        }

        PaintingMetadata metadata = METADATA.get(variantKey.identifier().getPath());
        if (metadata == null) {
            return stack;
        }

        stack.set(DataComponents.ITEM_NAME, Component.translatable(metadata.titleKey()).withStyle(ChatFormatting.GREEN));
        stack.set(DataComponents.LORE, new ItemLore(List.of(
                Component.translatable(metadata.loreKey()).withStyle(ChatFormatting.GRAY)
        )));
        return stack;
    }

    public static boolean isVillageQuestPainting(Holder<PaintingVariant> variantEntry) {
        if (variantEntry == null) {
            return false;
        }
        ResourceKey<PaintingVariant> variantKey = variantEntry.unwrapKey().orElse(null);
        return variantKey != null && VillageQuest.MOD_ID.equals(variantKey.identifier().getNamespace());
    }

    private record PaintingMetadata(String titleKey, String loreKey) {}
}
