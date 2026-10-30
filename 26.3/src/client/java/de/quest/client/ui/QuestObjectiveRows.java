package de.quest.client.ui;

import de.quest.quest.QuestObjectiveProgress;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** Uses the same numeric objective contracts as the short tracker. */
public final class QuestObjectiveRows {
    private QuestObjectiveRows() {}
    public record Row(Component label, Item item, int current, int target) {
        public int filledPixels(int width) {
            return target <= 0 ? width : (int)Math.min(width, (long)Math.max(0,current)*width/target);
        }
    }
    public static List<Row> rows(Component objective) {
        if (!objective.getSiblings().isEmpty()) return List.of();
        return QuestObjectiveProgress.parts(objective).stream()
                .map(p -> new Row(p.label(), icon(p.label()), p.current(), p.target())).toList();
    }
    private static Item icon(Component label) {
        String key = ((TranslatableContents)label.getContents()).getKey();
        if (key.equals("entity.minecraft.sheep")) return Items.SHEARS;
        if (key.equals("screen.village-quest.ui_review.logs")) return Items.OAK_LOG;
        if (key.equals("screen.village-quest.ui_review.mined_coal")) return Items.COAL;
        if (key.equals("screen.village-quest.ui_review.wool")) return Items.WOOL.pick(net.minecraft.world.item.DyeColor.WHITE);
        if (key.startsWith("item.minecraft.") || key.startsWith("block.minecraft."))
            return net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier
                    .fromNamespaceAndPath("minecraft", key.substring(key.indexOf("minecraft.")+10)));
        return Items.PAPER;
    }
    public static List<Component> separate(Component objective) { return QuestObjectiveProgress.separate(objective); }
}