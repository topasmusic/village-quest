package de.quest.quest;

import de.quest.config.ClientPreferenceService;
import de.quest.network.Payloads;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/**
 * Keeps quest feedback on one restrained audio ladder:
 * soft progress, a clearer completed objective, a short two-note stage chime,
 * and the existing level-up sound reserved for the final quest reward.
 */
public final class QuestSoundFeedback {
    private static final Pattern NUMBER = Pattern.compile("\\d+");
    private static final Pattern PROGRESS_PAIR = Pattern.compile("(\\d+)\\s*/\\s*(\\d+)");
    private static final Map<UUID, LastFeedback> LAST_FEEDBACK = new HashMap<>();

    private QuestSoundFeedback() {
    }

    public static void playProgressChange(ServerLevel world,
                                          ServerPlayer player,
                                          List<Component> beforeLines,
                                          List<Component> afterLines) {
        if (world == null || player == null || beforeLines == null || afterLines == null
                || beforeLines.isEmpty() || afterLines.isEmpty()) {
            return;
        }

        String before = flatten(beforeLines);
        String after = flatten(afterLines);
        if (before.equals(after)) {
            return;
        }

        FeedbackTier tier;
        if (!shape(before).equals(shape(after))) {
            tier = FeedbackTier.STAGE;
        } else if (newlyCompletedObjective(before, after)) {
            tier = FeedbackTier.OBJECTIVE;
        } else {
            tier = FeedbackTier.PROGRESS;
        }
        play(world, player, tier);
    }

    public static void playReady(ServerLevel world, ServerPlayer player) {
        play(world, player, FeedbackTier.STAGE);
    }

    public static void playAccepted(ServerLevel world, ServerPlayer player) {
        play(world, player, FeedbackTier.ACCEPTED);
    }

    public static void playNewOffer(ServerLevel world, ServerPlayer player) {
        play(world, player, FeedbackTier.AVAILABILITY);
    }

    private static void play(ServerLevel world, ServerPlayer player, FeedbackTier tier) {
        if (world == null || player == null || tier == null) {
            return;
        }

        long now = world.getGameTime();
        LastFeedback previous = LAST_FEEDBACK.get(player.getUUID());
        if (previous != null
                && now - previous.gameTick() < tier.cooldownTicks()
                && tier.priority() <= previous.tier().priority()) {
            return;
        }
        LAST_FEEDBACK.put(player.getUUID(), new LastFeedback(now, tier));
        if (ClientPreferenceService.questProgressSounds(player)) {
            ServerPlayNetworking.send(player, new Payloads.QuestFeedbackPayload(tier.ordinal()));
        }
    }

    private static String flatten(List<Component> lines) {
        StringBuilder text = new StringBuilder();
        for (Component line : lines) {
            if (line == null) {
                continue;
            }
            if (!text.isEmpty()) {
                text.append('\n');
            }
            text.append(line.getString());
        }
        return text.toString();
    }

    private static String shape(String text) {
        return NUMBER.matcher(text).replaceAll("#");
    }

    private static boolean newlyCompletedObjective(String before, String after) {
        List<ProgressPair> beforePairs = progressPairs(before);
        List<ProgressPair> afterPairs = progressPairs(after);
        int pairCount = Math.min(beforePairs.size(), afterPairs.size());
        for (int index = 0; index < pairCount; index++) {
            ProgressPair oldPair = beforePairs.get(index);
            ProgressPair newPair = afterPairs.get(index);
            if (oldPair.current() < oldPair.target() && newPair.current() >= newPair.target()) {
                return true;
            }
        }
        return false;
    }

    private static List<ProgressPair> progressPairs(String text) {
        List<ProgressPair> pairs = new ArrayList<>();
        Matcher matcher = PROGRESS_PAIR.matcher(text);
        while (matcher.find()) {
            try {
                pairs.add(new ProgressPair(
                        Integer.parseInt(matcher.group(1)),
                        Integer.parseInt(matcher.group(2))
                ));
            } catch (NumberFormatException ignored) {
                // Extremely large or malformed display numbers are not quest progress.
            }
        }
        return pairs;
    }

    private enum FeedbackTier {
        PROGRESS(1, 6),
        OBJECTIVE(2, 3),
        ACCEPTED(2, 3),
        STAGE(3, 3),
        AVAILABILITY(2, 10);

        private final int priority;
        private final int cooldownTicks;

        FeedbackTier(int priority, int cooldownTicks) {
            this.priority = priority;
            this.cooldownTicks = cooldownTicks;
        }

        private int priority() {
            return priority;
        }

        private int cooldownTicks() {
            return cooldownTicks;
        }
    }

    private record ProgressPair(int current, int target) {
    }

    private record LastFeedback(long gameTick, FeedbackTier tier) {
    }
}
