package de.quest.shrine;

import de.quest.data.QuestState;
import de.quest.mixin.MarketPlayerSaveInvoker;
import de.quest.mixin.MarketSavedDataFileInvoker;
import java.io.IOException;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueOutput;

/** Own intro document proof; uses generic native file accessors, never the market ledger/wallet. */
final class VillageIntroSaveBarrier {
    static final String PLAYER_PROOF = "village-quest:intro_delivery";
    static final String QUEST_JOURNAL = "villageIntroTransactions";
    private VillageIntroSaveBarrier() {}
    static boolean persist(ServerPlayer player, QuestState quests) {
        var server = player.level().getServer();
        if (!server.isSameThread() || server.isStopped() || server.isShutdown()) return false;
        try {
            var problems = new ProblemReporter.Collector(); var output = TagValueOutput.createWithContext(problems, player.registryAccess());
            player.saveWithoutId(output); if (!problems.isEmpty()) return false;
            var expectedPlayer = output.buildResult();
            var expectedQuest = (CompoundTag) QuestState.TYPE.codec().encodeStart(RegistryOps.create(NbtOps.INSTANCE, player.registryAccess()), quests).getOrThrow();
            var storage = server.overworld().getDataStorage(); quests.setDirty(); storage.saveAndJoin();
            var files = (MarketSavedDataFileInvoker) storage;
            var savedQuest = storage.readTagFromDisk(files.villageQuest$marketDataFile(QuestState.TYPE.id()), QuestState.TYPE.dataFixType(),
                    SharedConstants.getCurrentVersion().dataVersion().version()).getCompoundOrEmpty("data");
            var players = (MarketPlayerSaveInvoker) server.getPlayerList(); players.villageQuest$saveMarketPlayer(player);
            var savedPlayer = players.villageQuest$marketPlayerStorage().load(player.nameAndId()).orElse(null);
            boolean verified = matches(player.getUUID(), expectedQuest, expectedPlayer, savedQuest, savedPlayer, player.registryAccess());
            if (!verified) quests.setDirty();
            return verified;
        } catch (IOException | RuntimeException uncertain) { quests.setDirty(); return false; }
    }
    static boolean matches(UUID actor, CompoundTag expectedQuest, CompoundTag expectedPlayer,
                           CompoundTag savedQuest, CompoundTag savedPlayer, HolderLookup.Provider registries) {
        if (actor == null || expectedQuest == null || expectedPlayer == null || savedQuest == null || savedPlayer == null
                || !expectedQuest.equals(savedQuest)) return false;
        var q = VillageIntroTransaction.fromNbt(expectedQuest.getCompoundOrEmpty(QUEST_JOURNAL).get(actor.toString()), registries);
        var proof = expectedPlayer.getCompoundOrEmpty(AttachmentTarget.NBT_ATTACHMENT_KEY).get(PLAYER_PROOF);
        var p = VillageIntroTransaction.fromNbt(proof, registries);
        return q.isPresent() && p.isPresent() && q.get().actor().equals(actor) && q.get().sameTransaction(p.get())
                && proof.equals(savedPlayer.getCompoundOrEmpty(AttachmentTarget.NBT_ATTACHMENT_KEY).get(PLAYER_PROOF))
                && expectedPlayer.get("Inventory") instanceof ListTag inventory && inventory.equals(savedPlayer.get("Inventory"));
    }
}
