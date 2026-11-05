package de.quest.kingdom;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.RecipeManager;

/** Owning-thread native source lifecycle. Derived proofs/manager stamps never enter SavedData. */
public final class KingdomMarketSourceService {
    private static final Map<MinecraftServer,Stamp> STAMPS=Collections.synchronizedMap(new WeakHashMap<>());
    private record Stamp(RecipeManager recipes,HolderLookup.Provider lookup) {
        static Stamp capture(MinecraftServer server) { return new Stamp(server.getRecipeManager(),server.reloadableRegistries().lookup()); }
        boolean current(MinecraftServer server) { return recipes==server.getRecipeManager() && lookup==server.reloadableRegistries().lookup(); }
    }
    private KingdomMarketSourceService() {}
    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> onThread(server,() -> rebuild(server)));
        ServerLifecycleEvents.START_DATA_PACK_RELOAD.register((server,resources) -> onThread(server,() -> invalidate(server)));
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server,resources,success) -> onThread(server,() -> {
            if(success) rebuild(server); else invalidate(server);
        }));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> onThread(server,() -> invalidate(server)));
        ServerLifecycleEvents.SERVER_STOPPED.register(STAMPS::remove);
    }
    private static void onThread(MinecraftServer server,Runnable action) {
        if(server.isSameThread()) action.run(); else server.execute(action);
    }
    public static boolean invalidate(MinecraftServer server) {
        if(server==null || !server.isSameThread()) return false;
        STAMPS.remove(server);
        try { return LivingKingdomsState.get(server).marketSources().invalidate(); }
        catch(RuntimeException unavailable) { return false; }
    }
    public static boolean rebuild(MinecraftServer server) {
        if(server==null || !server.isSameThread()) return false;
        STAMPS.remove(server); LivingKingdomsState state=null;
        try {
            state=LivingKingdomsState.get(server);
            if(state.readOnly() || state.markets().readOnly()) { state.marketSources().invalidate(); return false; }
            var stamp=Stamp.capture(server);
            if(!state.marketSources().rebuild(() -> {
                var proof=NativeMarketSourceBuilder.buildRegional(server);
                var item=proof.unprovenItem();
                String kind="not applicable";
                if(item.isPresent()) {
                    var nativeItem=net.minecraft.core.registries.BuiltInRegistries.ITEM.get(item.get());
                    if(nativeItem.isPresent()) {
                        var value=nativeItem.get().value(); kind=value.getClass().getSimpleName();
                        if(value instanceof net.minecraft.world.item.BlockItem block) kind+="/"+block.getBlock().getClass().getSimpleName();
                    } else kind="unregistered";
                }
                de.quest.VillageQuest.LOGGER.info("Living Kingdoms market sources: status={}, reachable={}, firstUnprovenItem={}, nativeType={}",
                        proof.complete()?"READY":"HELD",proof.reachable().size(),item.map(Object::toString).orElse("preflight/recipe/data/work-bound"),kind);
                return proof;
            })) return false;
            if(!stamp.current(server)) { state.marketSources().invalidate(); return false; }
            STAMPS.put(server,stamp); return true;
        } catch(RuntimeException unavailable) {
            if(state!=null) state.marketSources().invalidate(); return false;
        }
    }
    public static boolean ready(MinecraftServer server) {
        if(server==null || !server.isSameThread()) return false;
        LivingKingdomsState state=null;
        try {
            state=LivingKingdomsState.get(server); if(!state.marketSources().ready()) return false;
            var stamp=STAMPS.get(server);
            if(state.readOnly() || state.markets().readOnly() || stamp==null || !stamp.current(server)) {
                STAMPS.remove(server); state.marketSources().invalidate(); return false;
            }
            return true;
        } catch(RuntimeException unavailable) {
            STAMPS.remove(server); if(state!=null) state.marketSources().invalidate(); return false;
        }
    }
}
