package de.quest.shrine;

import de.quest.content.story.ShadowsTradeRoadEncounterService;
import de.quest.content.story.VillagerDialogueService;
import de.quest.caravan.TradeRouteService;
import de.quest.data.PlayerQuestData;
import de.quest.data.QuestState;
import de.quest.quest.QuestBookHelper;
import de.quest.quest.daily.FirstDailyChoiceService;
import de.quest.questmaster.QuestMasterUiService;
import de.quest.village.GuildCornerPlacementService;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.phys.AABB;

/** Personal first-village introduction between the first Daily and later local stories. */
public final class VillageWelcomeService {
    static final int GREETING_TARGET = 3;
    private static final String ACTIVE = "guild_intro.welcome_active";
    private static final String COMPLETED = "guild_intro.welcome_completed";
    private static final String VILLAGE = "guild_intro.welcome_village";
    private static final String GREETINGS = "guild_intro.welcome_greetings";
    private static final String GREETED_PREFIX = "guild_intro.welcome_greeted_";

    private VillageWelcomeService() {}

    public static void unlockAfterFirstDaily(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null) {
            return;
        }
        player.sendSystemMessage(Component.translatable("message.village-quest.guild_intro.daily_complete")
                .withStyle(ChatFormatting.GOLD), false);
        QuestBookHelper.refreshQuestBook(world, player);
    }

    public static boolean onVillagerContact(ServerLevel world, ServerPlayer player, Villager villager) {
        if (world == null || player == null || villager == null) {
            return false;
        }
        PlayerQuestData data = QuestState.get(world.getServer()).getPlayerData(player.getUUID());
        if (!FirstDailyChoiceService.isCompleted(data) || data.hasTradeRouteFlag(COMPLETED)) {
            return false;
        }
        ShadowsTradeRoadEncounterService.VillageMarker marker =
                ShadowsTradeRoadEncounterService.currentVillage(world, player.blockPosition());
        if (marker == null) {
            return false;
        }

        GuildCornerPlacementService.onLocalVillageVisit(world, player, marker);

        if (!data.hasTradeRouteFlag(ACTIVE) && !hasEnoughVillagers(world, marker)) {
            player.sendSystemMessage(Component.translatable("message.village-quest.guild_intro.village_too_small")
                    .withStyle(ChatFormatting.GRAY), false);
            return true;
        }

        if (data.hasTradeRouteFlag(ACTIVE)) {
            int targetVillage = data.getTradeRouteInt(VILLAGE) - 1;
            int visitedVillage = VillageBondService.findVillage(data, marker.centerX(), marker.centerZ());
            if (visitedVillage != targetVillage) {
                player.sendSystemMessage(Component.translatable("message.village-quest.guild_intro.other_village")
                        .withStyle(ChatFormatting.GRAY), false);
                return true;
            }
        }

        VillageContactService.ContactResult contact = VillageContactService.establish(world, player.getUUID(), marker);
        if (!contact.accepted()) {
            return false;
        }
        int villageIndex = contact.contact().villageIndex();
        boolean started = !data.hasTradeRouteFlag(ACTIVE);
        if (started) {
            begin(data, villageIndex);
            QuestState.get(world.getServer()).setDirty();
            VillagerDialogueService.sendDialogue(player, villager, Component.translatable(
                    "message.village-quest.guild_intro.contact_started", contact.contact().type().label(),
                    GREETING_TARGET - 1));
        }

        GreetingResult result = greet(data, villager.getUUID());
        if (result == GreetingResult.REPEATED) {
            VillagerDialogueService.sendDialogue(player, villager, Component.translatable(
                    "message.village-quest.guild_intro.greeting_repeated"));
            return true;
        }

        QuestState.get(world.getServer()).setDirty();
        if (result == GreetingResult.COMPLETED) {
            giveKeepsake(player, contact.contact().type());
            VillagerDialogueService.sendDialogue(player, villager, Component.translatable(
                    "message.village-quest.guild_intro.completed", contact.contact().type().label()));
            player.sendSystemMessage(Component.translatable("message.village-quest.guild_intro.keepsake_received")
                    .withStyle(ChatFormatting.GOLD), false);
            world.playSound(null, player.blockPosition(), SoundEvents.VILLAGER_CELEBRATE,
                    SoundSource.PLAYERS, 0.9f, 1.05f);
        } else if (!started) {
            VillagerDialogueService.sendDialogue(player, villager, Component.translatable(
                    "message.village-quest.guild_intro.greeting_progress",
                    data.getTradeRouteInt(GREETINGS), GREETING_TARGET));
            world.playSound(null, player.blockPosition(), SoundEvents.VILLAGER_YES,
                    SoundSource.PLAYERS, 0.7f, 1.1f);
        }
        QuestBookHelper.refreshQuestBook(world, player);
        QuestMasterUiService.refreshIfOpen(world, player);
        return true;
    }

    static void begin(PlayerQuestData data, int villageIndex) {
        if (data == null || villageIndex < 0 || data.hasTradeRouteFlag(COMPLETED)) {
            return;
        }
        data.setTradeRouteFlag(ACTIVE, true);
        data.setTradeRouteInt(VILLAGE, villageIndex + 1);
    }

    static GreetingResult greet(PlayerQuestData data, UUID villagerId) {
        if (data == null || villagerId == null || !data.hasTradeRouteFlag(ACTIVE)) {
            return GreetingResult.IGNORED;
        }
        String key = GREETED_PREFIX + villagerId;
        if (data.hasTradeRouteFlag(key)) {
            return GreetingResult.REPEATED;
        }
        data.setTradeRouteFlag(key, true);
        int greetings = Math.min(GREETING_TARGET, data.getTradeRouteInt(GREETINGS) + 1);
        data.setTradeRouteInt(GREETINGS, greetings);
        if (greetings >= GREETING_TARGET) {
            data.setTradeRouteFlag(ACTIVE, false);
            data.setTradeRouteFlag(COMPLETED, true);
            return GreetingResult.COMPLETED;
        }
        return GreetingResult.PROGRESSED;
    }

    public static Component journalNextAction(ServerLevel world, UUID playerId) {
        if (world == null || playerId == null) {
            return Component.empty();
        }
        PlayerQuestData data = QuestState.get(world.getServer()).getPlayerData(playerId);
        if (FirstDailyChoiceService.canChoose(data)) {
            return Component.translatable("screen.village-quest.journal.guild_intro.choose_daily");
        }
        if (FirstDailyChoiceService.isActive(data)) {
            return Component.translatable("screen.village-quest.journal.guild_intro.finish_daily");
        }
        if (!FirstDailyChoiceService.isCompleted(data)) {
            return Component.empty();
        }
        if (data.hasTradeRouteFlag(ACTIVE)) {
            int villageIndex = data.getTradeRouteInt(VILLAGE) - 1;
            VillageContactService.VillageContact contact = VillageContactService.read(data, villageIndex);
            Component type = contact == null ? Component.translatable("text.village-quest.village_bond.type.granary")
                    : contact.type().label();
            return Component.translatable("screen.village-quest.journal.guild_intro.greet",
                    type, data.getTradeRouteInt(GREETINGS), GREETING_TARGET);
        }
        if (data.hasTradeRouteFlag(COMPLETED)) {
            return TradeRouteService.routeCount(world, playerId) == 0
                    ? Component.translatable("screen.village-quest.journal.guild_intro.preview")
                    : Component.empty();
        }
        return Component.translatable("screen.village-quest.journal.guild_intro.find_village");
    }

    public static boolean isCompleted(PlayerQuestData data) {
        return data != null && data.hasTradeRouteFlag(COMPLETED);
    }

    static boolean isActive(PlayerQuestData data) {
        return data != null && data.hasTradeRouteFlag(ACTIVE);
    }

    static int greetingCount(PlayerQuestData data) {
        return data == null ? 0 : Math.max(0, Math.min(GREETING_TARGET, data.getTradeRouteInt(GREETINGS)));
    }

    static boolean isCurrentWelcomeVillage(PlayerQuestData data,
                                           ShadowsTradeRoadEncounterService.VillageMarker marker) {
        if (!isActive(data) || marker == null) {
            return false;
        }
        int targetVillage = data.getTradeRouteInt(VILLAGE) - 1;
        return targetVillage >= 0
                && targetVillage == VillageBondService.findVillage(data, marker.centerX(), marker.centerZ());
    }

    private static void giveKeepsake(ServerPlayer player, VillageBondType type) {
        ItemStack keepsake = new ItemStack(Items.PAPER);
        keepsake.set(DataComponents.CUSTOM_NAME, Component.translatable(
                "item.village-quest.village_welcome_note").withStyle(ChatFormatting.GOLD));
        keepsake.set(DataComponents.LORE, new ItemLore(java.util.List.of(
                Component.translatable("item.village-quest.village_welcome_note.lore", type.label())
                        .withStyle(ChatFormatting.DARK_GRAY))));
        if (!player.getInventory().add(keepsake)) {
            player.drop(keepsake, false);
        }
        player.inventoryMenu.broadcastChanges();
    }

    private static boolean hasEnoughVillagers(ServerLevel world,
                                               ShadowsTradeRoadEncounterService.VillageMarker marker) {
        AABB area = new AABB(marker.minX() - 8.0, world.getMinY(), marker.minZ() - 8.0,
                marker.maxX() + 9.0, world.getMaxY(), marker.maxZ() + 9.0);
        return world.getEntitiesOfClass(Villager.class, area, Villager::isAlive).size() >= GREETING_TARGET;
    }

    enum GreetingResult {
        IGNORED,
        REPEATED,
        PROGRESSED,
        COMPLETED
    }
}
