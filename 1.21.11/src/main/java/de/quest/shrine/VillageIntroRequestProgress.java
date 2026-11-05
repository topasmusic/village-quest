package de.quest.shrine;

import de.quest.data.PersonalResetReceipts;
import de.quest.data.PlayerQuestData;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** Bounded persisted episode. Mutation is package-private until the delivery coordinator commits it. */
final class VillageIntroRequestProgress {
    static final String PREFIX = "village_intro.";
    private final PlayerQuestData data;
    private VillageIntroRequestProgress(PlayerQuestData data) { this.data = data; }

    static VillageIntroRequestProgress open(PlayerQuestData data, UUID village, int completions, boolean trusted) {
        if (data == null || village == null || completions < 0) throw new IllegalArgumentException("Invalid intro context");
        var progress = new VillageIntroRequestProgress(data);
        // Absence alone is migratable. Unknown/partial records remain untouched and blocked.
        boolean absent = data.getTradeRouteIntState().keySet().stream().noneMatch(k -> k.startsWith(PREFIX))
                && data.getTradeRouteStringState().keySet().stream().noneMatch(k -> k.startsWith(PREFIX))
                && data.getTradeRouteFlags().stream().noneMatch(k -> k.startsWith(PREFIX));
        if (absent && data.validProgressGeneration() && validCredit(data, village)) {
            data.setTradeRouteInt(PREFIX + "schema", 1);
            data.setTradeRouteInt(PREFIX + "generation", data.progressGeneration());
            data.setTradeRouteString(PREFIX + "village", village.toString());
            data.setTradeRouteString(PREFIX + "episode", UUID.randomUUID().toString());
            data.setTradeRouteInt(PREFIX + "revision", VillageIntroRequestType.CATALOG_REVISION);
            data.setTradeRouteInt(PREFIX + "completed", creditMask(data, village));
            progress.saveCredit();
            if (completions >= 2 || trusted) data.setTradeRouteFlag(PREFIX + "finished", true);
        }
        return progress;
    }

    UUID village() { return uuid("village"); }
    UUID episode() { return uuid("episode"); }
    private UUID uuid(String suffix) {
        try { return UUID.fromString(data.getTradeRouteString(PREFIX + suffix)); }
        catch (IllegalArgumentException invalid) { return null; }
    }
    private int mask() { return data.getTradeRouteInt(PREFIX + "completed"); }
    VillageIntroRequestType active() { return VillageIntroRequestType.fromId(data.getTradeRouteString(PREFIX + "active")); }
    boolean writable() {
        int mask = mask();
        String active = data.getTradeRouteString(PREFIX + "active");
        return data.validProgressGeneration() && data.getTradeRouteInt(PREFIX + "schema") == 1 && village() != null && episode() != null
                && data.getTradeRouteInt(PREFIX + "generation") == data.progressGeneration()
                && data.getTradeRouteInt(PREFIX + "revision") == VillageIntroRequestType.CATALOG_REVISION
                && mask >= 0 && (mask & ~7) == 0 && Integer.bitCount(mask) <= 2
                && (active.isEmpty() || active() != null && (mask & active().bit()) == 0)
                && validHarvests() && paidCount() <= 2 && validCredit(data, village())
                && (creditAbsent(data, village()) || creditMask(data, village()) == mask);
    }

