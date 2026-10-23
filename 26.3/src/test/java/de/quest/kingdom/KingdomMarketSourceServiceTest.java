package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.SavedDataStorage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Thread/source lifecycle boundaries with mocks only; no server/level construction or startup. */
final class KingdomMarketSourceServiceTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @Test void offThreadLifecycleCannotReadTheWorldOrLiveSourceData() {
        var server=mock(MinecraftServer.class); when(server.isSameThread()).thenReturn(false);
        assertFalse(KingdomMarketSourceService.invalidate(server));
        assertFalse(KingdomMarketSourceService.rebuild(server));
        assertFalse(KingdomMarketSourceService.ready(server));
        verify(server,times(3)).isSameThread(); verifyNoMoreInteractions(server);
    }
    @Test void aCacheWithoutNativeManagerStampCannotAuthorizeANewNativeBuyback() {
        var server=mock(MinecraftServer.class); var level=mock(ServerLevel.class); var storage=mock(SavedDataStorage.class);
        var state=new LivingKingdomsState(); var wheat=Identifier.withDefaultNamespace("wheat");
        assertTrue(state.marketSources().rebuild(() -> MarketSourceClosure.build(
                List.of(new MarketAcquisitionBounds.Acquisition(3,List.of(new MarketAcquisitionBounds.Goods(wheat,64)))),
                new NativeMarketRecipeRules.Reading(true,List.of()),item -> new NativeMarketRecipeRules.Reading(true,List.of()))));
        var saved=LivingKingdomsState.toNbt(state);
        when(server.isSameThread()).thenReturn(true); when(server.overworld()).thenReturn(level);
        when(level.getDataStorage()).thenReturn(storage); when(storage.computeIfAbsent(LivingKingdomsState.TYPE)).thenReturn(state);
        assertFalse(KingdomMarketSourceService.ready(server)); assertFalse(state.marketSources().ready());
        assertEquals(saved,LivingKingdomsState.toNbt(state));
        verify(server,never()).getRecipeManager(); verify(server,never()).reloadableRegistries();
    }
    @Test void missingServerIsHeldForAllLifecycleEntries() {
        assertFalse(KingdomMarketSourceService.invalidate(null)); assertFalse(KingdomMarketSourceService.rebuild(null));
        assertFalse(KingdomMarketSourceService.ready(null));
    }
    @Test void aSuccessfulNativeBuildNeedsItsExactCurrentManagersAndReloadInvalidatesSellAgain() {
        var server=mock(MinecraftServer.class); when(server.isSameThread()).thenReturn(true);
        var recipes=mock(net.minecraft.world.item.crafting.RecipeManager.class);
        var registries=mock(net.minecraft.server.ReloadableServerRegistries.Holder.class);
        var lookup=mock(net.minecraft.core.HolderLookup.Provider.class);
        when(server.getRecipeManager()).thenReturn(recipes); when(server.reloadableRegistries()).thenReturn(registries);
        when(registries.lookup()).thenReturn(lookup);
        var state=new LivingKingdomsState(); var wheat=Identifier.withDefaultNamespace("wheat");
        // A lifecycle fixture, not a claim of global production-price completeness.
        var proof=MarketSourceClosure.build(List.of(new MarketAcquisitionBounds.Acquisition(3,
                List.of(new MarketAcquisitionBounds.Goods(wheat,64)))),new NativeMarketRecipeRules.Reading(true,List.of()),
                item -> new NativeMarketRecipeRules.Reading(true,List.of()));
        try(var states=mockStatic(LivingKingdomsState.class); var builder=mockStatic(NativeMarketSourceBuilder.class)) {
            states.when(() -> LivingKingdomsState.get(server)).thenReturn(state);
            builder.when(() -> NativeMarketSourceBuilder.buildRegional(server)).thenReturn(proof);
            assertTrue(KingdomMarketSourceService.rebuild(server)); assertTrue(KingdomMarketSourceService.ready(server));
            when(registries.lookup()).thenReturn(mock(net.minecraft.core.HolderLookup.Provider.class));
            assertFalse(KingdomMarketSourceService.ready(server)); assertFalse(state.marketSources().ready());
            var held=MarketSourceClosure.build(List.of(new MarketAcquisitionBounds.Acquisition(3,
                    List.of(new MarketAcquisitionBounds.Goods(wheat,64)))),new NativeMarketRecipeRules.Reading(false,List.of()),
                    item -> new NativeMarketRecipeRules.Reading(true,List.of()));
            builder.when(() -> NativeMarketSourceBuilder.buildRegional(server)).thenReturn(held);
            assertFalse(KingdomMarketSourceService.rebuild(server)); assertFalse(KingdomMarketSourceService.ready(server));
        }
    }
}
