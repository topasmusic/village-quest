package de.quest.data;

import de.quest.content.story.SilentForgeStoryArc;
import de.quest.quest.story.StoryChapterDefinition;
import de.quest.quest.story.StoryQuestKeys;
import de.quest.quest.story.StoryQuestService;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.chat.Component;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Exercises the actual chapter with real stacks and state, replacing only server services. */
final class SilentForgeMasterworkTest {
    private final UUID playerId = UUID.randomUUID();
    private PlayerQuestData data = new PlayerQuestData();
    private final List<ItemStack> inventory = new ArrayList<>();
    private final Map<ResourceKey<Enchantment>, Holder.Reference<Enchantment>> enchantments = new HashMap<>();
    private final StoryChapterDefinition chapter = new SilentForgeStoryArc().chapter(3);
    private ServerLevel world;
    private ServerPlayer player;
    private MockedStatic<StoryQuestService> service;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        for (Item item : List.of(Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS,
                Items.IRON_BOOTS, Items.DIAMOND_BOOTS, Items.DIAMOND_SWORD, Items.ENCHANTED_BOOK)) {
            item.builtInRegistryHolder().bindComponents(DataComponentMap.builder()
                    .set(DataComponents.MAX_DAMAGE, 500).set(DataComponents.DAMAGE, 0).build());
        }
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        world = mock(ServerLevel.class);
        player = mock(ServerPlayer.class);
        when(player.getUUID()).thenReturn(playerId);
        RegistryAccess access = mock(RegistryAccess.class);
        Registry<Enchantment> registry = mock(Registry.class);
        when(world.registryAccess()).thenReturn(access);
        when(access.lookupOrThrow(Registries.ENCHANTMENT)).thenReturn(registry);
        when(registry.getOrThrow(org.mockito.ArgumentMatchers.<ResourceKey<Enchantment>>any())).thenAnswer(call -> enchantments.computeIfAbsent(call.getArgument(0), key -> mock(Holder.Reference.class)));
        Inventory playerInventory = mock(Inventory.class);
        when(player.getInventory()).thenReturn(playerInventory);
        when(playerInventory.getContainerSize()).thenAnswer(call -> inventory.size());
        when(playerInventory.getItem(anyInt())).thenAnswer(call -> inventory.get(call.getArgument(0)));
        service = mockStatic(StoryQuestService.class);
        service.when(() -> StoryQuestService.getQuestInt(eq(world), eq(playerId), anyString()))
                .thenAnswer(call -> data.getStoryInt(call.getArgument(2)));
        service.when(() -> StoryQuestService.setQuestInt(eq(world), eq(playerId), anyString(), anyInt()))
                .thenAnswer(call -> { data.setStoryInt(call.getArgument(2), call.getArgument(3)); return null; });
        service.when(() -> StoryQuestService.countMatchingCompletionItems(eq(world), eq(playerId), any()))
                .thenAnswer(call -> inventory.stream().filter(call.<Predicate<ItemStack>>getArgument(2)).mapToInt(ItemStack::getCount).sum());
        service.when(() -> StoryQuestService.consumeMatchingCompletionItems(eq(world), eq(playerId), any(), anyInt()))
                .thenAnswer(call -> consume(call.getArgument(2), call.getArgument(3)));
    }

    @AfterEach
    void tearDown() {
        if (service != null) service.close();
    }

    private boolean consume(Predicate<ItemStack> predicate, int count) {
        if (inventory.stream().filter(predicate).mapToInt(ItemStack::getCount).sum() < count) return false;
        for (ItemStack stack : inventory) {
            if (!predicate.test(stack)) continue;
            int taken = Math.min(count, stack.getCount());
            stack.shrink(taken);
            count -= taken;
            if (count == 0) return true;
        }
        return false;
    }

    private ItemStack enchanted(Item item, ResourceKey<Enchantment> key) {
        ItemStack stack = new ItemStack(item);
        ItemEnchantments.Mutable value = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        value.set(world.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key), 1);
        stack.set(item == Items.ENCHANTED_BOOK ? DataComponents.STORED_ENCHANTMENTS : DataComponents.ENCHANTMENTS, value.toImmutable());
        return stack;
    }

    private void buyBooks(ResourceKey<Enchantment> protection) {
        chapter.onVillagerTrade(world, player, enchanted(Items.ENCHANTED_BOOK, Enchantments.SHARPNESS));
        chapter.onVillagerTrade(world, player, enchanted(Items.ENCHANTED_BOOK, protection));
    }

    private void addFinishedSet() {
        inventory.add(enchanted(Items.IRON_HELMET, Enchantments.PROTECTION));
        inventory.add(enchanted(Items.IRON_CHESTPLATE, Enchantments.FIRE_PROTECTION));
        inventory.add(enchanted(Items.IRON_LEGGINGS, Enchantments.BLAST_PROTECTION));
        inventory.add(enchanted(Items.IRON_BOOTS, Enchantments.PROJECTILE_PROTECTION));
        inventory.add(enchanted(Items.DIAMOND_SWORD, Enchantments.SHARPNESS));
    }

    @Test
    void bothObjectivesAndIndividualEquipmentAreVisibleBeforeBookPurchases() {
        addFinishedSet();
        inventory.remove(3);
        var lines = chapter.progressLines(world, playerId);
        assertEquals(9, lines.size());
        assertEquals("quest.village-quest.story.silent_forge.chapter_4.progress.books", ((TranslatableContents) lines.get(0).getContents()).getKey());
        assertArrayEquals(new Object[]{0, 1}, ((TranslatableContents) lines.get(1).getContents()).getArgs());
        assertArrayEquals(new Object[]{0, 1}, ((TranslatableContents) lines.get(2).getContents()).getArgs());
        assertEquals("quest.village-quest.story.silent_forge.chapter_4.progress.equipment", ((TranslatableContents) lines.get(3).getContents()).getKey());
        assertArrayEquals(new Object[]{"✓"}, ((TranslatableContents) lines.get(4).getContents()).getArgs());
        assertArrayEquals(new Object[]{"—"}, ((TranslatableContents) lines.get(7).getContents()).getArgs());
        assertArrayEquals(new Object[]{"✓"}, ((TranslatableContents) lines.get(8).getContents()).getArgs());
        assertFalse(chapter.isComplete(world, player));
    }

    @Test
    void ownedAndPickedUpBooksAreNotVillagerPurchaseProof() {
        inventory.add(enchanted(Items.ENCHANTED_BOOK, Enchantments.SHARPNESS));
        inventory.add(enchanted(Items.ENCHANTED_BOOK, Enchantments.PROTECTION));
        chapter.onAccepted(world, player);
        chapter.onTrackedItemPickup(world, player, inventory.get(0), 1);
        chapter.onTrackedItemPickup(world, player, inventory.get(1), 1);
        assertEquals(0, data.getStoryInt(StoryQuestKeys.SILENT_FORGE_SHARPNESS_BOOK));
        assertEquals(0, data.getStoryInt(StoryQuestKeys.SILENT_FORGE_PROTECTION_BOOK));
        chapter.onServerTick(world, player);
        assertEquals(0, data.getStoryInt(StoryQuestKeys.SILENT_FORGE_SHARPNESS_BOOK));
        assertEquals(0, data.getStoryInt(StoryQuestKeys.SILENT_FORGE_PROTECTION_BOOK));
    }

    @Test
    void preEnchantedAndDamagedEquipmentNeedsNoCraftingOrAnvilFlags() {
        buyBooks(Enchantments.PROTECTION);
        addFinishedSet();
        inventory.forEach(stack -> stack.setDamageValue(10));
        assertTrue(inventory.stream().allMatch(stack -> stack.getDamageValue() == 10));
        assertTrue(chapter.isComplete(world, player));
        assertEquals(0, data.getStoryInt(StoryQuestKeys.SILENT_FORGE_MASTER_EDGE_CRAFTED));
        assertEquals(0, data.getStoryInt(StoryQuestKeys.SILENT_FORGE_MASTER_HELM));
    }

    @Test
    void everyVanillaProtectionBookWorksAndDuplicateProtectionCannotReplaceSharpness() {
        addFinishedSet();
        for (var protection : List.of(Enchantments.PROTECTION, Enchantments.FIRE_PROTECTION, Enchantments.BLAST_PROTECTION, Enchantments.PROJECTILE_PROTECTION)) {
            new ArrayList<>(data.getStoryIntState().keySet()).forEach(key -> data.setStoryInt(key, 0));
            chapter.onVillagerTrade(world, player, enchanted(Items.ENCHANTED_BOOK, protection));
            chapter.onVillagerTrade(world, player, enchanted(Items.ENCHANTED_BOOK, protection));
            assertFalse(chapter.isComplete(world, player));
            chapter.onVillagerTrade(world, player, enchanted(Items.ENCHANTED_BOOK, Enchantments.SHARPNESS));
            assertTrue(chapter.isComplete(world, player), protection.toString());
        }
    }

    @Test
    void equipmentTradeAndUnrelatedBookCannotProveBookPurchases() {
        addFinishedSet();
        chapter.onVillagerTrade(world, player, inventory.get(0));
        chapter.onVillagerTrade(world, player, inventory.get(4));
        chapter.onVillagerTrade(world, player, enchanted(Items.ENCHANTED_BOOK, Enchantments.UNBREAKING));
        assertFalse(chapter.isComplete(world, player));
        assertEquals(0, data.getStoryInt(StoryQuestKeys.SILENT_FORGE_SHARPNESS_BOOK));
        assertEquals(0, data.getStoryInt(StoryQuestKeys.SILENT_FORGE_PROTECTION_BOOK));
    }

    @Test
    void requiresEveryDistinctIronPieceAndSharpnessDiamondSword() {
        buyBooks(Enchantments.PROTECTION);
        addFinishedSet();
        for (int slot = 0; slot < 5; slot++) {
            ItemStack original = inventory.get(slot);
            inventory.set(slot, ItemStack.EMPTY);
            assertFalse(chapter.isComplete(world, player), "Missing slot " + slot);
            inventory.set(slot, new ItemStack(original.getItem()));
            assertFalse(chapter.isComplete(world, player), "Unenchanted slot " + slot);
            inventory.set(slot, original);
        }
        inventory.set(3, enchanted(Items.IRON_HELMET, Enchantments.PROTECTION));
        assertFalse(chapter.isComplete(world, player), "Duplicate helmet is not boots");
        inventory.set(3, enchanted(Items.DIAMOND_BOOTS, Enchantments.PROTECTION));
        assertFalse(chapter.isComplete(world, player), "Diamond boots are not iron boots");
    }

    @Test
    void completeHandInConsumesOnlyOneOfEachEquipmentAndKeepsBooks() {
        buyBooks(Enchantments.PROTECTION);
        addFinishedSet();
        ItemStack spare = enchanted(Items.IRON_HELMET, Enchantments.PROTECTION);
        ItemStack book = enchanted(Items.ENCHANTED_BOOK, Enchantments.SHARPNESS);
        inventory.add(spare);
        inventory.add(book);
        assertTrue(chapter.consumeCompletionRequirements(world, player));
        assertTrue(inventory.subList(0, 5).stream().allMatch(ItemStack::isEmpty));
        assertEquals(1, spare.getCount());
        assertEquals(1, book.getCount());
    }

    @Test
    void incompleteHandInDoesNotConsumeAnyEquipment() {
        buyBooks(Enchantments.PROTECTION);
        addFinishedSet();
        inventory.remove(3);
        assertFalse(chapter.consumeCompletionRequirements(world, player));
        assertTrue(inventory.stream().allMatch(stack -> stack.getCount() == 1));
        var message = (TranslatableContents) chapter.claimBlockedMessage(world, player).getContents();
        assertEquals("quest.village-quest.story.silent_forge.chapter_4.turnin.missing", message.getKey());
        var missingItem = (Component) message.getArgs()[0];
        assertEquals("quest.village-quest.story.silent_forge.chapter_4.turnin.boots",
                ((TranslatableContents) missingItem.getSiblings().getFirst().getContents()).getKey());
    }

    @Test
    void existingBookMilestonesRemainValidWithoutOldCraftAndAnvilMilestones() {
        QuestState saved = QuestState.fromNbt(new CompoundTag());
        PlayerQuestData oldData = saved.getPlayerData(playerId);
        oldData.setStoryInt(StoryQuestKeys.SILENT_FORGE_SHARPNESS_BOOK, 1);
        oldData.setStoryInt(StoryQuestKeys.SILENT_FORGE_FIRE_PROTECTION_BOOK, 1);
        data = QuestState.fromNbt(QuestState.toNbt(saved)).getPlayerData(playerId);
        addFinishedSet();
        assertTrue(chapter.isComplete(world, player));
    }
}
