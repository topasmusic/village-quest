package de.quest.util;

import net.minecraft.nbt.CompoundTag;

/** Persistent quest-only cycle IDs. Switching clocks never reopens a rewarded cycle. */
public final class QuestCalendar {
    public static final long DAY_TICKS = 24000;
    private boolean initialized;
    private boolean minecraft;
    private long day, week, lastTime, dailyTicks, weeklyTicks, realDay, realWeek;

    public boolean update(boolean useMinecraft, long time, long today, long thisWeek) {
        if (!initialized) {
            initialized = true;
            minecraft = useMinecraft;
            day = today;
            week = thisWeek;
            lastTime = time;
            realDay = today;
            realWeek = thisWeek;
            return true;
        }
        if (minecraft != useMinecraft) {
            minecraft = useMinecraft;
            lastTime = time;
            realDay = today;
            realWeek = thisWeek;
            dailyTicks = weeklyTicks = 0;
            return true;
        }
        if (minecraft) {
            // /time set backwards rebases the clock without undoing earned cycles.
            long delta = time >= lastTime ? time - lastTime : 0;
            if (delta == 0 && time == lastTime) return false;
            lastTime = time;
            dailyTicks += delta;
            weeklyTicks += delta;
            day += dailyTicks / DAY_TICKS;
            week += 7 * (weeklyTicks / (7 * DAY_TICKS));
            dailyTicks %= DAY_TICKS;
            weeklyTicks %= 7 * DAY_TICKS;
            return true;
        }
        // Keep high-water marks: a wall-clock rollback cannot reuse old reward IDs.
        long days = Math.max(0, today - realDay);
        long weeks = Math.max(0, thisWeek - realWeek);
        if (days == 0 && weeks == 0) return false;
        day += days;
        week += weeks;
        realDay = Math.max(realDay, today);
        realWeek = Math.max(realWeek, thisWeek);
        return true;
    }

    public long day() { return day; }
    public long week() { return week; }
    public long remainingTicks(boolean weekly) { return weekly ? 7 * DAY_TICKS - weeklyTicks : DAY_TICKS - dailyTicks; }

    public CompoundTag toNbt() {
        var tag = new CompoundTag();
        tag.putBoolean("initialized", initialized);
        tag.putBoolean("minecraft", minecraft);
        tag.putLong("day", day); tag.putLong("week", week);
        tag.putLong("lastTime", lastTime);
        tag.putLong("dailyTicks", dailyTicks); tag.putLong("weeklyTicks", weeklyTicks);
        tag.putLong("realDay", realDay); tag.putLong("realWeek", realWeek);
        return tag;
    }

    public static QuestCalendar fromNbt(CompoundTag tag) {
        var result = new QuestCalendar();
        result.initialized = tag.getBooleanOr("initialized", false);
        result.minecraft = tag.getBooleanOr("minecraft", false);
        result.day = tag.getLongOr("day", 0); result.week = tag.getLongOr("week", 0);
        result.lastTime = tag.getLongOr("lastTime", 0);
        result.dailyTicks = Math.floorMod(tag.getLongOr("dailyTicks", 0), DAY_TICKS);
        result.weeklyTicks = Math.floorMod(tag.getLongOr("weeklyTicks", 0), 7 * DAY_TICKS);
        result.realDay = tag.getLongOr("realDay", 0); result.realWeek = tag.getLongOr("realWeek", 0);
        return result;
    }
}
