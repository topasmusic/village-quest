package de.quest.quest;

import java.util.Arrays;

public final class DistinctObjectiveProgress {
    private DistinctObjectiveProgress() {}

    public static int[] record(int[] storedSlots, int token) {
        int[] slots = storedSlots == null ? new int[0] : Arrays.copyOf(storedSlots, storedSlots.length);
        int normalized = token == 0 ? 1 : token;
        for (int slot : slots) {
            if (slot == normalized) {
                return slots;
            }
        }
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == 0) {
                slots[i] = normalized;
                break;
            }
        }
        return slots;
    }

    public static int count(int[] slots) {
        if (slots == null) {
            return 0;
        }
        int count = 0;
        for (int slot : slots) {
            if (slot != 0) {
                count++;
            }
        }
        return count;
    }
}
