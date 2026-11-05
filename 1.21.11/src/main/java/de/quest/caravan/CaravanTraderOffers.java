package de.quest.caravan;

import de.quest.shrine.VillageBondType;
import java.util.List;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** Small fixed inventories tied to the destination's saved village identity. */
final class CaravanTraderOffers {
    private CaravanTraderOffers() {}

    static List<Offer> general() {
        return List.of(new Offer(Items.BREAD, 4, 2), new Offer(Items.PAPER, 8, 2));
    }

    static List<Offer> forVillage(VillageBondType type) {
        return switch (type) {
            case GRANARY -> List.of(new Offer(Items.BREAD, 4, 2),
                    new Offer(Items.PUMPKIN_PIE, 2, 3));
            case FORGE -> List.of(new Offer(Items.IRON_NUGGET, 8, 3),
                    new Offer(Items.COAL, 8, 3));
            case PASTURE -> List.of(new Offer(Items.LEATHER, 4, 3),
                    new Offer(Items.LEAD, 1, 3));
            case APIARY -> List.of(new Offer(Items.HONEY_BOTTLE, 2, 3),
                    new Offer(Items.HONEYCOMB, 4, 3));
            case ARCHIVE -> List.of(new Offer(Items.PAPER, 8, 2),
                    new Offer(Items.BOOK, 2, 3));
        };
    }

    record Offer(Item item, int count, int silvermarks) {}
}
