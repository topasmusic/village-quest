package de.quest.recipe;

import de.quest.VillageQuest;
import de.quest.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;

public final class VillageQuestRecipeBookService {
    private static final int CHECK_INTERVAL_TICKS = 20;
    private static final TagKey<Item> WHITE_FLOWERS = TagKey.create(
            Registries.ITEM, Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID, "white_flowers"));

    private VillageQuestRecipeBookService() {}

    public static void onServerTick(MinecraftServer server) {
        if (server == null || server.getTickCount() % CHECK_INTERVAL_TICKS != 0) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            unlockEligibleRecipes(player);
        }
    }

    public static void unlockEligibleRecipes(ServerPlayer player) {
        if (player == null) return;
        List<ResourceKey<Recipe<?>>> unlocked = new ArrayList<>();
        for (RecipeUnlock unlock : unlocks()) {
            if (!player.getRecipeBook().contains(unlock.key()) && hasAll(player, unlock.requirements())) {
                unlocked.add(unlock.key());
            }
        }
        if (!unlocked.isEmpty()) {
            player.awardRecipesByKey(unlocked);
        }
    }

    private static List<RecipeUnlock> unlocks() {
        return List.of(
                recipe("emberglass_lantern",
                        item(Items.IRON_NUGGET, 4), item(Items.AMETHYST_SHARD, 1)),
                recipe("guild_milestone",
                        item(Items.STONE_BRICKS, 6), item(Items.GOLD_NUGGET, 1)),
                recipe("guild_notice_post",
                        item(Items.PAPER, 2), item(Items.GOLD_NUGGET, 1),
                        item(Items.DARK_OAK_PLANKS, 3), item(Items.STICK, 1)),
                recipe("guild_wayshrine",
                        item(Items.STONE_BRICKS, 5), item(Items.AMETHYST_SHARD, 1),
                        item(Items.GOLD_INGOT, 2), item(ModItems.RESTORED_SHRINE_CORE, 1)),
                recipe("friedens_haube",
                        item(Items.LEATHER_HELMET, 1), item(Items.FEATHER, 1),
                        item(Items.DYE.white(), 1), tag(WHITE_FLOWERS, 1)),
                recipe("friedens_brustplatte",
                        item(Items.LEATHER_CHESTPLATE, 1), item(Items.FEATHER, 1),
                        item(Items.DYE.white(), 1), tag(WHITE_FLOWERS, 1)),
                recipe("friedens_beinschiene",
                        item(Items.LEATHER_LEGGINGS, 1), item(Items.FEATHER, 1),
                        item(Items.DYE.white(), 1), tag(WHITE_FLOWERS, 1)),
                recipe("friedens_stiefel",
                        item(Items.LEATHER_BOOTS, 1), item(Items.FEATHER, 1),
                        item(Items.DYE.white(), 1), tag(WHITE_FLOWERS, 1))
        );
    }

    private static boolean hasAll(ServerPlayer player, List<MaterialRequirement> requirements) {
        for (MaterialRequirement requirement : requirements) {
            int found = 0;
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                if (requirement.matches(stack)) {
                    found += stack.getCount();
                    if (found >= requirement.count()) break;
                }
            }
            if (found < requirement.count()) return false;
        }
        return true;
    }

    private static RecipeUnlock recipe(String path, MaterialRequirement... requirements) {
        ResourceKey<Recipe<?>> key = ResourceKey.create(
                Registries.RECIPE, Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID, path));
        return new RecipeUnlock(key, List.of(requirements));
    }

    private static MaterialRequirement item(Item item, int count) {
        return new MaterialRequirement(item, null, count);
    }

    private static MaterialRequirement tag(TagKey<Item> tag, int count) {
        return new MaterialRequirement(null, tag, count);
    }

    private record RecipeUnlock(ResourceKey<Recipe<?>> key, List<MaterialRequirement> requirements) {}

    private record MaterialRequirement(Item item, TagKey<Item> tag, int count) {
        private boolean matches(ItemStack stack) {
            return stack != null && !stack.isEmpty()
                    && (item != null ? stack.is(item) : tag != null && stack.is(tag));
        }
    }
}
