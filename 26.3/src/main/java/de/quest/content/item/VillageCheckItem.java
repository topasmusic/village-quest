package de.quest.content.item;

import de.quest.archive.GuildArchiveService;
import de.quest.kingdom.KingdomBuildingService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Personal, generation-bound diagnosis; never changes buildings or loads terrain. */
public final class VillageCheckItem extends Item {
    public VillageCheckItem(Properties properties) { super(properties); }
    @Override public boolean isFoil(ItemStack stack) { return !GuildArchiveService.isSuperseded(stack); }
    @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(level instanceof ServerLevel world) || !(player instanceof ServerPlayer actor)) return InteractionResult.SUCCESS;
        if (!GuildArchiveService.validateUse(world,actor,player.getItemInHand(hand),GuildArchiveService.ArchiveItem.VILLAGE_CHECK))
            return InteractionResult.FAIL;
        return KingdomBuildingService.useVillageCheck(world,actor) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
    }
}