    // One compact record per canonical village in the current personal generation. No episode history.
    static String creditPrefix(UUID village) { return "intro_credits." + village + "."; }
    private static boolean creditAbsent(PlayerQuestData data, UUID village) {
        String prefix = creditPrefix(village);
        return data.getTradeRouteIntState().keySet().stream().noneMatch(k -> k.startsWith(prefix))
                && data.getTradeRouteStringState().keySet().stream().noneMatch(k -> k.startsWith(prefix))
                && data.getTradeRouteFlags().stream().noneMatch(k -> k.startsWith(prefix));
    }
    private static int creditMask(PlayerQuestData data, UUID village) {
        return data.getTradeRouteInt(creditPrefix(village) + "completed");
    }
    private static boolean validCredit(PlayerQuestData data, UUID village) {
        if (creditAbsent(data, village)) return true;
        String prefix = creditPrefix(village); int mask = creditMask(data, village);
        return data.getTradeRouteInt(prefix + "schema") == 1
                && data.getTradeRouteInt(prefix + "generation") == data.progressGeneration()
                && mask >= 0 && (mask & ~7) == 0 && Integer.bitCount(mask) <= 2
                && data.getTradeRouteIntState().keySet().stream().filter(k -> k.startsWith(prefix))
                    .allMatch(k -> k.equals(prefix + "schema") || k.equals(prefix + "generation") || k.equals(prefix + "completed"))
                && data.getTradeRouteStringState().keySet().stream().noneMatch(k -> k.startsWith(prefix))
                && data.getTradeRouteFlags().stream().noneMatch(k -> k.startsWith(prefix));
    }
    private void saveCredit() {
        String prefix = creditPrefix(village());
        data.setTradeRouteInt(prefix + "schema", 1);
        data.setTradeRouteInt(prefix + "generation", data.progressGeneration());
        data.setTradeRouteInt(prefix + "completed", mask());
    }
    /** Data operation only. Caller must prove physical loss and finish pending claims BEFORE invoking it. */
    boolean rebind(UUID destination, int completions, boolean trusted) {
        if (!writable() || destination == null || destination.equals(village()) || completions < 0
                || !validCredit(data, destination)) return false;
        saveCredit();
        int destinationMask = creditMask(data, destination);
        data.setTradeRouteString(PREFIX + "village", destination.toString());
        data.setTradeRouteString(PREFIX + "episode", UUID.randomUUID().toString());
        data.setTradeRouteInt(PREFIX + "completed", destinationMask);
        data.setTradeRouteString(PREFIX + "active", "");
        for (int i = 0; i < 8; i++) data.setTradeRouteString(PREFIX + "harvest." + i, "");
        data.setTradeRouteInt(PREFIX + "harvest_count", 0);
        data.setTradeRouteFlag(PREFIX + "finished", completions >= 2 || trusted || Integer.bitCount(destinationMask) >= 2);
        saveCredit();
        return true;
    }
    private boolean validHarvests() {
        int count = harvestCount();
        if (count < 0 || count > 8 || count != 0 && active() != VillageIntroRequestType.HARVEST_HELP) return false;
        var seen = new java.util.HashSet<UUID>();
        for (int i = 0; i < 8; i++) {
            String raw = data.getTradeRouteString(PREFIX + "harvest." + i);
            if (i >= count) { if (!raw.isEmpty()) return false; }
            else try { if (!seen.add(UUID.fromString(raw))) return false; }
            catch (IllegalArgumentException invalid) { return false; }
        }
        return true;
    }
    List<VillageIntroRequestType> offers(int completions, boolean trusted) {
        if (!writable() || completions < 0 || completions >= 2 || trusted
                || data.hasTradeRouteFlag(PREFIX + "finished") || Integer.bitCount(mask()) >= 2) return List.of();
        return Arrays.stream(VillageIntroRequestType.values()).filter(t -> (mask() & t.bit()) == 0).toList();
    }
    boolean accept(VillageIntroRequestType type, int revision, int completions, boolean trusted) {
        if (type == null || revision != VillageIntroRequestType.CATALOG_REVISION || active() != null
                || !offers(completions, trusted).contains(type)) return false;
        data.setTradeRouteString(PREFIX + "active", type.id());
        return true;
    }
    boolean cancel(VillageIntroRequestType type) {
        if (!writable() || type == null || active() != type) return false;
        data.setTradeRouteString(PREFIX + "active", "");
        data.setTradeRouteInt(PREFIX + "harvest_count", 0);
        for (int i = 0; i < 8; i++) data.setTradeRouteString(PREFIX + "harvest." + i, "");
        // Invalidate every previously displayed acceptance/harvest token, preserving credits and paid IDs.
        data.setTradeRouteString(PREFIX + "episode", UUID.randomUUID().toString());
        return true;
    }
    int paidCount() {
        int count = 0;
        for (var type : VillageIntroRequestType.values()) if (PersonalResetReceipts.has(data, "village_intro." + type.id())) count++;
        return count;
    }
    boolean paysActive() {
        return active() != null && paidCount() < 2 && !PersonalResetReceipts.has(data, "village_intro." + active().id());
    }
    // Called only by the confirmed Q-effects commit, never by a client event.
    boolean complete(VillageIntroRequestType type) {
        if (!writable() || type == null || active() != type
                || type == VillageIntroRequestType.HARVEST_HELP && harvestCount() != 8) return false;
        if (paysActive()) PersonalResetReceipts.record(data, "village_intro." + type.id());
        data.setTradeRouteInt(PREFIX + "completed", mask() | type.bit());
        saveCredit();
        data.setTradeRouteString(PREFIX + "active", "");
        for (int i = 0; i < 8; i++) data.setTradeRouteString(PREFIX + "harvest." + i, "");
        data.setTradeRouteInt(PREFIX + "harvest_count", 0);
        return true;
    }
    int harvestCount() { return data.getTradeRouteInt(PREFIX + "harvest_count"); }
    // The live dispatcher must prove the mature->harvested transition before issuing this receipt.
    boolean harvest(UUID receipt) {
        if (!writable() || receipt == null || active() != VillageIntroRequestType.HARVEST_HELP || harvestCount() >= 8) return false;
        for (int i = 0; i < harvestCount(); i++)
            if (receipt.toString().equals(data.getTradeRouteString(PREFIX + "harvest." + i))) return false;
        int count = harvestCount();
        data.setTradeRouteString(PREFIX + "harvest." + count, receipt.toString());
        data.setTradeRouteInt(PREFIX + "harvest_count", count + 1);
        return true;
    }
}
