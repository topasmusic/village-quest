package de.quest.client.config;

import com.mojang.serialization.JsonOps;
import de.quest.VillageQuest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.world.level.storage.LevelResource;

/** Personal presentation only. A world/player-scoped pin never changes quest data. */
public final class QuestTrackerFocus {
    private static final Set<String> QUESTS = Set.of("daily", "weekly", "story", "special", "pilgrim");
    private static String scope = "";
    private static QuestTrackerFocus current = new QuestTrackerFocus(null);
    private final Path path;
    private String quest = "", title = "";
    private boolean onlyPinned;

    QuestTrackerFocus(Path path) { this.path = path; }

    public static QuestTrackerFocus current() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) return current;
        String world;
        if (client.getCurrentServer() != null) world = "server:" + client.getCurrentServer().ip;
        else if (client.getSingleplayerServer() != null)
            world = "world:" + client.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        else return current;
        String next = world + ":player:" + client.player.getUUID();
        if (!next.equals(scope)) {
            scope = next;
            Path file = FabricLoader.getInstance().getConfigDir().resolve("village-quest/tracker-focus")
                    .resolve(digest(next) + ".properties");
            current = load(file);
        }
        return current;
    }

    static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static String identity(Component title) {
        return title == null ? "" : ComponentSerialization.CODEC.encodeStart(JsonOps.INSTANCE, title)
                .result().map(Object::toString).filter(s -> s.length() <= 4096).orElse("");
    }

    public String quest() { return quest; }
    public boolean onlyPinned() { return onlyPinned; }
    public boolean matches(String id, Component value) { return quest.equals(id) && (id.contains(":") || title.equals(identity(value))); }

    public void toggle(String id, Component value) {
        if (!de.quest.quest.TrackerEntry.validId(id)) return;
        if (matches(id, value)) { quest = ""; title = ""; }
        else {
            String identity = identity(value);
            if (identity.isEmpty()) return;
            quest = id; title = identity;
        }
        save();
    }

    public void toggleOnlyPinned() { onlyPinned = !onlyPinned; save(); }

    /** Called only with a complete active-quest snapshot, never a disabled HUD packet. */
    public void reconcile(Map<String, Component> active) {
        if(quest.isEmpty()) return;
        if(active.containsKey(quest) && matches(quest,active.get(quest))) return;
        if(!quest.contains(":")) {
            String category=quest;
            var matches=active.entrySet().stream().filter(e -> e.getKey().startsWith(category+":")
                    && title.equals(identity(e.getValue()))).toList();
            if(matches.size()==1) { quest=matches.getFirst().getKey(); title=""; save(); return; }
        }
        quest=""; title=""; save();
    }

    static QuestTrackerFocus load(Path path) {
        var focus = new QuestTrackerFocus(path);
        if (!Files.isRegularFile(path)) return focus;
        try (var input = Files.newInputStream(path)) {
            var values = new Properties(); values.load(input);
            String id = values.getProperty("quest", ""), title = values.getProperty("title", "");
            if (de.quest.quest.TrackerEntry.validId(id) && (id.contains(":") || !title.isEmpty() && title.length() <= 4096)) {
                focus.quest = id; focus.title = title;
            }
            focus.onlyPinned = Boolean.parseBoolean(values.getProperty("only_pinned", "false"));
        } catch (IOException failure) { VillageQuest.LOGGER.warn("Could not read quest tracker focus", failure); }
        return focus;
    }

    private void save() {
        if (path == null) return;
        var values = new Properties(); values.setProperty("quest", quest); values.setProperty("title", title);
        values.setProperty("only_pinned", Boolean.toString(onlyPinned));
        try {
            Files.createDirectories(path.getParent());
            try (var output = Files.newOutputStream(path)) { values.store(output, "Village Quest personal tracker focus"); }
        } catch (IOException failure) { VillageQuest.LOGGER.warn("Could not save quest tracker focus", failure); }
    }
}
