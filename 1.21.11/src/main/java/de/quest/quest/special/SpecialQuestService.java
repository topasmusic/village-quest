package de.quest.quest.special;

import de.quest.data.PlayerQuestData;
import de.quest.data.QuestState;
import de.quest.entity.PilgrimEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import java.util.UUID;

public final class SpecialQuestService {
    public static final int TOTAL_SPECIAL_QUESTS = 14;

    private SpecialQuestService() {}

    public static java.util.List<de.quest.quest.JournalActiveQuest> journalEntries(ServerLevel world,UUID player) {
        var entries=new java.util.ArrayList<de.quest.quest.JournalActiveQuest>();
        add(entries,"relic",ShardRelicQuestService.openStatus(world,player));
        add(entries,"merchant_seal",MerchantSealQuestService.openStatus(world,player));
        add(entries,"shepherd_flute",ShepherdFluteQuestService.openStatus(world,player));
        add(entries,"apiarist_smoker",ApiaristSmokerQuestService.openStatus(world,player));
        add(entries,"surveyor_compass",SurveyorCompassQuestService.openStatus(world,player));
        var friends=FriendsQuestService.progress(world,player);
        for(String who:java.util.List.of("simon","lutte","marius")) if(friends.active(who)) {
            var title=net.minecraft.network.chat.Component.translatable("quest.village-quest.friends."+who+"."+(friends.stage(who)+1)+".title");
            entries.add(new de.quest.quest.JournalActiveQuest(new de.quest.quest.TrackerEntry("special:friend:"+who,"special",title,
                    FriendsQuestService.shortObjective(world.getServer().getPlayerList().getPlayer(player),friends,who)),FriendsQuestService.lines(world,world.getServer().getPlayerList().getPlayer(player),friends,who),-1));
        }
        if(friends.number("simon.herbs")==1) entries.add(new de.quest.quest.JournalActiveQuest(new de.quest.quest.TrackerEntry(
                "special:friend:simon_herbs","special",net.minecraft.network.chat.Component.translatable("quest.village-quest.friends.simon.herbs.title"),
                FriendsQuestService.shortObjective(world.getServer().getPlayerList().getPlayer(player),friends,"simon")),
                java.util.List.of(net.minecraft.network.chat.Component.translatable("quest.village-quest.friends.simon.herbs.objective")),-1));
        return java.util.List.copyOf(entries);
    }
    private static void add(java.util.List<de.quest.quest.JournalActiveQuest> entries,String id,SpecialQuestStatus status) {
        if(status!=null) {
            String prefix=switch(id) {
                case "relic" -> "shards";case "merchant_seal" -> "merchant";case "shepherd_flute" -> "flute";
                default -> id;
            };
            var details=new java.util.ArrayList<net.minecraft.network.chat.Component>(status.lines());
            details.add(net.minecraft.network.chat.Component.translatable("quest.village-quest.special."+prefix+".offer.1"));
            details.add(net.minecraft.network.chat.Component.translatable("quest.village-quest.special."+prefix+".offer.2",10));
            if(id.equals("surveyor_compass")) {
                details.add(net.minecraft.network.chat.Component.translatable("quest.village-quest.special.surveyor_compass_biomes.offer.1"));
                details.add(net.minecraft.network.chat.Component.translatable("quest.village-quest.special.surveyor_compass_biomes.offer.2"));
            }
            entries.add(new de.quest.quest.JournalActiveQuest(new de.quest.quest.TrackerEntry("special:"+id,"special",status.title(),
                    de.quest.quest.QuestShortObjective.current(status.lines())),details,-1));
        }
    }

    private static PlayerQuestData data(ServerLevel world, UUID playerId) {
        return QuestState.get(world.getServer()).getPlayerData(playerId);
    }

    public static boolean hasPendingOffer(ServerLevel world, UUID playerId) {
        return world != null
                && playerId != null
                && data(world, playerId).getPendingSpecialOfferKind() != null;
    }

    public static boolean handleQuestMasterInteraction(ServerLevel world, ServerPlayer player, boolean skipOffers) {
        return ShardRelicQuestService.handleQuestMasterInteraction(world, player, skipOffers)
                || MerchantSealQuestService.handleQuestMasterInteraction(world, player, skipOffers)
                || ShepherdFluteQuestService.handleQuestMasterInteraction(world, player, skipOffers)
                || ApiaristSmokerQuestService.handleQuestMasterInteraction(world, player, skipOffers)
                || SurveyorCompassQuestService.handleQuestMasterInteraction(world, player, skipOffers);
    }

