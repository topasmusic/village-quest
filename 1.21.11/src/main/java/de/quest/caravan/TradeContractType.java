package de.quest.caravan;

import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

public enum TradeContractType {
    BAKERS_DELIVERY(Items.WHEAT, 64, 20, 1, TradeRouteSpecialization.PROVISIONS, "bakers"),
    ROADWORKS(Items.STONE_BRICKS, 48, 24, 1, TradeRouteSpecialization.GENERAL, "roadworks"),
    IRON_CONSIGNMENT(Items.IRON_INGOT, 32, 30, 2, TradeRouteSpecialization.FORGE, "iron"),
    ARCHIVE_POST(Items.BOOK, 16, 28, 2, TradeRouteSpecialization.COURIER, "archive"),
    WINTER_FODDER(Items.HAY_BLOCK, 24, 34, 3, TradeRouteSpecialization.LIVESTOCK, "fodder"),
    APIARY_CRATES(Items.HONEY_BOTTLE, 12, 38, 3, TradeRouteSpecialization.PROVISIONS, "apiary"),
    WATCH_SUPPLIES(Items.ARROW, 48, 44, 4, TradeRouteSpecialization.GUARDED, "watch"),
    FESTIVAL_BREAD(Items.BREAD, 48, 50, 5, TradeRouteSpecialization.PROVISIONS, "festival");

    private final Item item;
    private final int amount;
    private final int reward;
    private final int requiredGuildRank;
    private final TradeRouteSpecialization specialization;
    private final String key;

    TradeContractType(Item item, int amount, int reward, int requiredGuildRank,
                      TradeRouteSpecialization specialization, String key) {
        this.item = item;
        this.amount = amount;
        this.reward = reward;
        this.requiredGuildRank = requiredGuildRank;
        this.specialization = specialization;
        this.key = key;
    }

    public Item item() { return item; }
    public int amount() { return amount; }
    public int reward() { return reward; }
    public int requiredGuildRank() { return requiredGuildRank; }
    public TradeRouteSpecialization specialization() { return specialization; }
    public Text title() { return Text.translatable("text.village-quest.trade_guild.contract." + key); }
}
