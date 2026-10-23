package de.quest.reward;

import de.quest.data.*;
import de.quest.economy.CurrencyService;
import de.quest.network.RewardPayloads;
import de.quest.quest.repeatable.RepeatableTargetProfile;
import java.util.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** One authoritative path for acceptance snapshots, extra currency and lossless item escrow. */
public final class VanillaRewardService {
    private record Pending(ServerLevel world, String identity, boolean admin, ServerLevel location, Vec3 position, long expires) { }
    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private static final RewardChoiceSessions SESSIONS = new RewardChoiceSessions();
    public static PlayerRewardState state(ServerLevel world, UUID player) { return QuestState.get(world.getServer()).getPlayerData(player).vanillaRewards(); }
    public static String dailyIdentity(String type) { return type + ":" + de.quest.util.QuestCalendarService.currentDay(); }
    public static String weeklyIdentity(String type) { return type + ":" + de.quest.util.QuestCalendarService.currentWeekCycle(); }
    public static boolean prepare(ServerLevel world, ServerPlayer player, String slot, String key, String identity,
                                  RepeatableTargetProfile profile, Runnable resume) {
        var all = VanillaRewardRegistry.options(key, profile);
        if (all.isEmpty()) {
            state(world,player.getUUID()).forget(slot); QuestState.get(world.getServer()).setDirty(); return true;
        }
        var state = state(world, player.getUUID());
        var existing = state.snapshot(slot, identity);
        if (!existing.isEmpty()) return state.writable() && !existing.getBooleanOr("redeemed", true);
        if (!state.canAccept()) { player.sendSystemMessage(Component.translatable(state.choiceCount() >= PlayerRewardState.MAX_OPEN_CHOICES
                ? "reward.village-quest.backlog_full" : "reward.village-quest.unavailable", PlayerRewardState.MAX_OPEN_CHOICES), false); return false; }
        var snapshot = new CompoundTag(); snapshot.putString("key", key); snapshot.putInt("version", 1);
        var choices = new ListTag();
        for (var option : all) {
            if (option.diamondGate() && !PlayerProgressionSignals.diamonds(player)) continue;
            var choice = new CompoundTag(); choice.putString("option", option.id()); choice.putLong("currency", option.currency());
            var goods=RewardItems.create(world,option.goods());
            if(key.equals("friends:lutte")) for(var item:goods) item.set(net.minecraft.core.component.DataComponents.ITEM_NAME,
                    de.quest.quest.special.FriendsQuestService.tr("lutte.tool."+option.id()));
            choice.put("items", RewardItems.encode(world, goods));
            choice.put("summaryComponent", ComponentSerialization.CODEC.encodeStart(NbtOps.INSTANCE, summary(option)).getOrThrow());
            var lines = new ListTag();
            for (var line : describe(option)) lines.add(ComponentSerialization.CODEC.encodeStart(NbtOps.INSTANCE, line).getOrThrow());
            choice.put("lines", lines); choices.add(choice);
        }
        snapshot.put("choices", choices);
        boolean accepted = state.accept(slot, identity, snapshot);
        if (accepted) QuestState.get(world.getServer()).setDirty();
        return accepted;
    }
    private static void openEarned(ServerLevel world, ServerPlayer player) { openEarned(world,player,null); }
    private static void openEarned(ServerLevel world, ServerPlayer player, String instance) {
        var state = state(world, player.getUUID());
        if (!state.writable() || !player.isAlive() || player.isSpectator() || state.earned().isEmpty()) return;
        var offer = instance == null ? (CompoundTag) state.earned().getFirst() : state.earned().stream()
                .map(raw -> (CompoundTag)raw).filter(saved -> saved.getStringOr("instance", "").equals(instance)).findFirst().orElse(null);
        if (offer == null) return;
        boolean admin = offer.getStringOr("slot", "").equals("admin_reward_test");
        if (admin && !world.getServer().getPlayerList().isOp(player.nameAndId())) return;
        var choices = new ArrayList<RewardPayloads.Choice>();
        for (var entry : offer.getListOrEmpty("choices")) {
            var choice = (CompoundTag) entry; var lines = new ArrayList<Component>();
            for (var line : choice.getListOrEmpty("lines"))
                lines.add(ComponentSerialization.CODEC.parse(NbtOps.INSTANCE, line).getOrThrow());
            var items = new ArrayList<ItemStack>();
            for (var item : choice.getListOrEmpty("items")) items.add(RewardItems.decode(world, item));
            choices.add(new RewardPayloads.Choice(choice.getStringOr("option", ""), List.copyOf(lines), choice.getLongOr("currency", 0), items));
        }
        String identity = offer.getStringOr("instance", "");
        var ticket = SESSIONS.open(player.getUUID(), identity, choices.stream().map(RewardPayloads.Choice::id).collect(java.util.stream.Collectors.toSet()), world.getGameTime());
        PENDING.put(player.getUUID(), new Pending(world, identity, admin, player.level(), player.position(), ticket.expires()));
        String preferred = state.preference().isEmpty() ? PlayerProgressionSignals.recommendation(player) : state.preference();
        if (!ticket.options().contains(preferred)) preferred = choices.getFirst().id();
        ServerPlayNetworking.send(player, new RewardPayloads.View(ticket.token(), offer.getStringOr("key", ""), List.copyOf(choices), preferred));
    }
    public static void handle(ServerPlayer player, RewardPayloads.Action action) {
        var pending = PENDING.get(player.getUUID());
        if (pending == null || !player.isAlive() || player.isSpectator() || player.level() != pending.location()
                || player.position().distanceToSqr(pending.position()) > 144 || pending.world().getServer() != player.level().getServer()
                || (pending.admin() && !pending.world().getServer().getPlayerList().isOp(player.nameAndId()))) return;
        if (!SESSIONS.consume(player.getUUID(), action.session(), pending.identity(), action.option(), pending.world().getGameTime())) return;
        PENDING.remove(player.getUUID());
        var state = state(pending.world(), player.getUUID());
        var choice = state.takeChoice(pending.identity(), action.option());
        if (choice.isEmpty()) return;
        state.preference(action.option()); pay(pending.world(), player, choice);
        if (de.quest.questmaster.QuestMasterUiService.isOpen(player.getUUID()))
            de.quest.questmaster.QuestMasterUiService.refreshIfOpen(pending.world(),player);
        // The client closes the submitted screen; further earned choices reopen through /vq rewards claim.
    }
    public static void onTick(MinecraftServer server) {
        PENDING.entrySet().removeIf(e -> {
            var pending = e.getValue();
            boolean remove = pending.world().getServer() != server || server.getPlayerList().getPlayer(e.getKey()) == null || pending.world().getGameTime() > pending.expires();
            if (remove) SESSIONS.clear(e.getKey()); return remove;
        });
    }
    public static void complete(ServerLevel world, ServerPlayer player, String slot, String identity) {
        var state = state(world, player.getUUID()); var snapshot = state.snapshot(slot, identity);
        if (!state.writable() || snapshot.isEmpty() || snapshot.getBooleanOr("redeemed", true)) return;
        if (snapshot.contains("choices")) {
            if (state.earn(slot, identity)) {
                QuestState.get(world.getServer()).setDirty();
                player.sendSystemMessage(Component.translatable("reward.village-quest.choice_pending")
                        .withStyle(style -> style.withColor(ChatFormatting.GOLD).withClickEvent(new ClickEvent.RunCommand("/vq rewards claim"))), false);
                openEarned(world, player);
            }
            return;
        }
        snapshot = state.redeem(slot, identity);
        if (!snapshot.isEmpty()) pay(world, player, snapshot);
    }
    private static void pay(ServerLevel world, ServerPlayer player, CompoundTag snapshot) {
        var state = state(world, player.getUUID()); var pending = state.pending(); var items = snapshot.getListOrEmpty("items");
        if (pending.size() + items.size() > PlayerRewardState.MAX_PENDING) throw new IllegalStateException("Reward queue reservation exceeded");
        for (var item : items) pending.add(item.copy());
        state.pending(pending); QuestState.get(world.getServer()).setDirty();
        long amount = snapshot.getLongOr("currency", 0);
        if (amount > 0) CurrencyService.addBalance(world, player.getUUID(), amount);
        player.sendSystemMessage(Component.translatable("reward.village-quest.earned", snapshotSummary(snapshot)).withStyle(ChatFormatting.GOLD), false);
        deliverPending(world, player);
    }
    public static void claimChoice(ServerLevel world, ServerPlayer player, String instance) {
        if (instance == null || instance.isEmpty()) return;
        openEarned(world,player,instance);
    }
    public static int claim(ServerLevel world, ServerPlayer player) {
        int delivered = deliverPending(world, player); openEarned(world, player); return delivered;
    }
    public static int deliverItems(ServerLevel world,ServerPlayer player) {return deliverPending(world,player);}
    private static int deliverPending(ServerLevel world, ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator()) return 0;
        var state = state(world, player.getUUID()); if (!state.writable()) return 0;
        var pending = state.pending(); var remaining = new ListTag(); int delivered = 0;
        for (var raw : pending) {
            try {
                var stack = RewardItems.decode(world, raw); int before = stack.getCount();
                player.getInventory().add(stack); delivered += before - stack.getCount();
                if (!stack.isEmpty()) remaining.add(RewardItems.encode(world, List.of(stack)).getFirst());
            } catch (RuntimeException unavailableComponent) { remaining.add(raw.copy()); }
        }
        state.pending(remaining); QuestState.get(world.getServer()).setDirty();
        if (!remaining.isEmpty()) player.sendSystemMessage(Component.translatable("reward.village-quest.pending", remaining.size())
                .withStyle(style -> style.withColor(ChatFormatting.YELLOW).withClickEvent(new ClickEvent.RunCommand("/vq rewards claim"))), false);
        return delivered;
    }
    public static void firstBonus(ServerLevel world, ServerPlayer player, String key) {
        var state = state(world, player.getUUID());
        if (state.first(key)) { CurrencyService.addBalance(world, player.getUUID(), 8); QuestState.get(world.getServer()).setDirty();
            player.sendSystemMessage(Component.translatable("reward.village-quest.first_bonus", CurrencyService.formatBalance(8)), false); }
    }
    /** Prepare before payment; debit and pending entitlement share the owning QuestState document.
     * Delivery starts only after the paid entitlement is installed. No post-delivery refund. */
    public static boolean enqueuePurchase(ServerLevel world,ServerPlayer player,List<ItemStack> items,long price) {
        if(world==null || player==null || price<0 || !world.getServer().isSameThread()) return false;
        if(items==null || items.isEmpty()) return false;
        for(var item:items) if(item==null || item.isEmpty()) return false;
        var state=state(world,player.getUUID());
        if(!state.writable() || !state.canEnqueue(items.size())) return false;
        net.minecraft.nbt.ListTag pending;
        try {
            var encoded=RewardItems.encode(world,items);
            if(encoded.size()!=items.size() || !state.canEnqueue(encoded.size())) return false;
            pending=state.pending();
            for(var item:encoded) pending.add(item.copy());
        } catch(RuntimeException invalidGoods) {
            de.quest.VillageQuest.LOGGER.warn("Cannot encode paid shop delivery",invalidGoods);
            return false;
        }
        if(!CurrencyService.removeBalance(world,player.getUUID(),price)) return false;
        state.pending(pending);
        QuestState.get(world.getServer()).setDirty();
        // Keep the paid claim on delivery problems; returning false here would encourage a refund.
        try {deliverPending(world,player);}
        catch(RuntimeException unavailableDelivery) {de.quest.VillageQuest.LOGGER.warn("Paid shop goods remain pending",unavailableDelivery);}
        return true;
    }

    public static boolean enqueue(ServerLevel world, ServerPlayer player, List<ItemStack> items) {
        var state = state(world, player.getUUID()); if (!state.writable()) return false;
        var pending = state.pending(); var encoded = RewardItems.encode(world, items);
        if (!state.canEnqueue(encoded.size())) return false;
        for (var item : encoded) pending.add(item.copy());
        state.pending(pending); QuestState.get(world.getServer()).setDirty(); deliverPending(world,player); return true;
    }
    public static List<Component> withSaved(ServerLevel world, UUID player, String slot, List<Component> lines) {
        var result = new ArrayList<>(lines); var summary = savedSummary(world, player, slot, state(world,player).currentIdentity(slot));
        if (!summary.getString().isEmpty()) result.add(summary);
        return List.copyOf(result);
    }
    public static Component joinLines(List<Component> lines) {
        var result = Component.empty(); boolean first = true;
        for (var line : lines) { if (!first) result.append("\n"); result.append(line); first = false; } return result;
    }
    public static List<Component> describe(VanillaRewardRegistry.Option option) {
        List<Component> lines = new ArrayList<>();
        if (option.currency() > 0) lines.add(CurrencyService.formatBalance(option.currency()));
        for (var goods : option.goods()) {
            String key = goods.potion().isEmpty() ? (goods.item().equals("glass") || goods.item().equals("lantern") || goods.item().equals("rail") || goods.item().equals("powered_rail") || goods.item().equals("bookshelf") ? "block" : "item") + ".minecraft." + goods.item()
                    : "item.minecraft.potion.effect." + (goods.potion().contains("healing") ? "healing" : "fire_resistance");
            MutableComponent line = Component.literal(goods.count() + " × ").append(Component.translatable(key));
            if (!goods.potion().isEmpty()) line.append(Component.literal(goods.potion().equals("strong_healing") ? " II" : " (8:00)"));
            lines.add(line);
            for (var enchantment : new TreeMap<>(goods.enchantments()).entrySet()) lines.add(Component.translatable("enchantment.minecraft." + enchantment.getKey()).append(" ").append(Component.translatable("enchantment.level." + enchantment.getValue())));
        }
        return List.copyOf(lines);
    }
    public static Component summary(VanillaRewardRegistry.Option option) {
        var result = Component.empty(); for (var line : describe(option)) { if (!result.getString().isEmpty()) result.append(", "); result.append(line); } return result;
    }
    public static Component savedSummary(ServerLevel world, UUID player, String slot, String identity) {
        var snapshot = state(world, player).snapshot(slot, identity);
        return snapshot.isEmpty() || snapshot.getBooleanOr("redeemed", true) ? Component.empty() : snapshot.contains("choices") ? Component.translatable("reward.village-quest.after_turn_in") : Component.translatable("reward.village-quest.extra", snapshotSummary(snapshot));
    }
    private static Component snapshotSummary(CompoundTag snapshot) {
        var raw = snapshot.get("summaryComponent");
        return raw == null ? Component.literal(snapshot.getStringOr("summary", "")) : ComponentSerialization.CODEC.parse(NbtOps.INSTANCE, raw).result().orElse(Component.empty());
    }
    public static void completeCurrent(ServerLevel world, ServerPlayer player, String slot) {
        complete(world, player, slot, state(world, player.getUUID()).currentIdentity(slot));
    }
    private VanillaRewardService() { }
}
