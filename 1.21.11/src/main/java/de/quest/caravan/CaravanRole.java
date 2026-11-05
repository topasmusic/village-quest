package de.quest.caravan;

/** Persistent crew assignment; courier is a temporary physical role when needed. */
public enum CaravanRole {
    MASTER, TRADER, GUARD, COURIER;

    public static CaravanRole byId(int id) {
        return id >= 0 && id < values().length ? values()[id] : MASTER;
    }
}
