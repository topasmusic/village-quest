package de.quest.quest.story;

import de.quest.quest.QuestCompletionMode;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public interface StoryChapterDefinition {
    Component title();

    Component offerParagraph1();

    Component offerParagraph2();

    default List<Component> descriptionLines() { return List.of(offerParagraph1(), offerParagraph2()); }

    default List<Component> descriptionLines(ServerLevel world, UUID playerId) { return descriptionLines(); }

    List<Component> progressLines(ServerLevel world, UUID playerId);

    /** Read-only next action; reuse the journal snapshot rather than querying it twice. */
    default Component shortObjective(ServerLevel world, UUID playerId, List<Component> progress) {
        Component next = de.quest.quest.QuestShortObjective.current(progress);
        if (next.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents text
                && text.getKey().equals("quest.village-quest.guidance.claim") && world != null && playerId != null) {
            ServerPlayer player = world.getServer().getPlayerList().getPlayer(playerId);
            Component blocked = player == null ? null : claimBlockedMessage(world, player, true);
            if(blocked!=null && de.quest.quest.QuestShortObjective.rows(blocked).stream().allMatch(c->
                    c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
                    && t.getKey().equals("quest.village-quest.tracker.friend_material"))) return blocked;
            if (blocked != null && blocked.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents missing
                    && missing.getKey().startsWith("text.village-quest.turnin_missing.")) {
                return de.quest.quest.QuestShortObjective.group(de.quest.quest.QuestShortObjective.turnInMaterials(blocked));
            }
        }
        return next;
    }

    boolean isComplete(ServerLevel world, ServerPlayer player);

    StoryChapterCompletion buildCompletion();

    /** Read-only presentation for the saved objective mode; reward identity stays unchanged. */
    default StoryChapterCompletion buildCompletion(ServerLevel world, UUID playerId) { return buildCompletion(); }

    default QuestCompletionMode completionMode() { return QuestCompletionMode.QUESTMASTER_TURN_IN; }

    default boolean canAccept(ServerLevel world, ServerPlayer player) { return true; }

    default Component acceptBlockedMessage(ServerLevel world, ServerPlayer player) { return null; }

    default boolean consumeCompletionRequirements(ServerLevel world, ServerPlayer player) { return true; }

    default Component claimBlockedMessage(ServerLevel world, ServerPlayer player) { return null; }

    default Component claimBlockedMessage(ServerLevel world, ServerPlayer player, boolean allRequirements) {
        return claimBlockedMessage(world, player);
    }

    default void onAccepted(ServerLevel world, ServerPlayer player) {}

    default void onClaimed(ServerLevel world, ServerPlayer player) {}

    default void onServerTick(ServerLevel world, ServerPlayer player) {}

    default void onUseBlock(ServerLevel world, ServerPlayer player, BlockPos pos, BlockState state, ItemStack inHand) {}

    default void onBlockBreak(ServerLevel world, ServerPlayer player, BlockPos pos, BlockState state) {}

    default void onEntityUse(ServerLevel world, ServerPlayer player, Entity entity, ItemStack inHand) {}

    default void onSheepSheared(ServerLevel world, ServerPlayer player, Sheep sheep) {}

    default void onTrackedItemPickup(ServerLevel world, ServerPlayer player, ItemStack stack, int count) {}

    default void onFurnaceOutput(ServerLevel world, ServerPlayer player, ItemStack stack) {}

    default void onAnvilOutput(ServerLevel world, ServerPlayer player, ItemStack leftInput, ItemStack rightInput, ItemStack output) {}

    default void onVillagerTrade(ServerLevel world, ServerPlayer player, ItemStack stack) {}

    default void onAnimalLove(ServerLevel world, ServerPlayer player, Animal animal) {}

    default void onPilgrimPurchase(ServerLevel world, ServerPlayer player, String offerId) {}

    default void onMonsterKill(ServerLevel world, ServerPlayer player, Entity killedEntity) {}

    default void onBeeNestInteract(ServerLevel world, ServerPlayer player, BlockState state, ItemStack inHand) {}
}
