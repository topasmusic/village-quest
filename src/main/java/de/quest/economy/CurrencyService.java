package de.quest.economy;

import de.quest.data.PlayerQuestData;
import de.quest.data.QuestState;
import de.quest.registry.ModItems;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.UUID;

public final class CurrencyService {
    public static final long SILVERMARK = 1L;
    public static final long CROWN = 10L;
    private static final long LEGACY_COPPER = 1L;
    private static final long LEGACY_IRON = SILVERMARK;
    private static final long LEGACY_GOLD = CROWN;

    private CurrencyService() {}

    public static long getBalance(ServerWorld world, UUID playerId) {
        if (world == null || playerId == null) {
            return 0L;
        }
        return QuestState.get(world.getServer()).getPlayerData(playerId).getCurrencyBalance();
    }

    public static long addBalance(ServerWorld world, UUID playerId, long amount) {
        if (world == null || playerId == null || amount == 0L) {
            return getBalance(world, playerId);
        }

        PlayerQuestData data = QuestState.get(world.getServer()).getPlayerData(playerId);
        long next = clampAdd(data.getCurrencyBalance(), amount);
        if (next != data.getCurrencyBalance()) {
            data.setCurrencyBalance(next);
            QuestState.get(world.getServer()).markDirty();
        }
        return next;
    }

    public static long setBalance(ServerWorld world, UUID playerId, long amount) {
        if (world == null || playerId == null) {
            return 0L;
        }

        PlayerQuestData data = QuestState.get(world.getServer()).getPlayerData(playerId);
        long next = Math.max(0L, amount);
        if (next != data.getCurrencyBalance()) {
            data.setCurrencyBalance(next);
            QuestState.get(world.getServer()).markDirty();
        }
        return next;
    }

    public static boolean canAfford(ServerWorld world, UUID playerId, long amount) {
        if (amount <= 0L) {
            return true;
        }
        return getBalance(world, playerId) >= amount;
    }

    public static boolean removeBalance(ServerWorld world, UUID playerId, long amount) {
        if (amount <= 0L) {
            return true;
        }
        if (!canAfford(world, playerId, amount)) {
            return false;
        }
        addBalance(world, playerId, -amount);
        return true;
    }

    public static Text formatBalance(long amount) {
        long clamped = Math.max(0L, amount);
        if (clamped == 0L) {
            return Text.empty()
                    .append(Text.literal("0").formatted(Formatting.GRAY))
                    .append(Text.literal(" ").formatted(Formatting.DARK_GRAY))
                    .append(Text.translatable("text.village-quest.currency.unit.silvermark.other").formatted(Formatting.GRAY));
        }
        long crowns = clamped / CROWN;
        long silvermarks = clamped % CROWN;

        MutableText text = Text.empty();
        boolean appended = false;
        appended = appendUnit(text, appended, crowns,
                "text.village-quest.currency.unit.crown.one",
                "text.village-quest.currency.unit.crown.other",
                Formatting.GOLD);
        appended = appendUnit(text, appended, silvermarks,
                "text.village-quest.currency.unit.silvermark.one",
                "text.village-quest.currency.unit.silvermark.other",
                Formatting.GRAY);

        if (!appended) {
            return Text.empty()
                    .append(Text.literal("0").formatted(Formatting.GRAY))
                    .append(Text.literal(" ").formatted(Formatting.DARK_GRAY))
                    .append(Text.translatable("text.village-quest.currency.unit.silvermark.other").formatted(Formatting.GRAY));
        }
        return text;
    }

    public static Text formatDelta(long amount) {
        long absolute = Math.abs(amount);
        MutableText text = Text.empty();
        if (amount > 0L) {
            text.append(Text.literal("+").formatted(Formatting.GREEN));
        } else if (amount < 0L) {
            text.append(Text.literal("-").formatted(Formatting.RED));
        }
        text.append(formatBalance(absolute));
        return text;
    }

    private static boolean appendUnit(MutableText text,
                                      boolean appended,
                                      long amount,
                                      String singularKey,
                                      String pluralKey,
                                      Formatting color) {
        if (amount <= 0L) {
            return appended;
        }
        if (appended) {
            text.append(Text.literal(" ").formatted(Formatting.DARK_GRAY));
        }
        text.append(Text.literal(Long.toString(amount)).formatted(color));
        text.append(Text.literal(" ").formatted(Formatting.DARK_GRAY));
        text.append(Text.translatable(amount == 1L ? singularKey : pluralKey).formatted(color));
        return true;
    }

