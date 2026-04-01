package de.quest.shop;

import de.quest.VillageQuest;
import de.quest.economy.CurrencyService;
import de.quest.painting.PaintingStackFactory;
import de.quest.quest.daily.DailyQuestService;
import de.quest.quest.special.SpecialQuestService;
import de.quest.quest.weekly.WeeklyQuestService;
import de.quest.reputation.ReputationService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.decoration.painting.PaintingVariant;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

public final class ShopService {
    private static final Map<String, ShopOffer> OFFERS = createOffers();

    private ShopService() {}

    private static Map<String, ShopOffer> createOffers() {
        Map<String, ShopOffer> offers = new LinkedHashMap<>();
        offers.put("proviantbeutel", new ShopOffer(
                "proviantbeutel",
                "travel",
                Component.translatable("text.village-quest.shop.offer.proviantbeutel.title"),
                Component.translatable("text.village-quest.shop.offer.proviantbeutel.description"),
                6L,
                world -> previewStack(Items.BUNDLE, "text.village-quest.shop.offer.proviantbeutel.title"),
                (world, player) -> deliver(player, createProvisionsSatchelLoot(world))
        ));
        offers.put("hunters_satchel", new ShopOffer(
                "hunters_satchel",
                "travel",
                Component.translatable("text.village-quest.shop.offer.hunters_satchel.title"),
                Component.translatable("text.village-quest.shop.offer.hunters_satchel.description"),
                7L,
                world -> previewStack(Items.BUNDLE, "text.village-quest.shop.offer.hunters_satchel.title"),
                (world, player) -> deliver(player,
                        new ItemStack(Items.ARROW, 32),
                        new ItemStack(Items.COOKED_BEEF, 6))
        ));
        offers.put("honigfass", new ShopOffer(
                "honigfass",
                "decor",
                Component.translatable("text.village-quest.shop.offer.honigfass.title"),
                Component.translatable("text.village-quest.shop.offer.honigfass.description"),
                5L,
                world -> VillageQuest.createHoneyBarrelHead(),
                (world, player) -> deliver(player, VillageQuest.createHoneyBarrelHead())
        ));
        offers.put("bienenwabe", new ShopOffer(
                "bienenwabe",
                "decor",
                Component.translatable("text.village-quest.shop.offer.bienenwabe.title"),
                Component.translatable("text.village-quest.shop.offer.bienenwabe.description"),
                5L,
                world -> VillageQuest.createAltRewardHead(),
                (world, player) -> deliver(player, VillageQuest.createAltRewardHead())
        ));
        offers.put("beekeepers_kit", new ShopOffer(
                "beekeepers_kit",
                "utility",
                Component.translatable("text.village-quest.shop.offer.beekeepers_kit.title"),
                Component.translatable("text.village-quest.shop.offer.beekeepers_kit.description"),
                12L,
                world -> previewStack(Items.BEEHIVE, "text.village-quest.shop.offer.beekeepers_kit.title"),
                (world, player) -> deliver(player,
                        new ItemStack(Items.BEEHIVE),
                        new ItemStack(Items.CAMPFIRE),
                        new ItemStack(Items.GLASS_BOTTLE, 8))
        ));
        offers.put("gemaelde_begleiter", new ShopOffer(
                "gemaelde_begleiter",
                "decor",
                Component.translatable("text.village-quest.shop.offer.gemaelde_begleiter.title"),
                Component.translatable("text.village-quest.shop.offer.gemaelde_begleiter.description"),
                15L,
                world -> DailyQuestService.createCompanionPainting(world, false),
                (world, player) -> deliver(player, DailyQuestService.createCompanionPainting(world, false))
        ));
        offers.put("gemaelde_majestaet", new ShopOffer(
                "gemaelde_majestaet",
                "decor",
                Component.translatable("text.village-quest.shop.offer.gemaelde_majestaet.title"),
                Component.translatable("text.village-quest.shop.offer.gemaelde_majestaet.description"),
                15L,
                world -> DailyQuestService.createCompanionPainting(world, true),
                (world, player) -> deliver(player, DailyQuestService.createCompanionPainting(world, true))
        ));
        addPaintingOffer(
                offers,
                "gemaelde_pepe_the_almighty",
                10L,
                "text.village-quest.shop.offer.gemaelde_pepe_the_almighty.title",
                "text.village-quest.shop.offer.gemaelde_pepe_the_almighty.description",
                "pepe_the_almighty",
                "item.village-quest.painting.pepe_the_almighty",
                "item.village-quest.painting.square_large.lore"
        );
        addPaintingOffer(
                offers,
                "gemaelde_over_there",
                10L,
                "text.village-quest.shop.offer.gemaelde_over_there.title",
                "text.village-quest.shop.offer.gemaelde_over_there.description",
                "over_there",
                "item.village-quest.painting.over_there",
                "item.village-quest.painting.square_large.lore"
        );
        addPaintingOffer(
                offers,
                "gemaelde_good_doge",
                5L,
                "text.village-quest.shop.offer.gemaelde_good_doge.title",
                "text.village-quest.shop.offer.gemaelde_good_doge.description",
                "good_doge",
                "item.village-quest.painting.good_doge",
                "item.village-quest.painting.square_small.lore"
        );
        addPaintingOffer(
                offers,
                "gemaelde_something_is_sus",
                5L,
                "text.village-quest.shop.offer.gemaelde_something_is_sus.title",
                "text.village-quest.shop.offer.gemaelde_something_is_sus.description",
                "something_is_sus",
                "item.village-quest.painting.something_is_sus",
                "item.village-quest.painting.square_small.lore"
        );
        addPaintingOffer(
                offers,
                "gemaelde_the_legend",
                10L,
                "text.village-quest.shop.offer.gemaelde_the_legend.title",
                "text.village-quest.shop.offer.gemaelde_the_legend.description",
                "the_legend",
                "item.village-quest.painting.the_legend",
                "item.village-quest.painting.square_large.lore"
        );
        addPaintingOffer(
                offers,
                "gemaelde_ancient_warrior",
                5L,
                "text.village-quest.shop.offer.gemaelde_ancient_warrior.title",
                "text.village-quest.shop.offer.gemaelde_ancient_warrior.description",
                "ancient_warrior",
                "item.village-quest.painting.ancient_warrior",
                "item.village-quest.painting.square_small.lore"
        );
        addPaintingOffer(
                offers,
                "gemaelde_happy_doge",
                15L,
                "text.village-quest.shop.offer.gemaelde_happy_doge.title",
                "text.village-quest.shop.offer.gemaelde_happy_doge.description",
                "happy_doge",
                "item.village-quest.painting.happy_doge",
                "item.village-quest.painting.landscape.lore"
        );
        offers.put("zombie_trophy", new ShopOffer(
                "zombie_trophy",
                "decor",
                Component.translatable("text.village-quest.shop.offer.zombie_trophy.title"),
                Component.translatable("text.village-quest.shop.offer.zombie_trophy.description"),
                10L,
                world -> previewStack(Items.ZOMBIE_HEAD, "text.village-quest.shop.offer.zombie_trophy.title"),
                (world, player) -> deliver(player, new ItemStack(Items.ZOMBIE_HEAD))
        ));
        offers.put("skeleton_trophy", new ShopOffer(
                "skeleton_trophy",
                "decor",
                Component.translatable("text.village-quest.shop.offer.skeleton_trophy.title"),
                Component.translatable("text.village-quest.shop.offer.skeleton_trophy.description"),
                12L,
                world -> previewStack(Items.SKELETON_SKULL, "text.village-quest.shop.offer.skeleton_trophy.title"),
                (world, player) -> deliver(player, new ItemStack(Items.SKELETON_SKULL))
        ));
        offers.put("creeper_trophy", new ShopOffer(
                "creeper_trophy",
                "decor",
                Component.translatable("text.village-quest.shop.offer.creeper_trophy.title"),
                Component.translatable("text.village-quest.shop.offer.creeper_trophy.description"),
                16L,
                world -> previewStack(Items.CREEPER_HEAD, "text.village-quest.shop.offer.creeper_trophy.title"),
                (world, player) -> deliver(player, new ItemStack(Items.CREEPER_HEAD))
        ));
        return offers;
    }

