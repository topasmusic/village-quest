package de.quest.shrine;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

final class VillageIntroSavedCheckpointTest {
    @org.junit.jupiter.api.BeforeAll static void bootstrap() { VillageIntroTransactionTest.bootstrap(); }
    @org.junit.jupiter.api.BeforeEach void components() { new VillageIntroTransactionTest().components(); }
    private VillageIntroTransaction transaction(int mask) { return new VillageIntroTransactionTest().transaction(mask); }
    private static net.minecraft.core.HolderLookup.Provider registries() { return VillageIntroTransactionTest.registries(); }
    @Test void successRequiresExactActualQuestAndPlayerImagesIncludingProsperity() {
        var tx = transaction(0); var quest = quest(tx); var player = player(tx);
        assertTrue(VillageIntroSaveBarrier.matches(tx.actor(), quest, player, quest.copy(), player.copy(), registries()));
        var changed = quest.copy(); changed.putInt("prosperitySentinel", 9);
        assertFalse(VillageIntroSaveBarrier.matches(tx.actor(), quest, player, changed, player.copy(), registries()));
        var changedPlayer = player.copy(); changedPlayer.getListOrEmpty("Inventory").add(new CompoundTag());
        assertFalse(VillageIntroSaveBarrier.matches(tx.actor(), quest, player, quest.copy(), changedPlayer, registries()));
        assertFalse(VillageIntroSaveBarrier.matches(UUID.randomUUID(), quest, player, quest.copy(), player.copy(), registries()));
        assertFalse(VillageIntroSaveBarrier.matches(tx.actor(), quest, player, null, player.copy(), registries()));
    }
    @Test void matchingUnknownOrForeignProofsDoNotProveAValidDelivery() {
        var tx = transaction(0); var quest = quest(tx); var player = player(tx);
        player.getCompoundOrEmpty(AttachmentTarget.NBT_ATTACHMENT_KEY).getCompoundOrEmpty(VillageIntroSaveBarrier.PLAYER_PROOF).putInt("schema", 99);
        assertFalse(VillageIntroSaveBarrier.matches(tx.actor(), quest, player, quest.copy(), player.copy(), registries()));
    }
    private CompoundTag quest(VillageIntroTransaction tx) {
        var root = new CompoundTag(); var entries = new CompoundTag(); entries.put(tx.actor().toString(), tx.toNbt());
        root.put(VillageIntroSaveBarrier.QUEST_JOURNAL, entries); root.putInt("prosperitySentinel", 8); return root;
    }
    private CompoundTag player(VillageIntroTransaction tx) {
        var root = new CompoundTag(); var entries = new CompoundTag(); entries.put(VillageIntroSaveBarrier.PLAYER_PROOF, tx.toNbt());
        root.put(AttachmentTarget.NBT_ATTACHMENT_KEY, entries); root.put("Inventory", new ListTag()); return root;
    }
}
