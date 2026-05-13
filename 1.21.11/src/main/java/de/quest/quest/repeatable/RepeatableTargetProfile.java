package de.quest.quest.repeatable;

import net.minecraft.util.math.random.Random;

public enum RepeatableTargetProfile {
    LIGHT(0),
    NORMAL(1),
    HEAVY(2);

    private final int id;

    RepeatableTargetProfile(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }

    public static RepeatableTargetProfile random(Random random) {
        if (random == null) {
            return NORMAL;
        }
        return switch (random.nextInt(3)) {
            case 0 -> LIGHT;
            case 2 -> HEAVY;
            default -> NORMAL;
        };
    }

    public static RepeatableTargetProfile byId(int id) {
        return switch (id) {
            case 0 -> LIGHT;
            case 2 -> HEAVY;
            default -> NORMAL;
        };
    }
}
