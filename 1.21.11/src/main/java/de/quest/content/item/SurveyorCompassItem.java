package de.quest.content.item;

import de.quest.quest.special.SurveyorCompassQuestService;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

public final class SurveyorCompassItem extends Item {
    public SurveyorCompassItem(Settings settings) {
        super(settings);
    }

    @Override
    public Text getName(ItemStack stack) {
        return Text.translatable(this.getTranslationKey()).formatted(Formatting.GOLD);
    }

    @Override
    public boolean hasGlint(ItemStack stack) {
        return true;
    }

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        if (!(world instanceof ServerWorld serverWorld) || !(user instanceof ServerPlayerEntity serverPlayer)) {
            return ActionResult.SUCCESS;
        }
        return SurveyorCompassQuestService.useCompass(serverWorld, serverPlayer, user.getStackInHand(hand));
    }
}
