package de.quest.ambient;

import de.quest.entity.AmbientAdventurerEntity;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.Projectile;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real native-boundary predicates with mocks; no game/server/world is started. */
final class AmbientAdventurerCombatTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    private Mob zombie() {
        var m=mock(Mob.class); when(m.getType()).thenReturn((EntityType)EntityTypes.ZOMBIE);
        when(m.isAlive()).thenReturn(true); when(m.entityTags()).thenReturn(Set.of()); return m;
    }
    @Test void onlyVisibleLiveActiveThreatsWithinEightBlocksCauseAlarm() {
        var npc=mock(LivingEntity.class); var enemy=mock(net.minecraft.world.entity.monster.Monster.class);
        when(enemy.isAlive()).thenReturn(true); when(npc.distanceToSqr(enemy)).thenReturn(16D);
        assertFalse(AmbientAdventurerCombat.perceivedThreat(npc,enemy));
        when(npc.hasLineOfSight(enemy)).thenReturn(true); assertTrue(AmbientAdventurerCombat.perceivedThreat(npc,enemy));
        when(npc.distanceToSqr(enemy)).thenReturn(65D); assertFalse(AmbientAdventurerCombat.perceivedThreat(npc,enemy));
        when(npc.distanceToSqr(enemy)).thenReturn(16D); when(enemy.isNoAi()).thenReturn(true);
        assertFalse(AmbientAdventurerCombat.perceivedThreat(npc,enemy)); when(enemy.isNoAi()).thenReturn(false);
        when(enemy.isRemoved()).thenReturn(true); assertFalse(AmbientAdventurerCombat.perceivedThreat(npc,enemy));
        when(enemy.isRemoved()).thenReturn(false); when(enemy.isAlive()).thenReturn(false);
        assertFalse(AmbientAdventurerCombat.perceivedThreat(npc,enemy));
    }
    @Test void calmSpidersAndNeutralMonstersDoNotDriveTravelersAway() {
        var npc=mock(LivingEntity.class);
        for(var enemy:new Mob[]{mock(net.minecraft.world.entity.monster.spider.Spider.class),mock(net.minecraft.world.entity.monster.Monster.class,withSettings().extraInterfaces(NeutralMob.class))}) {
            when(enemy.isAlive()).thenReturn(true); when(npc.hasLineOfSight(enemy)).thenReturn(true);
            assertFalse(AmbientAdventurerCombat.perceivedThreat(npc,enemy));
            when(enemy.getTarget()).thenReturn(npc); assertTrue(AmbientAdventurerCombat.perceivedThreat(npc,enemy));
        }
    }
    @Test void exactNativeAllowlistRejectsNeutralBossQuestRaidAndModdedTypes() {
        for(var t:new EntityType<?>[]{EntityTypes.ZOMBIE,EntityTypes.HUSK,EntityTypes.SKELETON,EntityTypes.STRAY,EntityTypes.BOGGED,EntityTypes.SPIDER}) assertTrue(AmbientAdventurerCombat.ordinary(t));
        for(var t:new EntityType<?>[]{EntityTypes.ZOMBIE_VILLAGER,EntityTypes.CREEPER,EntityTypes.CAVE_SPIDER,EntityTypes.WITCH,EntityTypes.WARDEN,EntityTypes.ENDERMAN,EntityTypes.PILLAGER,EntityTypes.WOLF,EntityTypes.IRON_GOLEM}) assertFalse(AmbientAdventurerCombat.ordinary(t));
        assertFalse(AmbientAdventurerCombat.ordinary(mock(EntityType.class)));
    }
    @Test void totalOrdinaryDamageIsFourAndVisualSwordAddsNoEquipmentModifiers() {
        var modifiers=net.minecraft.world.item.component.ItemAttributeModifiers.builder().add(
                net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE,
                new net.minecraft.world.entity.ai.attributes.AttributeModifier(net.minecraft.resources.Identifier.parse("test:ambient_sword"),5,
                        net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE),EquipmentSlotGroup.MAINHAND).build();
        net.minecraft.world.item.Items.IRON_SWORD.builtInRegistryHolder().bindComponents(net.minecraft.core.component.DataComponentMap.builder()
                .set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE,1).set(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS,modifiers).build());
        net.minecraft.world.item.Items.TORCH.builtInRegistryHolder().bindComponents(net.minecraft.core.component.DataComponentMap.builder()
                .set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE,64).build());
        assertEquals(4,AmbientAdventurerEntity.createAttributes().build().getValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE));
        var weapon=AmbientAdventurerEntity.appearanceItem(true,true);
        assertTrue(weapon.is(net.minecraft.world.item.Items.IRON_SWORD));
        assertTrue(weapon.get(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS).modifiers().isEmpty());
        assertEquals(0,weapon.getItem().getAttackDamageBonus(mock(Entity.class),4,null));
        assertTrue(AmbientAdventurerEntity.appearanceItem(false,false).isEmpty());
        assertTrue(AmbientAdventurerEntity.appearanceItem(false,true).is(net.minecraft.world.item.Items.TORCH));
    }
    @Test void namedPersistentTaggedAndTransportBoundMonstersNeverBecomeTargets() {
        var m=zombie(); assertTrue(AmbientAdventurerCombat.eligible(m,true));
        when(m.hasCustomName()).thenReturn(true); assertFalse(AmbientAdventurerCombat.eligible(m,false)); when(m.hasCustomName()).thenReturn(false);
        when(m.isPersistenceRequired()).thenReturn(true); assertFalse(AmbientAdventurerCombat.eligible(m,false)); when(m.isPersistenceRequired()).thenReturn(false);
        when(m.isPassenger()).thenReturn(true); assertFalse(AmbientAdventurerCombat.eligible(m,false)); when(m.isPassenger()).thenReturn(false);
        when(m.isVehicle()).thenReturn(true); assertFalse(AmbientAdventurerCombat.eligible(m,false)); when(m.isVehicle()).thenReturn(false);
        for(String tag:new String[]{"vq_trade_road_hostile","vq_empty_caravan_attacker","vq_trade_route_attacker","village-quest:protected"}) {
            when(m.entityTags()).thenReturn(Set.of(tag)); assertFalse(AmbientAdventurerCombat.eligible(m,false));
        }
    }
    @Test void playerOrPetCombatIsNeverTakenOverButExistingNpcFightMayFinish() {
        var m=zombie(); var player=mock(ServerPlayer.class);
        when(m.getTarget()).thenReturn(player); assertFalse(AmbientAdventurerCombat.eligible(m,true)); assertTrue(AmbientAdventurerCombat.eligible(m,false));
        when(m.getTarget()).thenReturn(null); when(m.getLastHurtByPlayerMemoryTime()).thenReturn(100); assertFalse(AmbientAdventurerCombat.eligible(m,true));
        when(m.getLastHurtByPlayerMemoryTime()).thenReturn(0); when(m.getLastHurtByMob()).thenReturn(mock(TamableAnimal.class));
        assertFalse(AmbientAdventurerCombat.eligible(m,true));
    }
    @Test void spiderMustAlreadyBeActivelyAggressive() {
        var m=zombie(); when(m.getType()).thenReturn((EntityType)EntityTypes.SPIDER);
        assertFalse(AmbientAdventurerCombat.eligible(m,false));
        when(m.getTarget()).thenReturn(mock(AmbientAdventurerEntity.class)); assertTrue(AmbientAdventurerCombat.eligible(m,true));
    }
    @Test void causalPlayerProjectilesAndTntWorkButPetOwnerAndUnknownTrapsNeverStandIn() {
        var source=mock(DamageSource.class); var player=mock(ServerPlayer.class);
        when(source.getEntity()).thenReturn(player); assertSame(player,AmbientAdventurerCombat.player(source));
        var projectile=mock(Projectile.class); when(projectile.getOwner()).thenReturn(player);
        when(source.getEntity()).thenReturn(null); when(source.getDirectEntity()).thenReturn(projectile); assertSame(player,AmbientAdventurerCombat.player(source));
        var tnt=mock(PrimedTnt.class); when(tnt.getOwner()).thenReturn(player); when(source.getEntity()).thenReturn(tnt); assertSame(player,AmbientAdventurerCombat.player(source));
        var pet=mock(TamableAnimal.class); when(pet.getOwner()).thenReturn(player); when(source.getEntity()).thenReturn(pet); assertNull(AmbientAdventurerCombat.player(source));
        when(source.getEntity()).thenReturn(null); when(source.getDirectEntity()).thenReturn(null); assertNull(AmbientAdventurerCombat.player(source));
    }
    @Test void onlyDirectNightrunnerFinalKillSuppressesLootRegardlessOfRecentPlayerDamage() {
        var victim=zombie(); when(victim.getLastHurtByPlayerMemoryTime()).thenReturn(100);
        var source=mock(DamageSource.class); var npc=mock(AmbientAdventurerEntity.class); when(npc.isNightrunner()).thenReturn(true);
        when(source.getEntity()).thenReturn(npc); when(source.getDirectEntity()).thenReturn(npc);
        assertTrue(AmbientAdventurerCombat.suppressLoot(victim,source));
        when(source.getEntity()).thenReturn(mock(ServerPlayer.class)); assertFalse(AmbientAdventurerCombat.suppressLoot(victim,source));
        when(source.getEntity()).thenReturn(npc); when(source.getDirectEntity()).thenReturn(mock(Projectile.class)); assertFalse(AmbientAdventurerCombat.suppressLoot(victim,source));
        when(source.getDirectEntity()).thenReturn(npc); assertFalse(AmbientAdventurerCombat.suppressLoot(mock(ServerPlayer.class),source));
        when(npc.isNightrunner()).thenReturn(false); assertFalse(AmbientAdventurerCombat.suppressLoot(victim,source));
    }
}
