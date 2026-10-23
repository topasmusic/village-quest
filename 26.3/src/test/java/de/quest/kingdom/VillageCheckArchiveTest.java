package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import de.quest.archive.GuildArchiveService;
import de.quest.archive.GuildArchiveService.ArchiveItem;
import de.quest.data.QuestState;
import de.quest.registry.ModItems;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.SavedDataStorage;
import org.junit.jupiter.api.*;

/** Native item tags and SavedData codecs with mocked levels only; no world/server is started. */
final class VillageCheckArchiveTest {
    private final UUID owner=UUID.randomUUID();
    private ServerLevel world; private ServerPlayer player; private QuestState quests; private Item previous;
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        Items.BLAZE_ROD.builtInRegistryHolder().bindComponents(net.minecraft.core.component.DataComponentMap.builder()
                .set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE,1)
                .set(net.minecraft.core.component.DataComponents.ITEM_NAME,net.minecraft.network.chat.Component.literal("Village Seal")).build());
    }
    @BeforeEach void setup() {
        previous=ModItems.VILLAGE_CHECK; ModItems.VILLAGE_CHECK=Items.BLAZE_ROD;
        world=mock(ServerLevel.class); player=mock(ServerPlayer.class); var server=mock(MinecraftServer.class);
        var storage=mock(SavedDataStorage.class);
        quests=QuestState.TYPE.codec().parse(NbtOps.INSTANCE,new CompoundTag()).getOrThrow();
        when(world.getServer()).thenReturn(server); when(server.overworld()).thenReturn(world);
        when(world.getDataStorage()).thenReturn(storage); when(storage.computeIfAbsent(QuestState.TYPE)).thenAnswer(i -> quests);
        when(player.getUUID()).thenReturn(owner);
    }
    @AfterEach void restore() { ModItems.VILLAGE_CHECK=previous; }
    @Test void creativeCopyCannotAdoptLegacyGenerationEvenAfterEarningTheCheck() {
        quests.getPlayerData(owner).setMilestoneFlag(KingdomStoryProgress.CHECK_EARNED,true);
        var raw=new ItemStack(ModItems.VILLAGE_CHECK);
        assertFalse(GuildArchiveService.isValidOwnedStack(world,player,raw,ArchiveItem.VILLAGE_CHECK));
        assertFalse(GuildArchiveService.validateUse(world,player,raw,ArchiveItem.VILLAGE_CHECK));
        assertEquals(0,quests.getPlayerData(owner).getTradeRouteInt("archive.gen.village_check"));
    }
    @Test void earnedPersonalIssueSurvivesSaveButBorrowedAndSupersededCopiesCannotWork() {
        quests.getPlayerData(owner).setMilestoneFlag(KingdomStoryProgress.CHECK_EARNED,true);
        var issued=GuildArchiveService.issueInitial(world,owner,ArchiveItem.VILLAGE_CHECK,new ItemStack(ModItems.VILLAGE_CHECK));
        quests=QuestState.TYPE.codec().parse(NbtOps.INSTANCE,QuestState.TYPE.codec().encodeStart(NbtOps.INSTANCE,quests).getOrThrow()).getOrThrow();
        assertTrue(GuildArchiveService.validateUse(world,player,issued,ArchiveItem.VILLAGE_CHECK));
        var stranger=mock(ServerPlayer.class); var id=UUID.randomUUID(); when(stranger.getUUID()).thenReturn(id);
        quests.getPlayerData(id).setMilestoneFlag(KingdomStoryProgress.CHECK_EARNED,true);
        assertFalse(GuildArchiveService.isValidOwnedStack(world,stranger,issued,ArchiveItem.VILLAGE_CHECK));
        quests.getPlayerData(owner).setTradeRouteInt("archive.gen.village_check",2);
        assertFalse(GuildArchiveService.validateUse(world,player,issued,ArchiveItem.VILLAGE_CHECK));
        assertTrue(GuildArchiveService.isSuperseded(issued));
    }
    @Test void tagAloneCannotGrantEntitlementButOfflinePartyCompletionCanRecoverTheTool() {
        var issued=GuildArchiveService.issueInitial(world,owner,ArchiveItem.VILLAGE_CHECK,new ItemStack(ModItems.VILLAGE_CHECK));
        assertFalse(GuildArchiveService.isValidOwnedStack(world,player,issued,ArchiveItem.VILLAGE_CHECK));
        quests.getPlayerData(owner).setStoryCompleted(KingdomStoryProgress.ARC,true);
        assertTrue(GuildArchiveService.isValidOwnedStack(world,player,issued,ArchiveItem.VILLAGE_CHECK));
    }
}
