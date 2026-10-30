package de.quest.shrine;

import static org.junit.jupiter.api.Assertions.*;
import de.quest.data.QuestState;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.*;

/** Native data/codec tests only: no server, world, GameTest or ingame simulation. */
final class VillageIntroJournalPortTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path temporary;
    @BeforeAll static void bootstrap() { VillageIntroTransactionTest.bootstrap(); }
    @BeforeEach void components() { new VillageIntroTransactionTest().components(); }
    private static final class Documents {
        final UUID actor = UUID.randomUUID(), village = UUID.randomUUID();
        QuestState quests = decode(new CompoundTag());
        SimpleContainer bag = new SimpleContainer(36);
        Tag proof;
        CompoundTag diskQ;
        Tag diskP;
        SimpleContainer diskBag;
        VillageIntroTransaction tx;
        int writes, failAt;
        boolean throwsAtFailure;
        java.nio.file.Path directory;
        Documents() { this(null); }
        Documents(java.nio.file.Path directory) {
            this.directory = directory;
            var data = quests.getPlayerData(actor);
            data.setTradeRouteInt("bond_village_count", 1);
            data.setTradeRouteString(VillageBondService.villageKey(0, "canonical_id"), village.toString());
            data.setTradeRouteInt(VillageBondService.villageKey(0, "request_day"), 77);
            data.setCurrencyBalance(10); data.setPilgrimInt("prosperity.stat.earned", 7);
            var progress = VillageIntroRequestProgress.open(data, village, 0, false);
            assertTrue(progress.accept(VillageIntroRequestType.PATH_STONE, 1, 0, false));
            bag.setItem(0, new ItemStack(Items.COBBLESTONE, 16));
            tx = VillageIntroTransaction.prepare(UUID.randomUUID(), actor, 1, progress.episode(), village,
                    VillageIntroInventoryPlan.plan(bag, VillageIntroRequestType.PATH_STONE).orElseThrow(),
                    0, 10, 7, 0, 0, VillageIntroTransactionTest.registries()).orElseThrow();
            diskQ = encode(quests); diskBag = copy(bag);
            if (directory != null) { write("q.nbt", diskQ); write("player.nbt", playerImage()); }
        }
        VillageIntroDeliveryCoordinator coordinator() {
            return new VillageIntroDeliveryCoordinator(new VillageIntroJournalPort(quests, actor, 0, bag,
                    VillageIntroTransactionTest.registries(), new VillageIntroJournalPort.PlayerProof() {
                        public Tag get() { return proof == null ? null : proof.copy(); }
                        public void set(CompoundTag tag) { proof = tag.copy(); }
                    }, this::save, tx));
        }
        boolean writeAllowed() {
            if (++writes != failAt) return true;
            if (throwsAtFailure) throw new IllegalStateException("Injected document write interruption");
            return false;
        }
        boolean save() {
            if (!writeAllowed()) return false;
            diskQ = encode(quests);
            if (directory != null) write("q.nbt", diskQ);
            if (!writeAllowed()) return false;
            diskP = proof == null ? null : proof.copy(); diskBag = copy(bag);
            if (directory != null) write("player.nbt", playerImage());
            return true;
        }
        CompoundTag playerImage() {
            var root = new CompoundTag(); if (diskP != null) root.put("proof", diskP.copy());
            var slots = new net.minecraft.nbt.ListTag();
            var ops = RegistryOps.create(NbtOps.INSTANCE, VillageIntroTransactionTest.registries());
            for (int i = 0; i < 36; i++) slots.add(ItemStack.OPTIONAL_CODEC.encodeStart(ops, diskBag.getItem(i)).getOrThrow());
            root.put("inventory", slots); return root;
        }
        void write(String name, CompoundTag image) {
            try {
                java.nio.file.Files.createDirectories(directory);
                try (var output = new java.io.DataOutputStream(java.nio.file.Files.newOutputStream(directory.resolve(name)))) {
                    net.minecraft.nbt.NbtIo.write(image, output);
                }
            } catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
        }
        CompoundTag read(String name) {
            try (var input = new java.io.DataInputStream(java.nio.file.Files.newInputStream(directory.resolve(name)))) {
                return net.minecraft.nbt.NbtIo.read(input, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
            } catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
        }
        void reload() {
            if (directory != null) {
                diskQ = read("q.nbt"); var root = read("player.nbt"); diskP = root.get("proof");
                var ops = RegistryOps.create(NbtOps.INSTANCE, VillageIntroTransactionTest.registries());
                diskBag = new SimpleContainer(36); var slots = root.getListOrEmpty("inventory");
                for (int i = 0; i < 36; i++) diskBag.setItem(i, ItemStack.OPTIONAL_CODEC.parse(ops, slots.get(i)).getOrThrow());
            }
            quests = decode(diskQ.copy()); proof = diskP == null ? null : diskP.copy(); bag = copy(diskBag); failAt = 0;
        }
        void assertCommitted() {
            var data = quests.getPlayerData(actor);
            assertEquals(15, data.getCurrencyBalance()); assertEquals(12, data.getPilgrimInt("prosperity.stat.earned"));
            assertEquals(1, data.getTradeRouteInt(VillageBondService.villageKey(0, "completions")));
            assertEquals(77, data.getTradeRouteInt(VillageBondService.villageKey(0, "request_day")));
            assertTrue(bag.getItem(0).isEmpty());
        }
    }
    private static CompoundTag encode(QuestState state) {
        return (CompoundTag) QuestState.TYPE.codec().encodeStart(RegistryOps.create(NbtOps.INSTANCE, VillageIntroTransactionTest.registries()), state).getOrThrow();
    }
    private static QuestState decode(CompoundTag tag) {
        return QuestState.TYPE.codec().parse(RegistryOps.create(NbtOps.INSTANCE, VillageIntroTransactionTest.registries()), tag).getOrThrow();
    }
    private static SimpleContainer copy(SimpleContainer source) {
        var result = new SimpleContainer(36);
        var ops = RegistryOps.create(NbtOps.INSTANCE, VillageIntroTransactionTest.registries());
        for (int i = 0; i < 36; i++) result.setItem(i, ItemStack.OPTIONAL_CODEC.parse(ops,
                ItemStack.OPTIONAL_CODEC.encodeStart(ops, source.getItem(i)).getOrThrow()).getOrThrow());
        return result;
    }
    @Test void realQuestStatePostimagesCommitTogetherAndReloadDoesNotPayAgain() {
        var docs = new Documents();
        assertEquals(VillageIntroDeliveryCoordinator.Result.COMMITTED, docs.coordinator().execute(docs.tx.receipt()));
        docs.assertCommitted(); docs.reload();
        assertEquals(VillageIntroDeliveryCoordinator.Result.COMMITTED, docs.coordinator().recover());
        docs.assertCommitted();
    }
    @Test void independentDocumentWritesAndExceptionsRecoverFromActualPersistedImages() {
        for (boolean throwing : new boolean[]{false, true}) for (int cut = 1; cut <= 8; cut++) {
            var docs = new Documents(temporary.resolve("case-" + throwing + "-" + cut)); docs.failAt = cut; docs.throwsAtFailure = throwing;
            assertEquals(VillageIntroDeliveryCoordinator.Result.HELD, docs.coordinator().execute(docs.tx.receipt()), "cut=" + cut);
            docs.reload();
            var result = docs.coordinator().recover();
            if (cut == 1 || cut == 2) {
                assertEquals(VillageIntroDeliveryCoordinator.Result.HELD, result);
                assertEquals(10, docs.quests.getPlayerData(docs.actor).getCurrencyBalance());
                assertEquals(16, docs.bag.getItem(0).getCount());
                if (cut == 2) assertNotNull(docs.quests.introTransaction(docs.actor));
            } else if (cut <= 4) {
                assertEquals(VillageIntroDeliveryCoordinator.Result.CANCELLED, result);
                assertEquals(10, docs.quests.getPlayerData(docs.actor).getCurrencyBalance());
                assertEquals(16, docs.bag.getItem(0).getCount());
            } else {
                assertEquals(VillageIntroDeliveryCoordinator.Result.COMMITTED, result, "cut=" + cut);
                docs.assertCommitted(); docs.reload();
                assertEquals(VillageIntroDeliveryCoordinator.Result.COMMITTED, docs.coordinator().recover()); docs.assertCommitted();
            }
        }
    }
    @Test void unknownJournalContainerAndOwnerRecordsSurviveWithoutBeingOverwritten() {
        for (Tag raw : java.util.List.of(net.minecraft.nbt.StringTag.valueOf("future"), new CompoundTag())) {
            var root = new CompoundTag();
            if (raw instanceof CompoundTag owners) { var future = new CompoundTag(); future.putInt("schema", 90); owners.put("invalid-owner", future); }
            root.put("villageIntroTransactions", raw);
            var state = decode(root); var actor = UUID.randomUUID();
            assertEquals(raw, encode(state).get("villageIntroTransactions"));
            if (!(raw instanceof CompoundTag)) assertFalse(state.compareIntroTransaction(actor, null, new CompoundTag()));
            var roundTrip = decode(encode(state)); assertEquals(raw, encode(roundTrip).get("villageIntroTransactions"));
        }
    }
    @Test void questEffectsWithOriginalPlayerBeforeFinishOnlyTheStoredConsumption() {
        var docs = new Documents();
        docs.proof = docs.tx.toNbt();
        assertTrue(docs.quests.compareIntroTransaction(docs.actor, null, docs.tx.toNbt()));
        assertTrue(docs.save()); var originalPlayer = docs.diskP.copy(); var originalBag = copy(docs.diskBag);
        // Continue the same BEFORE claim in this data-service fixture, then independently reload the old Player image.
        var port = new VillageIntroJournalPort(docs.quests, docs.actor, 0, docs.bag, VillageIntroTransactionTest.registries(),
                new VillageIntroJournalPort.PlayerProof() {
                    public Tag get() { return docs.proof; }
                    public void set(CompoundTag tag) { docs.proof = tag; }
                }, docs::save, docs.tx);
        assertTrue(port.applyInventory()); port.playerStage(VillageIntroDeliveryCoordinator.Stage.AFTER);
        assertTrue(port.applyEffects()); assertEquals(VillageIntroDeliveryCoordinator.Stage.EFFECTS_COMMITTED, port.questProof().stage());
        assertTrue(docs.save()); docs.diskP = originalPlayer; docs.diskBag = originalBag;
        docs.reload();
        assertEquals(VillageIntroDeliveryCoordinator.Result.COMMITTED, docs.coordinator().recover()); docs.assertCommitted();
    }
    @Test void aForeignProofOrChangedInventoryPreservesTheClaimWithoutGivingCredit() {
        for (boolean foreign : new boolean[]{false, true}) {
            var docs = new Documents(); docs.failAt = 5;
            assertEquals(VillageIntroDeliveryCoordinator.Result.HELD, docs.coordinator().execute(docs.tx.receipt()));
            docs.reload();
            if (foreign) { var raw = (CompoundTag) docs.proof; raw.putString("actor", UUID.randomUUID().toString()); }
            else docs.bag.setItem(0, new ItemStack(Items.COBBLESTONE, 3));
            var journal = docs.quests.introTransaction(docs.actor);
            assertEquals(VillageIntroDeliveryCoordinator.Result.HELD, docs.coordinator().recover());
            assertEquals(journal, docs.quests.introTransaction(docs.actor));
            assertEquals(10, docs.quests.getPlayerData(docs.actor).getCurrencyBalance());
        }
    }
    @Test void twoDifferentClaimsRequireConfirmedPriorAckAndShareThePaidBudget() {
        var docs = new Documents();
        assertEquals(VillageIntroDeliveryCoordinator.Result.COMMITTED, docs.coordinator().execute(docs.tx.receipt()));
        var first = docs.quests.introTransaction(docs.actor);
        var progress = VillageIntroRequestProgress.open(docs.quests.getPlayerData(docs.actor), docs.village, 1, false);
        assertTrue(progress.accept(VillageIntroRequestType.HARVEST_HELP, 1, 1, false));
        for (int i = 0; i < 8; i++) assertTrue(progress.harvest(UUID.randomUUID()));
        docs.tx = VillageIntroTransaction.prepare(UUID.randomUUID(), docs.actor, 1, progress.episode(), docs.village,
                VillageIntroInventoryPlan.plan(docs.bag, VillageIntroRequestType.HARVEST_HELP).orElseThrow(),
                1, 15, 12, VillageIntroRequestType.PATH_STONE.bit(), VillageIntroRequestType.PATH_STONE.bit(),
                VillageIntroTransactionTest.registries()).orElseThrow();
        docs.failAt = docs.writes + 1;
        assertEquals(VillageIntroDeliveryCoordinator.Result.HELD, docs.coordinator().execute(docs.tx.receipt()));
        assertEquals(first, docs.quests.introTransaction(docs.actor));
        assertEquals(15, docs.quests.getPlayerData(docs.actor).getCurrencyBalance());
        docs.failAt = 0;
        assertEquals(VillageIntroDeliveryCoordinator.Result.COMMITTED, docs.coordinator().execute(docs.tx.receipt()));
        docs.reload();
        var data = docs.quests.getPlayerData(docs.actor);
        assertEquals(17, data.getCurrencyBalance()); assertEquals(14, data.getPilgrimInt("prosperity.stat.earned"));
        assertEquals(2, data.getTradeRouteInt(VillageBondService.villageKey(0, "completions")));
        assertEquals(77, data.getTradeRouteInt(VillageBondService.villageKey(0, "request_day")));
        assertEquals(2, VillageIntroRequestProgress.open(data, docs.village, 2, true).paidCount());
        assertTrue(VillageIntroRequestProgress.open(data, docs.village, 2, true).offers(2, true).isEmpty());
    }
    @Test void acknowledgedTerminalRemainsTerminalAfterOrdinaryInventoryAndBalanceChanges() {
        var docs = new Documents();
        assertEquals(VillageIntroDeliveryCoordinator.Result.COMMITTED, docs.coordinator().execute(docs.tx.receipt()));
        docs.quests.getPlayerData(docs.actor).setCurrencyBalance(90);
        docs.bag.setItem(0, new ItemStack(Items.COBBLESTONE, 21));
        assertTrue(docs.save()); docs.reload();
        assertEquals(VillageIntroDeliveryCoordinator.Result.COMMITTED, docs.coordinator().recover());
        assertEquals(90, docs.quests.getPlayerData(docs.actor).getCurrencyBalance());
        assertEquals(21, docs.bag.getItem(0).getCount());
    }
    @Test void ownAttachmentCodecPreservesUnknownTypesAndCopiesBothDirections() {
        for (Tag raw : java.util.List.of(net.minecraft.nbt.StringTag.valueOf("future"), new CompoundTag())) {
            var encoded = VillageIntroRequestService.PROOF_CODEC.encodeStart(NbtOps.INSTANCE, raw).getOrThrow();
            var loaded = VillageIntroRequestService.PROOF_CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow();
            assertEquals(raw, loaded); if (raw instanceof CompoundTag) assertNotSame(raw, loaded);
        }
    }
}