    public static ShopOffer offer(String offerId) {
        if (offerId == null || offerId.isBlank()) {
            return null;
        }
        return OFFERS.get(offerId);
    }

    public static boolean hasOffers() {
        return !OFFERS.isEmpty();
    }

    public static List<String> rollPilgrimOfferIds(net.minecraft.util.RandomSource random, int count) {
        List<String> ids = new ArrayList<>(OFFERS.keySet());
        return shuffleAndTrim(random, ids, count);
    }

    public static List<String> rollPilgrimOfferIds(net.minecraft.util.RandomSource random,
                                                   ServerLevel world,
                                                   UUID playerId,
                                                   int count) {
        List<String> ids = availableOfferIds(world, playerId);
        if (ids.isEmpty()) {
            ids = new ArrayList<>(OFFERS.keySet());
        }
        return shuffleAndTrim(random, ids, count);
    }

    public static List<String> availableOfferIds(ServerLevel world, UUID playerId) {
        List<String> ids = new ArrayList<>();
        for (String offerId : OFFERS.keySet()) {
            if (isOfferUnlocked(world, playerId, offerId)) {
                ids.add(offerId);
            }
        }
        return ids;
    }

    public static boolean isOfferUnlocked(ServerLevel world, UUID playerId, String offerId) {
        if (offerId == null || offerId.isBlank()) {
            return false;
        }
        if (world == null || playerId == null) {
            return true;
        }
        return ReputationService.isOfferUnlocked(world, playerId, offerId);
    }

