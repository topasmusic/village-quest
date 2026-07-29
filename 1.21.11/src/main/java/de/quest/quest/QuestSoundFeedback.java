package de.quest.quest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

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

    public static void playProgressChange(ServerWorld world,
                                          ServerPlayerEntity player,
                                          List<Text> beforeLines,
                                          List<Text> afterLines) {
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

    public static void playReady(ServerWorld world, ServerPlayerEntity player) {
        play(world, player, FeedbackTier.STAGE);
    }

    public static void playAccepted(ServerWorld world, ServerPlayerEntity player) {
        play(world, player, FeedbackTier.ACCEPTED);
    }

    public static void playNewOffer(ServerWorld world, ServerPlayerEntity player) {
        play(world, player, FeedbackTier.AVAILABILITY);
    }

    private static void play(ServerWorld world, ServerPlayerEntity player, FeedbackTier tier) {
        if (world == null || player == null || tier == null) {
            return;
        }

        long now = world.getTime();
        LastFeedback previous = LAST_FEEDBACK.get(player.getUuid());
        if (previous != null
                && now - previous.gameTick() < tier.cooldownTicks()
                && tier.priority() <= previous.tier().priority()) {
            return;
        }
        LAST_FEEDBACK.put(player.getUuid(), new LastFeedback(now, tier));

        switch (tier) {
            case PROGRESS -> world.playSound(
                    null, player.getBlockPos(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP,
                    SoundCategory.PLAYERS, 0.12f, 1.45f
            );
            case OBJECTIVE -> world.playSound(
                    null, player.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,
                    SoundCategory.PLAYERS, 0.24f, 1.35f
            );
            case ACCEPTED -> world.playSound(
                    null, player.getBlockPos(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP,
                    SoundCategory.PLAYERS, 0.24f, 1.0f
            );
            case STAGE -> {
                world.playSound(
                        null, player.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,
                        SoundCategory.PLAYERS, 0.30f, 1.15f
                );
                world.playSound(
                        null, player.getBlockPos(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP,
                        SoundCategory.PLAYERS, 0.16f, 1.80f
                );
            }
            case AVAILABILITY -> world.playSound(
                    null, player.getBlockPos(), SoundEvents.ENTITY_VILLAGER_YES,
                    SoundCategory.PLAYERS, 0.28f, 1.08f
            );
        }
    }

    private static String flatten(List<Text> lines) {
        StringBuilder text = new StringBuilder();
        for (Text line : lines) {
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
