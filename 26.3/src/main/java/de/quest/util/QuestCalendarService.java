package de.quest.util;

import de.quest.config.VillageQuestServerConfig;
import de.quest.data.QuestState;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

/** Bound only for the active server lifecycle, before party restoration. Never used by client code. */
public final class QuestCalendarService {
    private static MinecraftServer server;
    private QuestCalendarService() {}
    public static void start(MinecraftServer active) { server = active; sample(); }
    public static void stop() { server = null; }
    private static QuestCalendar sample() {
        if (server == null) return null;
        var state = QuestState.get(server);
        var calendar = state.questCalendar();
        if (calendar.update(minecraft(), server.overworld().getOverworldClockTime(), TimeUtil.currentDay(), TimeUtil.currentWeekCycle())) state.setDirty();
        return calendar;
    }
    public static boolean minecraft() { return VillageQuestServerConfig.get().minecraftQuestCalendar(); }
    public static long currentDay() { var calendar = sample(); return calendar == null ? TimeUtil.currentDay() : calendar.day(); }
    public static long currentWeekCycle() { var calendar = sample(); return calendar == null ? TimeUtil.currentWeekCycle() : calendar.week(); }
    public static Component remaining(boolean weekly) {
        var calendar = sample();
        if (minecraft() && calendar != null) {
            long ticks = calendar.remainingTicks(weekly);
            return Component.translatable("screen.village-quest.questmaster.minecraft_reset",
                    String.format(java.util.Locale.ROOT, "%.2f", Math.ceil(ticks / 240.0) / 100.0));
        }
        return Component.literal(TimeUtil.compactResetDuration(weekly ? TimeUtil.millisUntilNextWeeklyReset() : TimeUtil.millisUntilNextDailyReset()));
    }
}
