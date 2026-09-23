package de.quest.shrine;

/** Pure decision table for non-connected Guild Notice Post interactions. */
final class GuildNoticePostTeaser {
    private GuildNoticePostTeaser() {}

    static boolean shouldUseIntroTeaser(boolean welcomeCompleted) {
        return !welcomeCompleted;
    }

    static Stage classify(boolean recognizedVillage,
                          boolean playerHomestead,
                          boolean canChooseFirstFavor,
                          boolean firstFavorActive,
                          boolean firstFavorCompleted,
                          boolean welcomeActive,
                          boolean welcomeCompleted,
                          boolean currentWelcomeVillage) {
        if (recognizedVillage) {
            if (canChooseFirstFavor) return Stage.FIRST_FAVOR_AVAILABLE;
            if (firstFavorActive) return Stage.FIRST_FAVOR_ACTIVE;
            if (firstFavorCompleted && !welcomeCompleted) {
                if (!welcomeActive) return Stage.MEET_VILLAGERS;
                return currentWelcomeVillage ? Stage.GREETING_PROGRESS : Stage.WELCOME_ELSEWHERE;
            }
            if (welcomeCompleted) return Stage.CONNECT_VILLAGE;
        }
        if (playerHomestead) return Stage.HOMESTEAD;
        return Stage.UNKNOWN;
    }

    enum Stage {
        FIRST_FAVOR_AVAILABLE,
        FIRST_FAVOR_ACTIVE,
        MEET_VILLAGERS,
        GREETING_PROGRESS,
        WELCOME_ELSEWHERE,
        CONNECT_VILLAGE,
        HOMESTEAD,
        UNKNOWN
    }
}
