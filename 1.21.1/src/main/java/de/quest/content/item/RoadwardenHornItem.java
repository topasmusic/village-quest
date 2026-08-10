package de.quest.content.item;

import de.quest.caravan.TradeRouteService;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

public final class RoadwardenHornItem extends Item {
    public RoadwardenHornItem(Settings settings) { super(settings); }

    @Override
    public Text getName(ItemStack stack) {
        return Text.translatable(this.getTranslationKey()).formatted(Formatting.GOLD);
    }

    @Override
    public boolean hasGlint(ItemStack stack) { return true; }

    @Override
    public TypedActionResult<ItemStack> use(World level, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        if (!(level instanceof ServerWorld world) || !(player instanceof ServerPlayerEntity serverPlayer)) {
            return TypedActionResult.success(stack);
        }
        return TradeRouteService.useRoadwardenHorn(world, serverPlayer)
                ? TypedActionResult.success(stack)
                : TypedActionResult.pass(stack);
    }
}
