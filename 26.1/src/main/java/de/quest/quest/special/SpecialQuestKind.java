package de.quest.quest.special;

public enum SpecialQuestKind {
    SHARD_RELIC("shard_relic"),
    MERCHANT_SEAL("merchant_seal"),
    SHEPHERD_FLUTE("shepherd_flute"),
    APIARIST_SMOKER("apiarist_smoker"),
    SURVEYOR_COMPASS("surveyor_compass");

    private final String id;

    SpecialQuestKind(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static SpecialQuestKind fromId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        for (SpecialQuestKind kind : values()) {
            if (kind.id.equalsIgnoreCase(raw)) {
                return kind;
            }
        }
        return null;
    }
}
