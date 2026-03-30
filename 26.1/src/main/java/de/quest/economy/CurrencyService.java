package de.quest.economy;

import de.quest.data.PlayerQuestData;
import de.quest.data.QuestState;
import de.quest.registry.ModItems;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public final class CurrencyService {
    public static final long SILVERMARK = 1L;
    public static final long CROWN = 10L;
    private static final long LEGACY_COPPER = 1L;
    private static final long LEGACY_IRON = SILVERMARK;
    private static final long LEGACY_GOLD = CROWN;

    private CurrencyService() {}

    public static long getBalance(ServerLevel world, UUID playerId) {
        if (world == null || playerId == null) {
            return 0L;
        }
        return QuestState.get(world.getServer()).getPlayerData(playerId).getCurrencyBalance();
    }

    public static long addBalance(ServerLevel world, UUID playerId, long amount) {
        if (world == null || playerId == null || amount == 0L) {
            return getBalance(world, playerId);
        }

        PlayerQuestData data = QuestState.get(world.getServer()).getPlayerData(playerId);
        long next = clampAdd(data.getCurrencyBalance(), amount);
        if (next != data.getCurrencyBalance()) {
            data.setCurrencyBalance(next);
            QuestState.get(world.getServer()).setDirty();
        }
        return next;
    }

    public static long setBalance(ServerLevel world, UUID playerId, long amount) {
        if (world == null || playerId == null) {
            return 0L;
        }

        PlayerQuestData data = QuestState.get(world.getServer()).getPlayerData(playerId);
        long next = Math.max(0L, amount);
        if (next != data.getCurrencyBalance()) {
            data.setCurrencyBalance(next);
            QuestState.get(world.getServer()).setDirty();
        }
        return next;
    }

    public static boolean canAfford(ServerLevel world, UUID playerId, long amount) {
        if (amount <= 0L) {
            return true;
        }
        return getBalance(world, playerId) >= amount;
    }

    public static boolean removeBalance(ServerLevel world, UUID playerId, long amount) {
        if (amount <= 0L) {
            return true;
        }
        if (!canAfford(world, playerId, amount)) {
            return false;
        }
        addBalance(world, playerId, -amount);
        return true;
    }

    public static Component formatBalance(long amount) {
        long clamped = Math.max(0L, amount);
        if (clamped == 0L) {
            return Component.empty()
                    .append(Component.literal("0").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(" ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.translatable("text.village-quest.currency.unit.silvermark.other").withStyle(ChatFormatting.GRAY));
        }
        long crowns = clamped / CROWN;
        long silvermarks = clamped % CROWN;

        MutableComponent text = Component.empty();
        boolean appended = false;
        appended = appendUnit(text, appended, crowns,
                "text.village-quest.currency.unit.crown.one",
                "text.village-quest.currency.unit.crown.other",
                ChatFormatting.GOLD);
        appended = appendUnit(text, appended, silvermarks,
                "text.village-quest.currency.unit.silvermark.one",
                "text.village-quest.currency.unit.silvermark.other",
                ChatFormatting.GRAY);

        if (!appended) {
            return Component.empty()
                    .append(Component.literal("0").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(" ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.translatable("text.village-quest.currency.unit.silvermark.other").withStyle(ChatFormatting.GRAY));
        }
        return text;
    }

    public static Component formatDelta(long amount) {
        long absolute = Math.abs(amount);
        MutableComponent text = Component.empty();
        if (amount > 0L) {
            text.append(Component.literal("+").withStyle(ChatFormatting.GREEN));
        } else if (amount < 0L) {
            text.append(Component.literal("-").withStyle(ChatFormatting.RED));
        }
        text.append(formatBalance(absolute));
        return text;
    }

    private static boolean appendUnit(MutableComponent text,
                                      boolean appended,
                                      long amount,
                                      String singularKey,
                                      String pluralKey,
                                      ChatFormatting color) {
        if (amount <= 0L) {
            return appended;
        }
        if (appended) {
            text.append(Component.literal(" ").withStyle(ChatFormatting.DARK_GRAY));
        }
        text.append(Component.literal(Long.toString(amount)).withStyle(color));
        text.append(Component.literal(" ").withStyle(ChatFormatting.DARK_GRAY));
        text.append(Component.translatable(amount == 1L ? singularKey : pluralKey).withStyle(color));
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

    public static LegacyCoinConversion convertLegacyCoins(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null) {
            return new LegacyCoinConversion(0, 0, 0, 0L, 0L);
        }

        Inventory inventory = player.getInventory();
        int copperCount = 0;
        int ironCount = 0;
        int goldCount = 0;

        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.is(ModItems.KUPFER_GROSCHEN)) {
                copperCount += stack.getCount();
                inventory.setItem(i, ItemStack.EMPTY);
                continue;
            }
            if (stack.is(ModItems.EISEN_GROSCHEN)) {
                ironCount += stack.getCount();
                inventory.setItem(i, ItemStack.EMPTY);
                continue;
            }
            if (stack.is(ModItems.GOLD_GROSCHEN)) {
                goldCount += stack.getCount();
                inventory.setItem(i, ItemStack.EMPTY);
            }
        }

        long addedAmount = (long) copperCount * LEGACY_COPPER
                + (long) ironCount * LEGACY_IRON
                + (long) goldCount * LEGACY_GOLD;
        long newBalance = getBalance(world, player.getUUID());
        if (addedAmount > 0L) {
            newBalance = addBalance(world, player.getUUID(), addedAmount);
            player.inventoryMenu.broadcastChanges();
        }
        return new LegacyCoinConversion(copperCount, ironCount, goldCount, addedAmount, newBalance);
    }

    public static Component formatLegacyCoinBreakdown(int copperCount, int ironCount, int goldCount) {
        MutableComponent text = Component.empty();
        boolean appended = false;
        appended = appendLegacyCount(text, appended, goldCount, "item.village-quest.gold_groschen", ChatFormatting.GOLD);
        appended = appendLegacyCount(text, appended, ironCount, "item.village-quest.eisen_groschen", ChatFormatting.GRAY);
        appended = appendLegacyCount(text, appended, copperCount, "item.village-quest.kupfer_groschen", ChatFormatting.GRAY);
        if (!appended) {
            return Component.empty()
                    .append(Component.literal("0").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(" ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.translatable("item.village-quest.kupfer_groschen").withStyle(ChatFormatting.GRAY));
        }
        return text;
    }

    private static boolean appendLegacyCount(MutableComponent text,
                                             boolean appended,
                                             int count,
                                             String translationKey,
                                             ChatFormatting color) {
        if (count <= 0) {
            return appended;
        }
        if (appended) {
            text.append(Component.literal(", ").withStyle(ChatFormatting.DARK_GRAY));
        }
        text.append(Component.literal(Integer.toString(count)).withStyle(color));
        text.append(Component.literal(" ").withStyle(ChatFormatting.DARK_GRAY));
        text.append(Component.translatable(translationKey).withStyle(color));
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
