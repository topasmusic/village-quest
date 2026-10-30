package de.quest.party;

import java.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.players.PlayerList;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Identity presentation/persistence only; no server/world/game loop. UUID remains the authority. */
final class QuestPartyNamesTest {
    @BeforeAll static void bootstrap() { net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap(); }
    @Test void savedMemberNameSurvivesReloadAndOfflineRemovalMessagePreparation() {
        var id=UUID.randomUUID(); var member=UUID.randomUUID(); var party=new PartyRuntime(UUID.randomUUID(),id);
        party.members().addAll(List.of(id,member)); party.rememberName(id,"Topasmusic"); party.rememberName(member,"Mvrius_VII");
        var nbt=QuestPartyPersistence.write(Map.of(party.id(),party),Map.of());
        var parties=new HashMap<UUID,PartyRuntime>(); var members=new HashMap<UUID,UUID>();
        QuestPartyPersistence.read(nbt,parties,members,new HashMap<>());
        var restored=parties.get(party.id()); assertEquals("Mvrius_VII",restored.memberNames().get(member));
        var world=mock(ServerLevel.class); var server=mock(MinecraftServer.class); var players=mock(PlayerList.class);
        when(world.getServer()).thenReturn(server); when(server.getPlayerList()).thenReturn(players);
        Component departure=QuestPartyMessaging.nameOf(world,restored,member);
        restored.members().remove(member); restored.memberNames().remove(member);
        assertEquals("Mvrius_VII",departure.getString()); assertEquals(party.id(),members.get(member));
        assertTrue(QuestPartyMessaging.nameOf(world,restored,id).getString().contains("Topasmusic"));
    }
    @Test void disconnectCanRememberThePlayerObjectEvenWhenTheOnlineListHasAlreadyDroppedIt() {
        var id=UUID.randomUUID(); var party=new PartyRuntime(UUID.randomUUID(),id); party.members().add(id);
        var player=mock(ServerPlayer.class); when(player.getUUID()).thenReturn(id);
        when(player.getGameProfile()).thenReturn(new com.mojang.authlib.GameProfile(id,"Mvrius_VII"));
        var world=mock(ServerLevel.class); var server=mock(MinecraftServer.class);
        when(world.getServer()).thenReturn(server); when(server.getPlayerList()).thenReturn(mock(PlayerList.class));
        QuestPartyMessaging.rememberName(party,player);
        assertEquals("Mvrius_VII",QuestPartyMessaging.nameOf(world,party,id).getString());
        when(player.getGameProfile()).thenReturn(new com.mojang.authlib.GameProfile(id,"NewName"));
        QuestPartyMessaging.rememberName(party,player);
        assertEquals("NewName",QuestPartyMessaging.nameOf(world,party,id).getString());
    }
    @Test void legacyMembershipStillLoadsAndInvalidOrNonMemberNamesAreNotStored() {
        var id=UUID.randomUUID(); var party=new PartyRuntime(UUID.randomUUID(),id); party.members().add(id);
        assertFalse(party.rememberName(id,"bad\nname")); assertFalse(party.rememberName(id,"x".repeat(65)));
        assertFalse(party.rememberName(UUID.randomUUID(),"Someone"));
        var parties=new HashMap<UUID,PartyRuntime>(); var members=new HashMap<UUID,UUID>();
        QuestPartyPersistence.read(QuestPartyPersistence.write(Map.of(party.id(),party),Map.of()),parties,members,new HashMap<>());
        assertEquals(party.id(),members.get(id)); assertTrue(parties.get(party.id()).memberNames().isEmpty());
        var world=mock(ServerLevel.class); var server=mock(MinecraftServer.class);
        when(world.getServer()).thenReturn(server); when(server.getPlayerList()).thenReturn(mock(PlayerList.class));
        var unknown=QuestPartyMessaging.nameOf(world,party,id);
        assertEquals("message.village-quest.party.unknown",((net.minecraft.network.chat.contents.TranslatableContents)unknown.getContents()).getKey());
    }
}
