package de.quest.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.storage.PlayerDataStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PlayerList.class)
public interface MarketPlayerSaveInvoker {
    @Invoker("save") void villageQuest$saveMarketPlayer(ServerPlayer player);
    @Accessor("playerIo") PlayerDataStorage villageQuest$marketPlayerStorage();
}
