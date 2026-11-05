package de.quest.caravan;

import de.quest.shrine.VillageBondType;
import java.util.Locale;
import net.minecraft.network.chat.Component;

/** Names a shipment from the stored source and destination identities. */
final class RegionalDispatchCommission {
    private RegionalDispatchCommission() {}

    static String key(VillageBondType source, VillageBondType target) {
        return (source == target ? "same_" : "to_") + target.name().toLowerCase(Locale.ROOT);
    }

    static Component title(VillageBondType source, VillageBondType target) {
        return Component.translatable("screen.village-quest.dispatch.commission." + key(source, target));
    }
}
