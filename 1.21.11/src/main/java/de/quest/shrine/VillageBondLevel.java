package de.quest.shrine;

import net.minecraft.network.chat.Component;

public enum VillageBondLevel {
    KNOWN(0, "known"),
    TRUSTED(1, "trusted"),
    ALLIED(2, "allied");

    private final int id;
    private final String key;

    VillageBondLevel(int id, String key) {
        this.id = id;
        this.key = key;
    }

    public int id() { return id; }
    public String key() { return key; }
    public Component label() { return Component.translatable("text.village-quest.village_bond.level." + key); }

    public static VillageBondLevel byId(int id) {
        VillageBondLevel result = KNOWN;
        for (VillageBondLevel value : values()) {
            if (id >= value.id) result = value;
        }
        return result;
    }
}