    private static List<String> shuffleAndTrim(net.minecraft.util.RandomSource random, List<String> ids, int count) {
        if (ids.isEmpty()) {
            return ids;
        }
        for (int i = ids.size() - 1; i > 0; i--) {
            int swapIndex = random.nextInt(i + 1);
            Collections.swap(ids, i, swapIndex);
        }
        if (count < ids.size()) {
            return new ArrayList<>(ids.subList(0, Math.max(0, count)));
        }
        return ids;
    }

    public static int buy(ServerLevel world, ServerPlayer player, String offerId) {
        if (world == null || player == null) {
            return 0;
        }

        ShopOffer offer = offer(offerId);
        if (offer == null) {
            player.sendSystemMessage(Component.translatable("command.village-quest.shop.offer_missing").withStyle(ChatFormatting.RED), false);
            return 0;
        }
        ReputationService.OfferUnlock unlock = ReputationService.unlockForOffer(offerId);
        if (unlock != null && !isOfferUnlocked(world, player.getUUID(), offerId)) {
            player.sendSystemMessage(Component.translatable(
                    "command.village-quest.shop.locked",
                    ReputationService.displayName(unlock.track()),
                    ReputationService.displayUnlockTitle(unlock),
                    Component.literal(Integer.toString(ReputationService.get(world, player.getUUID(), unlock.track()))).withStyle(unlock.track().color()),
                    Component.literal(Integer.toString(unlock.requiredReputation())).withStyle(unlock.track().color())
            ).withStyle(ChatFormatting.RED), false);
            return 0;
        }
        if (!CurrencyService.canAfford(world, player.getUUID(), offer.price())) {
            player.sendSystemMessage(Component.translatable(
                    "command.village-quest.shop.not_enough",
                    CurrencyService.formatBalance(offer.price()),
                    CurrencyService.formatBalance(CurrencyService.getBalance(world, player.getUUID()))
            ).withStyle(ChatFormatting.RED), false);
            return 0;
        }

        ShopOffer.PurchaseResult result = offer.purchaseHandler().purchase(world, player);
        if (!result.success()) {
            player.sendSystemMessage(result.message(), false);
            return 0;
        }

        if (!CurrencyService.removeBalance(world, player.getUUID(), offer.price())) {
            player.sendSystemMessage(Component.translatable("command.village-quest.shop.not_enough",
                    CurrencyService.formatBalance(offer.price()),
                    CurrencyService.formatBalance(CurrencyService.getBalance(world, player.getUUID()))
            ).withStyle(ChatFormatting.RED), false);
            return 0;
        }

        SpecialQuestService.onPilgrimPurchase(world, player, offerId);
        WeeklyQuestService.onPilgrimPurchase(world, player, offerId);
        long newBalance = CurrencyService.getBalance(world, player.getUUID());
        player.sendSystemMessage(Component.translatable(
                "command.village-quest.shop.buy.success",
                offer.title(),
                CurrencyService.formatBalance(offer.price()),
                CurrencyService.formatBalance(newBalance)
        ).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static ShopOffer.PurchaseResult deliver(ServerPlayer player, ItemStack... stacks) {
        if (player == null || stacks == null || stacks.length == 0) {
            return ShopOffer.PurchaseResult.fail(Component.translatable("command.village-quest.shop.offer_missing").withStyle(ChatFormatting.RED));
        }

        for (ItemStack stack : stacks) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            giveOrDrop(player, stack.copy());
        }
        return ShopOffer.PurchaseResult.success(Component.empty());
    }

    private static ItemStack[] createProvisionsSatchelLoot(ServerLevel world) {
        List<ItemStack> rewards = new ArrayList<>();
        rewards.add(new ItemStack(Items.GOLDEN_CARROT, 32));

        if (world != null && world.getRandom().nextFloat() < 0.10f) {
            rewards.add(new ItemStack(Items.GOLDEN_APPLE, 1));
        }

        rewards.add(rollProvisionsSatchelBonus(world));
        if (world != null && world.getRandom().nextFloat() < 0.65f) {
            rewards.add(rollProvisionsSatchelBonus(world));
        }

        rewards.removeIf(stack -> stack == null || stack.isEmpty());
        return rewards.toArray(ItemStack[]::new);
    }

    private static ItemStack rollProvisionsSatchelBonus(ServerLevel world) {
        int roll = world == null ? 0 : world.getRandom().nextInt(100);
        if (roll < 24) {
            return new ItemStack(Items.GOLD_INGOT, randomRange(world, 8, 20));
        }
        if (roll < 42) {
            return new ItemStack(Items.IRON_INGOT, randomRange(world, 12, 24));
        }
        if (roll < 56) {
            return new ItemStack(Items.EMERALD, randomRange(world, 4, 10));
        }
        if (roll < 64) {
            return new ItemStack(Items.DIAMOND, randomRange(world, 1, 4));
        }
        if (roll < 76) {
            return new ItemStack(Items.TORCH, randomRange(world, 24, 48));
        }
        if (roll < 88) {
            return new ItemStack(Items.COOKED_BEEF, randomRange(world, 8, 16));
        }
        return new ItemStack(Items.COAL, randomRange(world, 16, 32));
    }

    private static int randomRange(ServerLevel world, int minInclusive, int maxInclusive) {
        if (maxInclusive <= minInclusive) {
            return minInclusive;
        }
        if (world == null) {
            return minInclusive;
        }
        return minInclusive + world.getRandom().nextInt(maxInclusive - minInclusive + 1);
    }

    private static ItemStack previewStack(Item item, String titleKey) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.translatable(titleKey).withStyle(ChatFormatting.GREEN));
        return stack;
    }

    private static void addPaintingOffer(Map<String, ShopOffer> offers,
                                         String offerId,
                                         long price,
                                         String offerTitleKey,
                                         String offerDescriptionKey,
                                         String variantPath,
                                         String itemTitleKey,
                                         String loreKey) {
        offers.put(offerId, new ShopOffer(
                offerId,
                "art",
                Component.translatable(offerTitleKey),
                Component.translatable(offerDescriptionKey),
                price,
                world -> createPaintingStack(world, variantPath, itemTitleKey, loreKey),
                (world, player) -> deliver(player, createPaintingStack(world, variantPath, itemTitleKey, loreKey))
        ));
    }

    private static ItemStack createPaintingStack(ServerLevel world, String variantPath, String titleKey, String loreKey) {
        return PaintingStackFactory.create(world, variantPath);
    }

    private static void giveOrDrop(ServerPlayer player, ItemStack stack) {
        ItemStack remainder = stack.copy();
        boolean inserted = player.getInventory().add(remainder);
        if (!inserted || !remainder.isEmpty()) {
            if (!remainder.isEmpty()) {
                player.drop(remainder, false);
            }
            player.sendSystemMessage(Component.translatable("message.village-quest.daily.inventory_full.prefix").withStyle(ChatFormatting.GRAY)
                    .append(stack.getDisplayName())
                    .append(Component.translatable("message.village-quest.daily.inventory_full.suffix").withStyle(ChatFormatting.GRAY)), false);
        } else {
            player.inventoryMenu.broadcastChanges();
        }
    }
}
