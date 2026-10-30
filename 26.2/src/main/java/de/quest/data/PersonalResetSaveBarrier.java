package de.quest.data;

import de.quest.guild.VillageGuildState;
import de.quest.guildtown.GuildTownSharedState;
import de.quest.kingdom.LivingKingdomsState;
import de.quest.mixin.MarketPlayerSaveInvoker;
import de.quest.mixin.MarketSavedDataFileInvoker;
import de.quest.village.LivingVillageNetworkState;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
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
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.TagValueOutput;

/** Actual native file saves followed by independent readback of every reset participant. */
final class PersonalResetSaveBarrier {
    static final String PLAYER_PROOF="village-quest:personal_reset";
    private PersonalResetSaveBarrier() {}
    static boolean persist(ServerPlayer player,boolean requireProof) {
        var server=player.level().getServer();
        if(!server.isSameThread() || server.isStopped() || server.isShutdown())return false;
        var documents=new LinkedHashMap<SavedDataType<?>,SavedData>();
        documents.put(QuestState.TYPE,QuestState.get(server));
        documents.put(VillageGuildState.TYPE,VillageGuildState.get(server));
        documents.put(GuildTownSharedState.TYPE,GuildTownSharedState.get(server));
        documents.put(LivingVillageNetworkState.TYPE,LivingVillageNetworkState.get(server));
        documents.put(LivingKingdomsState.TYPE,LivingKingdomsState.get(server));
        try {
            if(!PersonalResetItems.nativeStorageOnly(player))return false;
            var ops=RegistryOps.create(NbtOps.INSTANCE,player.registryAccess());
            var expected=new LinkedHashMap<SavedDataType<?>,CompoundTag>();
            for(var entry:documents.entrySet())expected.put(entry.getKey(),encode(entry.getKey(),entry.getValue(),ops));
            var problems=new ProblemReporter.Collector();var output=TagValueOutput.createWithContext(problems,player.registryAccess());
            player.saveWithoutId(output);if(!problems.isEmpty())return false;
            var expectedPlayer=output.buildResult();
            var storage=server.overworld().getDataStorage();documents.values().forEach(SavedData::setDirty);storage.saveAndJoin();
            var files=(MarketSavedDataFileInvoker)storage;var actual=new LinkedHashMap<SavedDataType<?>,CompoundTag>();
            for(var type:documents.keySet())actual.put(type,storage.readTagFromDisk(files.villageQuest$marketDataFile(type.id()),type.dataFixType(),
                    SharedConstants.getCurrentVersion().dataVersion().version()).getCompoundOrEmpty("data"));
            var players=(MarketPlayerSaveInvoker)server.getPlayerList();players.villageQuest$saveMarketPlayer(player);
            var savedPlayer=players.villageQuest$marketPlayerStorage().load(player.nameAndId()).orElse(null);
            boolean verified=matches(player.getUUID(),expected,actual,expectedPlayer,savedPlayer,player.registryAccess(),requireProof);
            if(!verified)documents.values().forEach(SavedData::setDirty);
            return verified;
        } catch(IOException | RuntimeException uncertain){documents.values().forEach(SavedData::setDirty);return false;}
    }
    @SuppressWarnings("unchecked")
    private static <T extends SavedData> CompoundTag encode(SavedDataType<T> type,SavedData value,RegistryOps<net.minecraft.nbt.Tag> ops) {
        return (CompoundTag)type.codec().encodeStart(ops,(T)value).getOrThrow();
    }
    static boolean matches(UUID actor,Map<SavedDataType<?>,CompoundTag> expected,Map<SavedDataType<?>,CompoundTag> actual,
            CompoundTag player,CompoundTag savedPlayer,HolderLookup.Provider registries,boolean requireProof) {
        if(actor==null || expected==null || actual==null || expected.size()!=5 || !expected.keySet().equals(java.util.Set.of(
                QuestState.TYPE,VillageGuildState.TYPE,GuildTownSharedState.TYPE,LivingVillageNetworkState.TYPE,LivingKingdomsState.TYPE))
                || !expected.equals(actual) || player==null || savedPlayer==null)return false;
        for(String key:java.util.List.of("Inventory","EnderItems"))
            if(!(player.get(key) instanceof ListTag) || !player.get(key).equals(savedPlayer.get(key)))return false;
        var attachments=player.get(AttachmentTarget.NBT_ATTACHMENT_KEY);
        if(!java.util.Objects.equals(attachments,savedPlayer.get(AttachmentTarget.NBT_ATTACHMENT_KEY)))return false;
        if(!requireProof)return true;
        var q=PersonalResetTransaction.fromNbt(expected.get(QuestState.TYPE).getCompoundOrEmpty("personalResetTransactions").get(actor.toString()),registries).orElse(null);
        var p=PersonalResetTransaction.fromNbt(player.getCompoundOrEmpty(AttachmentTarget.NBT_ATTACHMENT_KEY).get(PLAYER_PROOF),registries).orElse(null);
        return q!=null && q.actor().equals(actor) && q.sameTransaction(p);
    }
}
