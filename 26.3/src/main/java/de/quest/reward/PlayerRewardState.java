package de.quest.reward;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/** Persistent personal reward snapshots; item data is encoded by the server. */
public final class PlayerRewardState {
    public static final int MAX_PENDING = 256;
    public static final int MAX_OPEN_CHOICES = 8; // New acceptances only; legacy queues retain their 32-entry safety limit.
    private CompoundTag root = fresh();
    private boolean writable = true;
    private static CompoundTag fresh() {
        var tag = new CompoundTag(); tag.putInt("schema", 1);
        tag.put("slots", new CompoundTag()); tag.put("first", new CompoundTag()); tag.put("pending", new ListTag()); tag.put("earned", new ListTag());
        return tag;
    }
    public boolean writable() { return writable; }
    public int reservedStacks() {
        int count = pending().size(); var slots = root.getCompoundOrEmpty("slots");
        for (String key : slots.keySet()) { var snapshot = slots.getCompoundOrEmpty(key);
            if (!snapshot.getBooleanOr("redeemed", true)) count += stacks(snapshot); }
        for (var entry : earned()) count += stacks((CompoundTag) entry);
        return count;
    }
    public boolean canAccept() { return writable && reservedStacks() < 240 && choiceCount() < MAX_OPEN_CHOICES; }
    public boolean canEnqueue(int count) { return writable && count >= 0 && reservedStacks() + count <= 240; }
    public boolean accept(String slot, String identity, CompoundTag snapshot) {
        if (!canAccept() || slot == null || identity == null || snapshot == null || snapshot.isEmpty()) return false;
        var slots = root.getCompoundOrEmpty("slots");
        if (reservedStacks() + stacks(snapshot) > MAX_PENDING) return false;
        if (slots.getCompoundOrEmpty(slot).getStringOr("identity", "").equals(identity)) return false;
        var copy = snapshot.copy(); copy.putString("identity", identity);
        copy.putInt("generation", slots.getCompoundOrEmpty(slot).getIntOr("generation", 0) + 1);
        copy.putString("instance", java.util.UUID.randomUUID().toString()); copy.putBoolean("redeemed", false);
        slots.put(slot, copy); return true;
    }
    public CompoundTag snapshot(String slot, String identity) {
        var tag = root.getCompoundOrEmpty("slots").getCompoundOrEmpty(slot);
        return tag.getStringOr("identity", "").equals(identity) ? tag.copy() : new CompoundTag();
    }
    public CompoundTag redeem(String slot, String identity) {
        if (!writable) return new CompoundTag();
        var tag = root.getCompoundOrEmpty("slots").getCompoundOrEmpty(slot);
        if (!tag.getStringOr("identity", "").equals(identity) || tag.getBooleanOr("redeemed", true)) return new CompoundTag();
        tag.putBoolean("redeemed", true); return tag.copy();
    }
    private static int stacks(CompoundTag snapshot) {
        int count = snapshot.getListOrEmpty("items").size();
        for (var entry : snapshot.getListOrEmpty("choices"))
            count = Math.max(count, ((CompoundTag) entry).getListOrEmpty("items").size());
        return count;
    }
    public int choiceCount() {
        int count = earned().size(); var slots = root.getCompoundOrEmpty("slots");
        for (String key : slots.keySet()) {
            var snapshot = slots.getCompoundOrEmpty(key);
            if (!snapshot.getBooleanOr("redeemed", true) && snapshot.contains("choices")) count++;
        }
        return count;
    }
    public ListTag earned() { return root.getListOrEmpty("earned").copy(); }
    /** Moves the already reserved offer independently of the active quest slot. */
    public boolean earn(String slot, String identity) {
        if (!writable || earned().size() >= 32) return false;
        var snapshot = snapshot(slot, identity);
        if (snapshot.isEmpty() || snapshot.getBooleanOr("redeemed", true) || snapshot.getListOrEmpty("choices").isEmpty()) return false;
        var earned = earned(); snapshot = redeem(slot, identity); snapshot.putString("slot", slot);
        earned.add(snapshot); root.put("earned", earned); return true;
    }
    public CompoundTag takeChoice(String instance, String option) {
        if (!writable) return new CompoundTag();
        var earned = earned();
        for (int i = 0; i < earned.size(); i++) {
            var snapshot = (CompoundTag) earned.get(i);
            if (!snapshot.getStringOr("instance", "").equals(instance)) continue;
            for (var entry : snapshot.getListOrEmpty("choices")) {
                var choice = (CompoundTag) entry;
                if (!choice.getStringOr("option", "").equals(option)) continue;
                earned.remove(i); root.put("earned", earned); return choice.copy();
            }
        }
        return new CompoundTag();
    }
    private static boolean validChoices(CompoundTag snapshot) {
        if (!snapshot.contains("choices")) return true;
        if (!(snapshot.get("choices") instanceof ListTag choices) || choices.isEmpty() || choices.size() > 3) return false;
        var ids = new java.util.HashSet<String>();
        for (var entry : choices) {
            if (!(entry instanceof CompoundTag choice) || choice.getStringOr("option", "").isEmpty()
                    || !ids.add(choice.getStringOr("option", ""))) return false;
        }
        return true;
    }
    public void forget(String slot) { if (writable) root.getCompoundOrEmpty("slots").remove(slot); }
    public void resetSlots() { if (writable) root.put("slots", new CompoundTag()); }
    public boolean first(String key) {
        if (!writable || key == null || key.length() > 128) return false;
        var first = root.getCompoundOrEmpty("first");
        if (first.contains(key)) return false;
        first.putBoolean(key, true); return true;
    }
    public String preference() { return root.getStringOr("preference", ""); }
    public String nextIdentity(String slot, String key) {
        return key + ":" + (root.getCompoundOrEmpty("slots").getCompoundOrEmpty(slot).getIntOr("generation", 0) + 1);
    }
    public String currentIdentity(String slot) { return root.getCompoundOrEmpty("slots").getCompoundOrEmpty(slot).getStringOr("identity", ""); }
    public void preference(String option) { if (writable) root.putString("preference", option); }
    public ListTag pending() { return root.getListOrEmpty("pending").copy(); }
    public void pending(ListTag items) {
        if (!writable || items == null || items.size() > MAX_PENDING) throw new IllegalStateException("Reward queue unavailable");
        root.put("pending", items.copy());
    }
    public CompoundTag toNbt() { return root.copy(); }
    public static PlayerRewardState fromNbt(CompoundTag tag) {
        var state = new PlayerRewardState();
        if (tag == null || tag.isEmpty()) return state;
        state.root = tag.copy();
        state.writable = tag.getIntOr("schema", -1) == 1
                && tag.get("slots") instanceof CompoundTag && tag.get("first") instanceof CompoundTag
                && tag.get("pending") instanceof ListTag && tag.getListOrEmpty("pending").size() <= MAX_PENDING;
        if (state.writable) for (String slot : tag.getCompoundOrEmpty("slots").keySet()) {
            if (!(tag.getCompoundOrEmpty("slots").get(slot) instanceof CompoundTag snapshot)
                    || (snapshot.contains("version") && snapshot.getIntOr("version",-1) != 1) || !validChoices(snapshot)) { state.writable = false; break; }
        }
        if (state.writable && tag.contains("earned")) {
            if (!(tag.get("earned") instanceof ListTag earned) || earned.size() > 32) state.writable = false;
            else for (var entry : earned) {
                if (!(entry instanceof CompoundTag offer) || !offer.contains("choices") || !validChoices(offer)
                        || offer.getStringOr("instance", "").isEmpty()) { state.writable = false; break; }
            }
        }
        return state;
    }
}
