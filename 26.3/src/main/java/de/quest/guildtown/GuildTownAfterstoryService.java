package de.quest.guildtown;

import de.quest.data.PlayerQuestData;
import de.quest.data.QuestState;
import de.quest.economy.CurrencyService;
import de.quest.shrine.VillageBondService;
import de.quest.shrine.VillageBondType;
import de.quest.shrine.VillageRequestType;
import de.quest.village.VillageCondition;
import de.quest.village.VillageLifeState;
import de.quest.village.VillageNeed;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Identity-specific follow-ups after the original local story, shown on its Notice Post. */
public final class GuildTownAfterstoryService {
    private static final int REWARD_SILVERMARKS = 8;

    private GuildTownAfterstoryService() {}

    public record View(GuildTownAfterstoryLedger.State state, Component title, Component detail,
                       ItemStack delivery, int amount, int inventory) {
        public boolean ready() { return state == GuildTownAfterstoryLedger.State.ACTIVE && inventory >= amount; }
    }

    public static View view(ServerLevel world, ServerPlayer player,
                            GuildTownService.NoticePostStoryResolution resolution,
                            VillageBondService.VillageBondView connected) {
        if (world == null || player == null || resolution == null
                || resolution.village() == null) return null;
        PlayerQuestData data = QuestState.get(world.getServer()).getPlayerData(player.getUUID());
        GuildTownStoryVillageResolver.StoryVillage village = resolution.village();
        GuildTownStory story = GuildTownStory.forVillage(village.type());
        if (!GuildTownProgress.storyCompletedAt(data, story, village.index())) return null;
        GuildTownAfterstoryLedger.State state = GuildTownAfterstoryLedger.state(data, village.index());
        String type = village.type().key();
        Component title = Component.translatable("quest.village-quest.guild_town.afterstory." + type + ".title");
        if (state == GuildTownAfterstoryLedger.State.COMPLETE) {
            return new View(state, title,
                    Component.translatable("quest.village-quest.guild_town.afterstory." + type + ".complete"),
                    ItemStack.EMPTY, 0, 0);
        }
        if (state == GuildTownAfterstoryLedger.State.NOT_STARTED) {
            VillageRequestType request = offer(village.type(), currentNeed(village, connected),
                    village.condition(), village.index());
            return new View(state, title,
                    Component.translatable("quest.village-quest.guild_town.afterstory." + type + ".offer",
                            new ItemStack(request.item()).getHoverName()),
                    new ItemStack(request.item()), amount(request), 0);
        }
        VillageRequestType request = GuildTownAfterstoryLedger.request(data, village.index());
        if (request == null || request.bondType() != village.type()) return null;
        int amount = amount(request);
        int carried = GuildTownService.count(player, request.item());
        return new View(state, title,
                Component.translatable("quest.village-quest.guild_town.afterstory.active",
                        amount, new ItemStack(request.item()).getHoverName()),
                new ItemStack(request.item()), amount, carried);
    }

    public static boolean handle(ServerLevel world, ServerPlayer player, boolean deliver) {
        if (world == null || player == null || world != world.getServer().overworld()) return false;
        PlayerQuestData data = QuestState.get(world.getServer()).getPlayerData(player.getUUID());
        GuildTownStoryVillageResolver.StoryVillage village =
                GuildTownStoryVillageResolver.current(world, player, data);
        if (village == null || !GuildTownProgress.storyCompletedAt(data,
                GuildTownStory.forVillage(village.type()), village.index())
                || VillageLifeState.get(world.getServer()).status(new VillageLifeState.VillageKey(
                world.dimension().identifier().toString(), village.x(), village.z()))
                != VillageLifeState.Status.ACTIVE) return false;
        GuildTownAfterstoryLedger.State state = GuildTownAfterstoryLedger.state(data, village.index());
        if (!deliver && state == GuildTownAfterstoryLedger.State.NOT_STARTED) {
            VillageBondService.VillageBondView connected = VillageBondService.inspectCurrentVillage(world, player, false);
            VillageRequestType request = offer(village.type(), currentNeed(village, connected),
                    village.condition(), village.index());
            if (!GuildTownAfterstoryLedger.begin(data, village.index(), request)) return false;
            QuestState.get(world.getServer()).setDirty();
            player.sendSystemMessage(Component.translatable("message.village-quest.guild_town.afterstory.accepted")
                    .withStyle(ChatFormatting.AQUA), false);
            return true;
        }
        if (!deliver || state != GuildTownAfterstoryLedger.State.ACTIVE) return false;
        VillageRequestType request = GuildTownAfterstoryLedger.request(data, village.index());
        if (request == null || request.bondType() != village.type()) return false;
        if (!GuildTownService.consumeAtomic(player, request.item(), amount(request))) {
            player.sendSystemMessage(Component.translatable("message.village-quest.guild_town.afterstory.missing",
                    amount(request), new ItemStack(request.item()).getHoverName())
                    .withStyle(ChatFormatting.YELLOW), false);
            return false;
        }
        if (!GuildTownAfterstoryLedger.complete(data, village.index())) return false;
        var socialVillage = new VillageLifeState.VillageKey(world.dimension().identifier().toString(), village.x(), village.z());
        de.quest.reputation.SocialReputationService.recordNamedBenefit(world.getServer(), player.getUUID(),
                de.quest.reputation.SocialReputationRules.BenefitKind.AFTERSTORY,
                socialVillage + ":" + village.type().key(), List.of(socialVillage));
        GuildTownProgress.addPersonalChronicle(data,
                new GuildTownProgress.VillageIdentity(player.getUUID(), village.x(), village.z()),
                "afterstory_" + village.type().key(), false, world.getGameTime());
        QuestState.get(world.getServer()).setDirty();
        CurrencyService.addBalance(world, player.getUUID(), REWARD_SILVERMARKS);
        player.sendSystemMessage(Component.translatable("message.village-quest.guild_town.afterstory.completed",
                REWARD_SILVERMARKS).withStyle(ChatFormatting.GREEN), false);
        return true;
    }

    static int amount(VillageRequestType request) {
        return Math.max(4, Math.min(24, (request.amount() + 7) / 8));
    }

    static VillageRequestType offer(VillageBondType type, VillageNeed need,
                                    VillageCondition condition, int villageIndex) {
        List<VillageRequestType> matching = new ArrayList<>();
        for (VillageRequestType request : VillageRequestType.values()) {
            if (request.bondType() == type && need.matches(request)) matching.add(request);
        }
        if (matching.isEmpty()) return VillageRequestType.forVillage(type, villageIndex);
        int choice = condition == VillageCondition.CRISIS || condition == VillageCondition.STRAINED
                ? 0 : Math.floorMod(villageIndex + 1, matching.size());
        return matching.get(choice);
    }

    private static VillageNeed currentNeed(GuildTownStoryVillageResolver.StoryVillage village,
                                           VillageBondService.VillageBondView connected) {
        return connected != null && connected.index() == village.index()
                ? connected.network().need() : VillageNeed.forVillage(village.type(), village.index());
    }
}