    public static boolean acceptPendingOffer(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null) {
            return false;
        }
        UUID playerId = player.getUUID();
        if (ShardRelicQuestService.consumePendingSpecialOffer(world, playerId)) {
            return ShardRelicQuestService.acceptSpecialQuest(world, player);
        }
        if (data(world, playerId).getPendingSpecialOfferKind() == SpecialQuestKind.MERCHANT_SEAL) {
            return MerchantSealQuestService.acceptQuest(world, player);
        }
        if (data(world, playerId).getPendingSpecialOfferKind() == SpecialQuestKind.SHEPHERD_FLUTE) {
            return ShepherdFluteQuestService.acceptQuest(world, player);
        }
        if (data(world, playerId).getPendingSpecialOfferKind() == SpecialQuestKind.APIARIST_SMOKER) {
            return ApiaristSmokerQuestService.acceptQuest(world, player);
        }
        if (data(world, playerId).getPendingSpecialOfferKind() == SpecialQuestKind.SURVEYOR_COMPASS) {
            return SurveyorCompassQuestService.acceptQuest(world, player);
        }
        return false;
    }

    public static int discoveredCount(ServerLevel world, UUID playerId) {
        int count = 0;
        if (ShardRelicQuestService.isDiscovered(world, playerId)) {
            count++;
        }
        if (MerchantSealQuestService.isDiscovered(world, playerId)) {
            count++;
        }
        if (ShepherdFluteQuestService.isDiscovered(world, playerId)) {
            count++;
        }
        if (ApiaristSmokerQuestService.isDiscovered(world, playerId)) {
            count++;
        }
        if (SurveyorCompassQuestService.isDiscovered(world, playerId)) {
            count++;
        }
        return count + FriendsQuestService.discovered(FriendsQuestService.progress(world, playerId));
    }

    public static int completedCount(ServerLevel world, UUID playerId) {
        int count = 0;
        if (ShardRelicQuestService.isCompleted(world, playerId)) {
            count++;
        }
        if (MerchantSealQuestService.isCompleted(world, playerId)) {
            count++;
        }
        if (ShepherdFluteQuestService.isCompleted(world, playerId)) {
            count++;
        }
        if (ApiaristSmokerQuestService.isCompleted(world, playerId)) {
            count++;
        }
        if (SurveyorCompassQuestService.isCompleted(world, playerId)) {
            count++;
        }
        return count + FriendsQuestService.completed(FriendsQuestService.progress(world, playerId));
    }

    public static int activeCount(ServerLevel world, UUID playerId) {
        int count = 0;
        if (ShardRelicQuestService.isActive(world, playerId)) {
            count++;
        }
        if (MerchantSealQuestService.isActive(world, playerId)) {
            count++;
        }
        if (ShepherdFluteQuestService.isActive(world, playerId)) {
            count++;
        }
        if (ApiaristSmokerQuestService.isActive(world, playerId)) {
            count++;
        }
        if (SurveyorCompassQuestService.isActive(world, playerId)) {
            count++;
        }
        return count + FriendsQuestService.active(FriendsQuestService.progress(world, playerId));
    }

    public static SpecialQuestStatus openStatus(ServerLevel world, UUID playerId) {
        SpecialQuestStatus status = ShardRelicQuestService.openStatus(world, playerId);
        if (status != null) {
            return status;
        }
        status = MerchantSealQuestService.openStatus(world, playerId);
        if (status != null) {
            return status;
        }
        status = ShepherdFluteQuestService.openStatus(world, playerId);
        if (status != null) {
            return status;
        }
        status = ApiaristSmokerQuestService.openStatus(world, playerId);
        if (status != null) {
            return status;
        }
        status = SurveyorCompassQuestService.openStatus(world, playerId);
        return status != null ? status : FriendsQuestService.openStatus(world, playerId);
    }

    public static void onServerTick(MinecraftServer server) {
        FriendsQuestService.onServerTick(server);
        ShardRelicQuestService.onServerTick(server);
        ShepherdFluteQuestService.onServerTick(server);
        ApiaristSmokerQuestService.onServerTick(server);
        SurveyorCompassQuestService.onServerTick(server);
    }

    public static InteractionResult onUseBlock(ServerLevel world, ServerPlayer player, BlockPos pos) {
        return ShardRelicQuestService.onUseBlock(world, player, pos);
    }

    public static boolean allowBlockBreak(ServerLevel world, ServerPlayer player, BlockPos pos) {
        return ShardRelicQuestService.allowBlockBreak(world, player, pos);
    }

    public static void onBlockBreak(ServerLevel world, ServerPlayer player, BlockPos pos, BlockState state) {
        ShardRelicQuestService.onBlockBreak(world, player, pos, state);
        SurveyorCompassQuestService.onBlockBreak(world, player, pos, state);
    }

    public static void onTrackedItemPickup(ServerLevel world, ServerPlayer player, ItemStack stack, int count) {
        ShardRelicQuestService.onTrackedItemPickup(world, player, stack, count);
        ShepherdFluteQuestService.onTrackedItemPickup(world, player, stack, count);
        SurveyorCompassQuestService.onTrackedItemPickup(world, player, stack, count);
    }

    public static InteractionResult onUseEntity(ServerLevel world, ServerPlayer player, InteractionHand hand, Entity entity, ItemStack stack) {
        if (entity instanceof PilgrimEntity pilgrim) {
            InteractionResult sealResult = MerchantSealQuestService.tryUseOnPilgrim(world, player, pilgrim, stack);
            if (sealResult != InteractionResult.PASS) {
                return sealResult;
            }
        }
        if (entity instanceof WanderingTrader trader) {
            InteractionResult sealResult = MerchantSealQuestService.tryUseOnWanderingTrader(world, player, trader, stack);
            if (sealResult != InteractionResult.PASS) {
                return sealResult;
            }
        }
        return ShepherdFluteQuestService.onEntityUse(world, player, entity, stack);
    }

    public static void onVillagerTrade(ServerLevel world, ServerPlayer player, ItemStack stack) {
        MerchantSealQuestService.onVillagerTrade(world, player, stack);
    }

    public static void onAnimalLove(ServerLevel world, ServerPlayer player, Animal animal) {
        ShepherdFluteQuestService.onAnimalLove(world, player, animal);
        ApiaristSmokerQuestService.onAnimalLove(world, player, animal);
    }

    public static void onAnimalTamed(ServerLevel world, ServerPlayer player, TamableAnimal animal) {
        ShepherdFluteQuestService.onAnimalTamed(world, player, animal);
    }

    public static void onBeeNestInteract(ServerLevel world, ServerPlayer player, BlockState state, ItemStack stack) {
        ApiaristSmokerQuestService.onBeeNestInteract(world, player, state, stack);
    }

    public static void onPilgrimPurchase(ServerLevel world, ServerPlayer player, String offerId) {
        MerchantSealQuestService.onPilgrimPurchase(world, player, offerId);
    }

}
