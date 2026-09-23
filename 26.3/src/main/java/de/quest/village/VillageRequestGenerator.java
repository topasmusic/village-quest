package de.quest.village;

import de.quest.config.VillageQuestServerConfig.AdventureProfile;
import de.quest.shrine.VillageBondLevel;
import de.quest.shrine.VillageBondType;
import de.quest.shrine.VillageRequestType;
import java.util.ArrayList;
import java.util.List;

/** Deterministic, non-FOMO three-choice generator driven by identity and current need. */
public final class VillageRequestGenerator {
    public static final int OFFER_COUNT = 3;

    private VillageRequestGenerator() {}

    public static List<VillageRequestOffer> generate(VillageBondType type,
                                                     VillageNeed need,
                                                     VillageRequestType current,
                                                     VillageRequestType lastCompleted,
                                                     VillageBondLevel bond,
                                                     VillageCondition condition,
                                                     AdventureProfile profile,
                                                     int seed,
                                                     int supportBonus,
                                                     double rewardBonus) {
        VillageBondType safeType = type == null ? VillageBondType.GRANARY : type;
        VillageNeed safeNeed = need == null ? VillageNeed.forVillage(safeType, seed) : need;
        VillageBondLevel safeBond = bond == null ? VillageBondLevel.KNOWN : bond;
        VillageCondition safeCondition = condition == null ? VillageCondition.STABLE : condition;
        AdventureProfile safeProfile = profile == null ? AdventureProfile.STANDARD : profile;

        List<VillageRequestType> ordered = new ArrayList<>();
        List<VillageRequestType> rotated = rotatedRequests(safeType, seed);
        if (safeNeed.matches(current)) addIfEligible(ordered, current, safeType, lastCompleted);
        for (VillageRequestType request : rotated) {
            if (safeNeed.matches(request)) addIfEligible(ordered, request, safeType, lastCompleted);
        }
        addIfEligible(ordered, current, safeType, lastCompleted);
        for (VillageRequestType request : rotated) {
            addIfEligible(ordered, request, safeType, lastCompleted);
        }
        // The last request is only a soft anti-repeat rule; never let it reduce the board below three choices.
        if (ordered.size() < OFFER_COUNT) addIfEligible(ordered, lastCompleted, safeType, null);

        List<VillageRequestOffer> result = new ArrayList<>(OFFER_COUNT);
        for (VillageRequestType request : ordered) {
            if (result.size() >= OFFER_COUNT) break;
            boolean matching = safeNeed.matches(request);
            int amount = scaledAmount(request.amount(), safeBond, safeCondition, safeProfile);
            long reward = scaledReward(request.reward(), safeBond, safeCondition, amount, request.amount(), rewardBonus);
            int support = (matching ? LivingVillageNetworkService.MATCHING_DELIVERY_SUPPORT
                    : LivingVillageNetworkService.GENERAL_DELIVERY_SUPPORT)
                    + (safeBond == VillageBondLevel.ALLIED ? 2 : 0) + Math.max(0, supportBonus);
            result.add(new VillageRequestOffer(request.id(), request, amount, reward, support, matching));
        }
        return List.copyOf(result);
    }

    static int scaledAmount(int base, VillageBondLevel bond, VillageCondition condition, AdventureProfile profile) {
        double trust = bond == VillageBondLevel.ALLIED ? 0.85 : bond == VillageBondLevel.TRUSTED ? 0.90 : 1.0;
        double urgency = condition == VillageCondition.CRISIS ? 0.75
                : condition == VillageCondition.STRAINED ? 0.85
                : condition == VillageCondition.THRIVING ? 1.10 : 1.0;
        return Math.max(1, (int) Math.ceil(profile.scaleRequestAmount(base) * trust * urgency));
    }

    static long scaledReward(int base, VillageBondLevel bond, VillageCondition condition,
                             int amount, int baseAmount, double externalBonus) {
        double trust = bond == VillageBondLevel.ALLIED ? 1.20 : bond == VillageBondLevel.TRUSTED ? 1.10 : 1.0;
        double urgency = condition == VillageCondition.CRISIS ? 1.20
                : condition == VillageCondition.STRAINED ? 1.10
                : condition == VillageCondition.THRIVING ? 0.95 : 1.0;
        double effort = amount / (double) Math.max(1, baseAmount);
        return Math.max(1L, Math.round(base * trust * urgency * effort * Math.max(1.0, externalBonus)));
    }

    private static List<VillageRequestType> rotatedRequests(VillageBondType type, int seed) {
        List<VillageRequestType> requests = new ArrayList<>();
        for (VillageRequestType value : VillageRequestType.values()) if (value.bondType() == type) requests.add(value);
        if (requests.isEmpty()) return requests;
        int shift = Math.floorMod(seed, requests.size());
        List<VillageRequestType> rotated = new ArrayList<>(requests.size());
        for (int i = 0; i < requests.size(); i++) rotated.add(requests.get((i + shift) % requests.size()));
        return rotated;
    }

    private static void addIfEligible(List<VillageRequestType> target, VillageRequestType request,
                                      VillageBondType type, VillageRequestType excluded) {
        if (request != null && request.bondType() == type && request != excluded && !target.contains(request)) {
            target.add(request);
        }
    }
}