    public static CurrencyUnit parseUnit(String value) {
        if (value == null || value.isEmpty()) {
            return CurrencyUnit.SILVERMARK;
        }
        return switch (value.toLowerCase(java.util.Locale.ROOT)) {
            case "silvermark", "silvermarks", "silver", "silberling", "silberlinge", "mark", "marks", "iron_mark", "iron_marks", "sm", "m" -> CurrencyUnit.SILVERMARK;
            case "crown", "crowns", "krone", "kronen", "gold_crown", "gold_crowns", "cr", "c", "k" -> CurrencyUnit.CROWN;
            default -> null;
        };
    }

    public static long toBase(long amount, CurrencyUnit unit) {
        if (amount <= 0L || unit == null) {
            return 0L;
        }
        long factor = unit.value();
        if (amount > Long.MAX_VALUE / factor) {
            return Long.MAX_VALUE;
        }
        return amount * factor;
    }

    public static LegacyCoinConversion convertLegacyCoins(ServerWorld world, ServerPlayerEntity player) {
        if (world == null || player == null) {
            return new LegacyCoinConversion(0, 0, 0, 0L, 0L);
        }

        PlayerInventory inventory = player.getInventory();
        int copperCount = 0;
        int ironCount = 0;
        int goldCount = 0;

        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.isOf(ModItems.KUPFER_GROSCHEN)) {
                copperCount += stack.getCount();
                inventory.setStack(i, ItemStack.EMPTY);
                continue;
            }
            if (stack.isOf(ModItems.EISEN_GROSCHEN)) {
                ironCount += stack.getCount();
                inventory.setStack(i, ItemStack.EMPTY);
                continue;
            }
            if (stack.isOf(ModItems.GOLD_GROSCHEN)) {
                goldCount += stack.getCount();
                inventory.setStack(i, ItemStack.EMPTY);
            }
        }

        long addedAmount = (long) copperCount * LEGACY_COPPER
                + (long) ironCount * LEGACY_IRON
                + (long) goldCount * LEGACY_GOLD;
        long newBalance = getBalance(world, player.getUuid());
        if (addedAmount > 0L) {
            newBalance = addBalance(world, player.getUuid(), addedAmount);
            player.playerScreenHandler.sendContentUpdates();
        }
        return new LegacyCoinConversion(copperCount, ironCount, goldCount, addedAmount, newBalance);
    }

    public static Text formatLegacyCoinBreakdown(int copperCount, int ironCount, int goldCount) {
        MutableText text = Text.empty();
        boolean appended = false;
        appended = appendLegacyCount(text, appended, goldCount, "item.village-quest.gold_groschen", Formatting.GOLD);
        appended = appendLegacyCount(text, appended, ironCount, "item.village-quest.eisen_groschen", Formatting.GRAY);
        appended = appendLegacyCount(text, appended, copperCount, "item.village-quest.kupfer_groschen", Formatting.GRAY);
        if (!appended) {
            return Text.empty()
                    .append(Text.literal("0").formatted(Formatting.GRAY))
                    .append(Text.literal(" ").formatted(Formatting.DARK_GRAY))
                    .append(Text.translatable("item.village-quest.kupfer_groschen").formatted(Formatting.GRAY));
        }
        return text;
    }

    private static boolean appendLegacyCount(MutableText text,
                                             boolean appended,
                                             int count,
                                             String translationKey,
                                             Formatting color) {
        if (count <= 0) {
            return appended;
        }
        if (appended) {
            text.append(Text.literal(", ").formatted(Formatting.DARK_GRAY));
        }
        text.append(Text.literal(Integer.toString(count)).formatted(color));
        text.append(Text.literal(" ").formatted(Formatting.DARK_GRAY));
        text.append(Text.translatable(translationKey).formatted(color));
        return true;
    }

    private static long clampAdd(long current, long delta) {
        if (delta > 0L && current > Long.MAX_VALUE - delta) {
            return Long.MAX_VALUE;
        }
        if (delta < 0L && current < -delta) {
            return 0L;
        }
        return Math.max(0L, current + delta);
    }

    public enum CurrencyUnit {
        SILVERMARK("silvermark", CurrencyService.SILVERMARK),
        CROWN("crown", CurrencyService.CROWN);

        private final String id;
        private final long value;

        CurrencyUnit(String id, long value) {
            this.id = id;
            this.value = value;
        }

        public String id() {
            return id;
        }

        public long value() {
            return value;
        }
    }

    public record LegacyCoinConversion(int copperCount,
                                       int ironCount,
                                       int goldCount,
                                       long addedAmount,
                                       long newBalance) {
        public boolean isEmpty() {
            return addedAmount <= 0L;
        }
    }
}
