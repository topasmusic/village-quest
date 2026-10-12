package de.quest.client.screen;

import de.quest.network.VillageNetworkPayloads.NoticeJourneyPayload;
import de.quest.network.VillageNetworkPayloads.NoticeJourneyActionPayload;
import java.util.ArrayList;
import java.util.List;

/** One source of geometry for drawing and clicking the village story actions. */
final class NoticeJourneyLayout {
    static final int WIDTH = 360, HEIGHT = 186;
    static final int CLOSE = -1, TRUST = -2, REQUESTS = -3, QUESTMASTER = -4;
    record Button(int x, int y, int width, int height, String key, int action, boolean primary) {
        boolean contains(double mx, double my) { return mx >= x && mx < x + width && my >= y && my < y + height; }
    }
    static List<Button> buttons(int stage, boolean requests) {
        var result = new ArrayList<Button>();
        var footer = new ArrayList<Button>();
        if (stage == NoticeJourneyPayload.CHOOSE) {
            result.add(new Button(43, 142, 134, 14, "screen.village-quest.notice_journey.reserve", NoticeJourneyActionPayload.RESERVE, true));
            result.add(new Button(183, 142, 134, 14, "screen.village-quest.notice_journey.share", NoticeJourneyActionPayload.SHARE, true));
        } else {
            String key; int action;
            switch (stage) {
                case NoticeJourneyPayload.AVAILABLE -> { key = "screen.village-quest.notice_journey.accept"; action = NoticeJourneyActionPayload.ACCEPT; }
                case NoticeJourneyPayload.READY -> { key = "screen.village-quest.notice_journey.deliver"; action = NoticeJourneyActionPayload.DELIVER; }
                case NoticeJourneyPayload.AFTERSTORY_AVAILABLE -> { key = "screen.village-quest.afterstory.accept"; action = NoticeJourneyActionPayload.ACCEPT_AFTERSTORY; }
                case NoticeJourneyPayload.AFTERSTORY_READY -> { key = "screen.village-quest.afterstory.deliver"; action = NoticeJourneyActionPayload.DELIVER_AFTERSTORY; }
                case NoticeJourneyPayload.QUESTMASTER -> { key = "screen.village-quest.notice_journey.questmaster"; action = QUESTMASTER; }
                case NoticeJourneyPayload.PAUSED -> { key = "screen.village-quest.notice_journey.resume"; action = NoticeJourneyActionPayload.RESUME; }
                default -> { key = "screen.village-quest.notice_journey.refresh"; action = NoticeJourneyActionPayload.REFRESH; }
            }
            footer.add(new Button(0, 0, 0, 0, key, action, true));
        }
        if (requests) footer.add(new Button(0, 0, 0, 0, "screen.village-quest.notice_journey.requests", REQUESTS, false));
        footer.add(new Button(0, 0, 0, 0, "screen.village-quest.dispatch.tab", NoticeJourneyActionPayload.DISPATCH, false));
        footer.add(new Button(0, 0, 0, 0, "screen.village-quest.reputation.title", TRUST, false));
        footer.add(new Button(0, 0, 0, 0, "screen.village-quest.notice_board.close", CLOSE, false));
        int gap = 5;
        int[] widths = stage == NoticeJourneyPayload.CHOOSE
                ? (requests ? new int[] {80, 115, 57, 57} : new int[] {148, 83, 83})
                : (requests ? new int[] {90, 80, 58, 38, 38} : new int[] {115, 98, 48, 48});
        int x = 18;
        for (int i = 0; i < footer.size(); i++) {
            var button = footer.get(i);
            result.add(new Button(x, 162, widths[i], 14, button.key(), button.action(), button.primary()));
            x += widths[i] + gap;
        }
        return List.copyOf(result);
    }
}
