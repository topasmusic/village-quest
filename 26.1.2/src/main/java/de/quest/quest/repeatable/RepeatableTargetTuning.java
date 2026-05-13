package de.quest.quest.repeatable;

public final class RepeatableTargetTuning {
    private RepeatableTargetTuning() {}

    public static int adjust(int baseTarget, RepeatableTargetProfile profile, String salt) {
        if (baseTarget <= 1) {
            return Math.max(1, baseTarget);
        }
        RepeatableTargetProfile effectiveProfile = profile == null ? RepeatableTargetProfile.NORMAL : profile;
        double factor = switch (effectiveProfile) {
            case LIGHT -> 0.88d;
            case NORMAL -> 0.97d;
            case HEAVY -> 1.12d;
        };
        int adjusted = Math.max(1, (int) Math.round(baseTarget * factor));
        int saltOffset = saltedOffset(salt, effectiveProfile);
        int minimum = Math.max(1, baseTarget - profileVariance(baseTarget));
        int maximum = Math.max(minimum, baseTarget + profileVariance(baseTarget));
        adjusted = Math.max(minimum, Math.min(maximum, adjusted + saltOffset));
        if (adjusted == baseTarget) {
            adjusted = Math.max(minimum, Math.min(maximum, adjusted + fallbackOffset(effectiveProfile)));
        }
        return avoidRoundNumbers(baseTarget, adjusted, minimum, maximum, salt);
    }

    private static int profileVariance(int baseTarget) {
        if (baseTarget >= 96) {
            return 9;
        }
        if (baseTarget >= 48) {
            return 6;
        }
        if (baseTarget >= 24) {
            return 4;
        }
        if (baseTarget >= 10) {
            return 3;
        }
        return 2;
    }

    private static int saltedOffset(String salt, RepeatableTargetProfile profile) {
        int hash = Math.floorMod(salt == null ? 0 : salt.hashCode(), 5);
        return switch (profile) {
            case LIGHT -> switch (hash) {
                case 0 -> -1;
                case 1 -> 0;
                case 2 -> -2;
                case 3 -> -1;
                default -> 0;
            };
            case NORMAL -> switch (hash) {
                case 0 -> -1;
                case 1 -> 1;
                case 2 -> 0;
                case 3 -> 2;
                default -> -2;
            };
            case HEAVY -> switch (hash) {
                case 0 -> 1;
                case 1 -> 2;
                case 2 -> 0;
                case 3 -> 3;
                default -> 1;
            };
        };
    }

    private static int fallbackOffset(RepeatableTargetProfile profile) {
        return switch (profile) {
            case LIGHT -> -1;
            case NORMAL -> 1;
            case HEAVY -> 2;
        };
    }

    private static int avoidRoundNumbers(int baseTarget, int adjusted, int minimum, int maximum, String salt) {
        if (!isRoundish(adjusted)) {
            return adjusted;
        }
        int direction = Math.floorMod(salt == null ? 0 : salt.hashCode(), 2) == 0 ? 1 : -1;
        int candidate = adjusted + direction;
        if (candidate >= minimum && candidate <= maximum && !isRoundish(candidate)) {
            return candidate;
        }
        candidate = adjusted - direction;
        if (candidate >= minimum && candidate <= maximum && !isRoundish(candidate)) {
            return candidate;
        }
        if (adjusted == baseTarget && adjusted + 2 <= maximum) {
            return adjusted + 2;
        }
        if (adjusted == baseTarget && adjusted - 2 >= minimum) {
            return adjusted - 2;
        }
        return adjusted;
    }

    private static boolean isRoundish(int value) {
        return value > 8 && (value % 64 == 0 || value % 32 == 0 || value % 16 == 0 || value % 8 == 0 || value % 10 == 0);
    }
}
