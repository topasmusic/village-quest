package de.quest.guild;

import net.minecraft.network.chat.Component;

public enum VillageGuildRole {
    MEMBER(0, "member"),
    STEWARD(1, "steward"),
    LEADER(2, "leader");

    private final int id;
    private final String key;

    VillageGuildRole(int id, String key) { this.id = id; this.key = key; }
    public int id() { return id; }
    public String key() { return key; }
    public boolean canInvite() { return this == STEWARD || this == LEADER; }
    public boolean canChooseProject() { return this == STEWARD || this == LEADER; }
    public boolean canPromote() { return this == LEADER; }
    public Component label() { return Component.translatable("text.village-quest.guild.role." + key); }
    public static VillageGuildRole byId(int id) {
        for (VillageGuildRole value : values()) if (value.id == id) return value;
        return MEMBER;
    }
}
