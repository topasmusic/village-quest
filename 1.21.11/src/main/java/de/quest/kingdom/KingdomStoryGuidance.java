package de.quest.kingdom;

import de.quest.data.PlayerQuestData;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;

/** First unfinished requirement from the same earned evidence used by the five story chapters. */
public final class KingdomStoryGuidance {
    private static final String TEXT = "quest.village-quest.story.living_kingdoms.next.";
    private KingdomStoryGuidance() {}

    public static Component next(PlayerQuestData data, LivingKingdomsState state, int chapter) {
        var target = KingdomStoryProgress.target(data);
        if (target == null) return Component.translatable(TEXT+"target");
        var village = state.village(target);
        if (village.isEmpty()) return Component.translatable(TEXT+"recover_target");
        String name = village.get().name();
        if (KingdomStoryProgress.complete(data,state,chapter)) return Component.translatable(TEXT+"claim",name);
        if (chapter<2) {
            for (var role : List.of(BuildingScanner.Role.COUNCIL, BuildingScanner.Role.INN,
                    BuildingScanner.Role.MARKET, BuildingScanner.Role.STOREHOUSE)) {
                if ((chapter==0 ? role==BuildingScanner.Role.COUNCIL : role!=BuildingScanner.Role.COUNCIL)
                        && !KingdomStoryProgress.earned(data,state,role))
                    return Component.translatable(TEXT+"building", name, Component.translatable(
                            "screen.village-quest.building.role."+role.name().toLowerCase(Locale.ROOT)));
            }
            if (chapter==1 && !KingdomStoryProgress.hasMarketPlace(data,state)) return Component.translatable(TEXT+"place",name);
        }
        List<KingdomStoryProgress.Deed> deeds = switch (chapter) {
            case 2 -> List.of(KingdomStoryProgress.Deed.MEET_LEADER,KingdomStoryProgress.Deed.MEET_INNKEEPER,KingdomStoryProgress.Deed.MEET_STOREKEEPER);
            case 3 -> List.of(KingdomStoryProgress.Deed.MARKET_TRADE,KingdomStoryProgress.Deed.STORE_DELIVERY);
            case 4 -> List.of(KingdomStoryProgress.Deed.FIRST_PROJECT);
            default -> List.of();
        };
        for (var deed : deeds) if (!KingdomStoryProgress.has(data,deed))
            return Component.translatable(TEXT+deed.name().toLowerCase(Locale.ROOT), name);
        return Component.empty();
    }
}
