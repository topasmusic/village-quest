package de.quest.client.ui;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/** Presentation of known argument contracts. Never parses translated prose. */
public final class QuestObjectiveRows {
    private QuestObjectiveRows() {}
    public record Row(Component label, net.minecraft.world.item.Item item, int current, int target) {
        public int filledPixels(int width) {
            return target <= 0 ? width : (int)Math.min(width, (long)Math.max(0,current)*width/target);
        }
    }
    /** Known numeric contracts only; malformed, styled compound and custom objectives keep their text. */
    public static List<Row> rows(Component objective) {
        if (!(objective.getContents() instanceof TranslatableContents t)
                || !objective.getSiblings().isEmpty() || t.getArgs().length != 4) return List.of();
        String[] labels; net.minecraft.world.item.Item[] items;
        switch(t.getKey()) {
            case "quest.village-quest.daily.wheat.progress" -> {
                labels=new String[]{"item.minecraft.wheat","item.minecraft.bread"};
                items=new net.minecraft.world.item.Item[]{net.minecraft.world.item.Items.WHEAT,net.minecraft.world.item.Items.BREAD};
            }
            case "quest.village-quest.daily.wood.progress" -> {
                labels=new String[]{"screen.village-quest.ui_review.logs","block.minecraft.coal_ore"};
                items=new net.minecraft.world.item.Item[]{net.minecraft.world.item.Items.OAK_LOG,net.minecraft.world.item.Items.COAL_ORE};
            }
            case "quest.village-quest.daily.wool.progress" -> {
                labels=new String[]{"entity.minecraft.sheep","screen.village-quest.ui_review.wool"};
                items=new net.minecraft.world.item.Item[]{net.minecraft.world.item.Items.SHEARS,net.minecraft.world.item.Items.WOOL.pick(net.minecraft.world.item.DyeColor.WHITE)};
            }
            default -> { return List.of(); }
        }
        Object[] a=t.getArgs(); int[] counts=new int[4];
        for(int i=0;i<4;i++) {
            if(!(a[i] instanceof Integer n) || n<0) return List.of();
            counts[i]=n;
        }
        return List.of(new Row(Component.translatable(labels[0]).withStyle(objective.getStyle()),items[0],counts[0],counts[1]),
                new Row(Component.translatable(labels[1]).withStyle(objective.getStyle()),items[1],counts[2],counts[3]));
    }

    public static List<Component> separate(Component objective) {
        if (!(objective.getContents() instanceof TranslatableContents text)
                || !objective.getSiblings().isEmpty() || text.getArgs().length != 4) return List.of(objective);
        String[] labels = switch (text.getKey()) {
            case "quest.village-quest.daily.wheat.progress" -> new String[]{"item.minecraft.wheat", "item.minecraft.bread"};
            case "quest.village-quest.daily.wood.progress" -> new String[]{"screen.village-quest.ui_review.logs", "block.minecraft.coal_ore"};
            case "quest.village-quest.daily.wool.progress" -> new String[]{"entity.minecraft.sheep", "screen.village-quest.ui_review.wool"};
            default -> null;
        };
        if (labels == null) return List.of(objective);
        Object[] args = text.getArgs();
        return List.of(Component.translatable("screen.village-quest.ui_review.objective_row",
                        Component.translatable(labels[0]),args[0],args[1]).withStyle(objective.getStyle()),
                Component.translatable("screen.village-quest.ui_review.objective_row",
                        Component.translatable(labels[1]),args[2],args[3]).withStyle(objective.getStyle()));
    }
}
