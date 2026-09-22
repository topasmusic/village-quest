package de.quest.guildtown;

import de.quest.shrine.VillageBondType;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** Every identity pair appears once and uses a distinct two-part gameplay recipe. */
public enum GuildTownCommission {
    REINFORCED_PLOUGHS(0, "reinforced_ploughs", VillageBondType.GRANARY, VillageBondType.FORGE,
            Mechanic.HARVEST, 24, Mechanic.SMELT, 12, Items.IRON_INGOT, 8),
    WINTER_FEED(1, "winter_feed", VillageBondType.GRANARY, VillageBondType.PASTURE,
            Mechanic.HARVEST, 32, Mechanic.BREED, 3, Items.HAY_BLOCK, 6),
    POLLINATED_ORCHARDS(2, "pollinated_orchards", VillageBondType.GRANARY, VillageBondType.APIARY,
            Mechanic.HONEY, 6, Mechanic.PLANT, 8, Items.OAK_SAPLING, 8),
    SEED_REGISTER(3, "seed_register", VillageBondType.GRANARY, VillageBondType.ARCHIVE,
            Mechanic.HARVEST, 20, Mechanic.TRADE, 3, Items.WRITABLE_BOOK, 2),
    HORSESHOES_AND_TACK(4, "horseshoes_and_tack", VillageBondType.FORGE, VillageBondType.PASTURE,
            Mechanic.SMELT, 10, Mechanic.ANIMAL_CONTACT, 3, Items.LEAD, 2),
    SMOKER_AND_LANTERN_FITTINGS(5, "smoker_and_lantern_fittings", VillageBondType.FORGE, VillageBondType.APIARY,
            Mechanic.SMELT, 12, Mechanic.HONEY, 6, Items.LANTERN, 4),
    TOOL_REGISTER(6, "tool_register", VillageBondType.FORGE, VillageBondType.ARCHIVE,
            Mechanic.SMELT, 12, Mechanic.TRADE, 4, Items.IRON_PICKAXE, 2),
    FLOWERING_PASTURES(7, "flowering_pastures", VillageBondType.PASTURE, VillageBondType.APIARY,
            Mechanic.BREED, 4, Mechanic.PLANT, 12, Items.FLOWER_POT, 4),
    HERD_REGISTER(8, "herd_register", VillageBondType.PASTURE, VillageBondType.ARCHIVE,
            Mechanic.ANIMAL_CONTACT, 5, Mechanic.TRADE, 3, Items.BOOK, 4),
    WAX_SEALED_ROAD_BOOKS(9, "wax_sealed_road_books", VillageBondType.APIARY, VillageBondType.ARCHIVE,
            Mechanic.HONEY, 8, Mechanic.ROUTE_ARRIVAL, 2, Items.HONEYCOMB, 12);

    public enum Mechanic {
        HARVEST("harvest"), SMELT("smelt"), BREED("breed"), HONEY("honey"),
        PLANT("plant"), TRADE("trade"), ANIMAL_CONTACT("animal_contact"), ROUTE_ARRIVAL("route_arrival");

        private final String key;
        Mechanic(String key) { this.key = key; }
        public Component label() {
            return Component.translatable("quest.village-quest.guild_town.mechanic." + key);
        }
    }

    private final int id;
    private final String key;
    private final VillageBondType first;
    private final VillageBondType second;
    private final Mechanic firstMechanic;
    private final int firstTarget;
    private final Mechanic secondMechanic;
    private final int secondTarget;
    private final Item deliveryItem;
    private final int deliveryAmount;

    GuildTownCommission(int id, String key, VillageBondType first, VillageBondType second,
                        Mechanic firstMechanic, int firstTarget, Mechanic secondMechanic, int secondTarget,
                        Item deliveryItem, int deliveryAmount) {
        this.id = id;
        this.key = key;
        this.first = first;
        this.second = second;
        this.firstMechanic = firstMechanic;
        this.firstTarget = firstTarget;
        this.secondMechanic = secondMechanic;
        this.secondTarget = secondTarget;
        this.deliveryItem = deliveryItem;
        this.deliveryAmount = deliveryAmount;
    }

    public int id() { return id; }
    public String key() { return key; }
    public int bit() { return 1 << id; }
    public VillageBondType first() { return first; }
    public VillageBondType second() { return second; }
    public Mechanic firstMechanic() { return firstMechanic; }
    public int firstTarget() { return firstTarget; }
    public Mechanic secondMechanic() { return secondMechanic; }
    public int secondTarget() { return secondTarget; }
    public Item deliveryItem() { return deliveryItem; }
    public int deliveryAmount() { return deliveryAmount; }
    public Component title() { return Component.translatable("quest.village-quest.guild_town.commission." + key + ".title"); }

    public static GuildTownCommission byKey(String raw) {
        String normalized = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        for (GuildTownCommission commission : values()) if (commission.key.equals(normalized)) return commission;
        return null;
    }

    public static GuildTownCommission byId(int id) {
        for (GuildTownCommission commission : values()) if (commission.id == id) return commission;
        return null;
    }
}
