package de.quest.shrine;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;

/** Frozen bounded claim, shared by the Q journal and a separate player attachment. */
final class VillageIntroTransaction {
    private final CompoundTag image;
    private VillageIntroTransaction(CompoundTag image) { this.image = image.copy(); }
    static Optional<VillageIntroTransaction> prepare(UUID receipt, UUID actor, int generation, UUID episode, UUID village,
            VillageIntroInventoryPlan plan, int completions, long balance, int prosperity, int completedMask, int paidMask,
            HolderLookup.Provider registries) {
        if (receipt == null || actor == null || episode == null || village == null || plan == null || registries == null) return Optional.empty();
        var tag = new CompoundTag(); tag.putInt("schema", 1); tag.putString("receipt", receipt.toString()); tag.putString("actor", actor.toString());
        tag.putString("episode", episode.toString()); tag.putString("village", village.toString()); tag.putInt("generation", generation);
        tag.putInt("revision", VillageIntroRequestType.CATALOG_REVISION); tag.putString("offer", plan.type().id());
        tag.putString("stage", VillageIntroDeliveryCoordinator.Stage.BEFORE.name()); tag.put("plan", plan.toNbt(registries));
        tag.putInt("completionsBefore", completions); tag.putInt("completionsAfter", completions + 1);
        tag.putInt("completedBefore", completedMask); tag.putInt("completedAfter", completedMask | plan.type().bit());
        int reward = (paidMask & plan.type().bit()) == 0 && Integer.bitCount(paidMask) < 2 ? plan.paidReward() : 0;
        tag.putInt("paidBefore", paidMask); tag.putInt("paidAfter", reward == 0 ? paidMask : paidMask | plan.type().bit());
        tag.putInt("reward", reward); tag.putLong("balanceBefore", balance);
        if (balance > Long.MAX_VALUE - reward) return Optional.empty();
        tag.putLong("balanceAfter", balance + reward); tag.putInt("prosperityBefore", prosperity);
        tag.putInt("prosperityAfter", (int) Math.min(Integer.MAX_VALUE, (long) prosperity + reward));
        return fromNbt(tag, registries);
    }
    static Optional<VillageIntroTransaction> fromNbt(Tag payload, HolderLookup.Provider registries) {
        if (!(payload instanceof CompoundTag tag) || registries == null) return Optional.empty();
        for (String key : java.util.List.of("schema", "generation", "revision", "completionsBefore", "completionsAfter",
                "completedBefore", "completedAfter", "paidBefore", "paidAfter", "reward", "prosperityBefore", "prosperityAfter"))
            if (!(tag.get(key) instanceof IntTag)) return Optional.empty();
        if (!(tag.get("balanceBefore") instanceof LongTag) || !(tag.get("balanceAfter") instanceof LongTag)
                || tag.getIntOr("schema", 0) != 1 || tag.getIntOr("revision", 0) != VillageIntroRequestType.CATALOG_REVISION
                || tag.getIntOr("generation", 0) < 1 || !(tag.get("plan") instanceof CompoundTag planTag)) return Optional.empty();
        try {
            for (String key : java.util.List.of("receipt", "actor", "episode", "village"))
                if (UUID.fromString(tag.getStringOr(key, "")).equals(new UUID(0, 0))) return Optional.empty();
            var stage = VillageIntroDeliveryCoordinator.Stage.valueOf(tag.getStringOr("stage", ""));
            if (tag.contains("acknowledged") && (!(tag.get("acknowledged") instanceof net.minecraft.nbt.ByteTag ack)
                    || ack.byteValue() != 1
                    || stage != VillageIntroDeliveryCoordinator.Stage.COMMITTED && stage != VillageIntroDeliveryCoordinator.Stage.CANCELLED)) return Optional.empty();
            var plan = VillageIntroInventoryPlan.fromNbt(planTag, registries);
            var type = VillageIntroRequestType.fromId(tag.getStringOr("offer", ""));
            if (plan.isEmpty() || type == null || plan.get().type() != type) return Optional.empty();
            int c = tag.getIntOr("completionsBefore", -1), done = tag.getIntOr("completedBefore", -1), paid = tag.getIntOr("paidBefore", -1);
            if (c < 0 || c >= 2 || !mask(done) || Integer.bitCount(done) >= 2 || (done & type.bit()) != 0 || !mask(paid)) return Optional.empty();
            int reward = (paid & type.bit()) == 0 && Integer.bitCount(paid) < 2 ? plan.get().paidReward() : 0;
            long balance = tag.getLongOr("balanceBefore", -1); int prosperity = tag.getIntOr("prosperityBefore", -1);
            if (balance < 0 || balance > Long.MAX_VALUE - reward || prosperity < 0 || tag.getIntOr("reward", -1) != reward
                    || tag.getLongOr("balanceAfter", -1) != balance + reward || tag.getIntOr("completionsAfter", -1) != c + 1
                    || tag.getIntOr("completedAfter", -1) != (done | type.bit())
                    || tag.getIntOr("paidAfter", -1) != (reward == 0 ? paid : paid | type.bit())
                    || tag.getIntOr("prosperityAfter", -1) != (int) Math.min(Integer.MAX_VALUE, (long) prosperity + reward)) return Optional.empty();
            return Optional.of(new VillageIntroTransaction(tag));
        } catch (IllegalArgumentException invalid) { return Optional.empty(); }
    }
    private static boolean mask(int mask) { return mask >= 0 && (mask & ~7) == 0 && Integer.bitCount(mask) <= 2; }
    UUID actor() { return UUID.fromString(image.getStringOr("actor", "")); }
    UUID receipt() { return UUID.fromString(image.getStringOr("receipt", "")); }
    int generation() { return image.getIntOr("generation", 0); }
    int reward() { return image.getIntOr("reward", 0); }
    int completionsAfter() { return image.getIntOr("completionsAfter", 0); }
    long balanceBefore() { return image.getLongOr("balanceBefore", 0); }
    long balanceAfter() { return image.getLongOr("balanceAfter", 0); }
    int prosperityAfter() { return image.getIntOr("prosperityAfter", 0); }
    int prosperityBefore() { return image.getIntOr("prosperityBefore", 0); }
    int completionsBefore() { return image.getIntOr("completionsBefore", 0); }
    int completedBefore() { return image.getIntOr("completedBefore", 0); }
    int completedAfter() { return image.getIntOr("completedAfter", 0); }
    int paidBefore() { return image.getIntOr("paidBefore", 0); }
    int paidAfter() { return image.getIntOr("paidAfter", 0); }
    UUID village() { return UUID.fromString(image.getStringOr("village", "")); }
    UUID episode() { return UUID.fromString(image.getStringOr("episode", "")); }
    VillageIntroRequestType type() { return VillageIntroRequestType.fromId(image.getStringOr("offer", "")); }
    VillageIntroInventoryPlan plan(HolderLookup.Provider registries) {
        return VillageIntroInventoryPlan.fromNbt(image.getCompoundOrEmpty("plan"), registries).orElseThrow();
    }
    VillageIntroDeliveryCoordinator.Stage stage() { return VillageIntroDeliveryCoordinator.Stage.valueOf(image.getStringOr("stage", "")); }
    CompoundTag toNbt() { return image.copy(); }
    boolean acknowledged() { return image.getBooleanOr("acknowledged", false); }
    /** Q-only unlock evidence. Set solely AFTER the matching terminal documents were read back. */
    CompoundTag acknowledgedImage() {
        if (stage() != VillageIntroDeliveryCoordinator.Stage.COMMITTED && stage() != VillageIntroDeliveryCoordinator.Stage.CANCELLED)
            throw new IllegalStateException("Nonterminal intro claim");
        var copy = image.copy(); copy.putBoolean("acknowledged", true); return copy;
    }
    VillageIntroTransaction withStage(VillageIntroDeliveryCoordinator.Stage stage) {
        var copy = image.copy(); copy.remove("acknowledged"); copy.putString("stage", stage.name()); return new VillageIntroTransaction(copy);
    }
    boolean sameTransaction(VillageIntroTransaction other) {
        if (other == null) return false;
        var left = image.copy(); var right = other.image.copy(); left.remove("stage"); right.remove("stage");
        left.remove("acknowledged"); right.remove("acknowledged"); return left.equals(right);
    }
}
