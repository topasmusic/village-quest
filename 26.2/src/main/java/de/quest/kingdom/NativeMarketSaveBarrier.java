package de.quest.kingdom;

import de.quest.data.QuestState;
import de.quest.mixin.MarketPlayerSaveInvoker;
import de.quest.mixin.MarketSavedDataFileInvoker;
import java.io.IOException;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueOutput;

/** Save and read back the actual documents, never infer success from void/Future completion. */
final class NativeMarketSaveBarrier {
    private NativeMarketSaveBarrier() {}
    static boolean persist(ServerPlayer player,LivingKingdomsState kingdoms,QuestState quests) {
        var server=player.level().getServer();
        if(!server.isSameThread() || server.isStopped() || server.isShutdown()) return false;
        try {
            var problems=new ProblemReporter.Collector(); var output=TagValueOutput.createWithContext(problems,player.registryAccess());
            player.saveWithoutId(output); if(!problems.isEmpty()) return false;
            var expectedPlayer=output.buildResult(); var expectedMarket=LivingKingdomsState.toNbt(kingdoms);
            var ops=RegistryOps.create(NbtOps.INSTANCE,player.registryAccess());
            var expectedWallet=(CompoundTag)QuestState.TYPE.codec().encodeStart(ops,quests).getOrThrow();
            var storage=server.overworld().getDataStorage(); kingdoms.setDirty(); quests.setDirty(); storage.saveAndJoin();
            var files=(MarketSavedDataFileInvoker)storage;
            int version=SharedConstants.getCurrentVersion().dataVersion().version();
            var savedMarket=storage.readTagFromDisk(files.villageQuest$marketDataFile(LivingKingdomsState.TYPE.id()),LivingKingdomsState.TYPE.dataFixType(),version).getCompoundOrEmpty("data");
            var savedWallet=storage.readTagFromDisk(files.villageQuest$marketDataFile(QuestState.TYPE.id()),QuestState.TYPE.dataFixType(),version).getCompoundOrEmpty("data");
            var playerStorage=(MarketPlayerSaveInvoker)server.getPlayerList();
            playerStorage.villageQuest$saveMarketPlayer(player);
            // PlayerList.loadPlayerData may redirect an integrated owner to an older world UUID. Verify the file we wrote.
            var savedPlayer=playerStorage.villageQuest$marketPlayerStorage().load(player.nameAndId()).orElse(null);
            boolean verified=MarketSavedCheckpoint.matches(player.getUUID(),expectedMarket,expectedWallet,expectedPlayer,savedMarket,savedWallet,savedPlayer);
            if(!verified) { kingdoms.setDirty(); quests.setDirty(); }
            return verified;
        } catch(IOException | RuntimeException failure) {
            // Keep documents dirty for a later normal save; do not clear receipts or repay uncertain state.
            kingdoms.setDirty(); quests.setDirty(); return false;
        }
    }
}
