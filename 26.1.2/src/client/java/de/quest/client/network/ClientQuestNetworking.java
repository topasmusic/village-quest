package de.quest.client.network;

import de.quest.client.hud.QuestTrackerHud;
import de.quest.client.screen.AdminJournalScreen;
import de.quest.client.screen.JournalScreen;
import de.quest.client.screen.PilgrimTradeScreen;
import de.quest.client.screen.QuestMasterScreen;
import de.quest.network.Payloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.util.ArrayList;
import java.util.List;

public final class ClientQuestNetworking {
    private ClientQuestNetworking() {}

    public static void register() {
        Payloads.register();

        ClientPlayNetworking.registerGlobalReceiver(Payloads.JournalPayload.ID, (payload, context) -> {
            var client = context.client();
            client.execute(() -> {
                if (payload.action() == Payloads.JournalPayload.ACTION_CLOSE) {
                    if (client.screen instanceof JournalScreen) {
                        client.setScreen(null);
                    }
                    return;
                }

                JournalScreen.JournalData data = new JournalScreen.JournalData(
                        payload.total(),
                        payload.discovered(),
                        payload.completed(),
                        payload.active(),
                        payload.currencyBalance(),
                        payload.farmingReputation(),
                        payload.craftingReputation(),
                        payload.animalReputation(),
                        payload.tradeReputation(),
                        payload.monsterReputation(),
                        payload.hasStarreachRing(),
                        payload.hasMerchantSeal(),
                        payload.hasShepherdFlute(),
                        payload.hasApiaristSmoker(),
                        payload.hasSurveyorCompass(),
                        payload.dailyActive(),
                        payload.dailyTitle(),
                        payload.dailyProgress(),
                        payload.weeklyActive(),
                        payload.weeklyTitle(),
                        payload.weeklyProgress(),
                        payload.storyActive(),
                        payload.storyTitle(),
                        payload.storyProgress(),
                        payload.pilgrimActive(),
                        payload.pilgrimTitle(),
                        payload.pilgrimProgress(),
                        payload.specialActive(),
                        payload.specialTitle(),
                        payload.specialProgress(),
                        payload.hasVillageLedgerProject(),
                        payload.hasApiaryCharterProject(),
                        payload.hasForgeCharterProject(),
                        payload.hasMarketCharterProject(),
                        payload.hasPastureCharterProject(),
                        payload.hasWatchBellProject()
                );

                if (payload.action() == Payloads.JournalPayload.ACTION_OPEN) {
                    client.setScreen(new JournalScreen(data));
                    return;
                }

                if (client.screen instanceof JournalScreen screen) {
                    screen.updateData(data);
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(Payloads.AdminJournalPayload.ID, (payload, context) -> {
            var client = context.client();
            client.execute(() -> client.setScreen(new AdminJournalScreen(payload.lines())));
        });

        ClientPlayNetworking.registerGlobalReceiver(Payloads.QuestTrackerPayload.ID, (payload, context) -> {
            var client = context.client();
            client.execute(() -> QuestTrackerHud.update(new QuestTrackerHud.TrackerState(
                    payload.enabled(),
                    payload.dailyActive(),
                    payload.dailyTitle(),
                    payload.dailyLines(),
                    payload.weeklyActive(),
                    payload.weeklyTitle(),
                    payload.weeklyLines(),
                    payload.storyActive(),
                    payload.storyTitle(),
                    payload.storyLines(),
                    payload.pilgrimActive(),
                    payload.pilgrimTitle(),
                    payload.pilgrimLines(),
                    payload.specialActive(),
                    payload.specialTitle(),
                    payload.specialLines()
            )));
        });

        ClientPlayNetworking.registerGlobalReceiver(Payloads.PilgrimTradePayload.ID, (payload, context) -> {
            var client = context.client();
            client.execute(() -> {
                if (payload.action() == Payloads.PilgrimTradePayload.ACTION_CLOSE) {
                    if (client.screen instanceof PilgrimTradeScreen) {
                        client.setScreen(null);
                    }
                    return;
                }

                List<PilgrimTradeScreen.TradeView> offers = new ArrayList<>(payload.offers().size());
                for (Payloads.PilgrimTradeOfferData offer : payload.offers()) {
                    offers.add(new PilgrimTradeScreen.TradeView(
                            offer.offerId(),
                            offer.title(),
                            offer.description(),
                            offer.price(),
                            offer.previewStack()
                    ));
                }

                List<PilgrimTradeScreen.PilgrimContractView> contracts = new ArrayList<>(payload.contracts().size());
                for (Payloads.PilgrimContractData contract : payload.contracts()) {
                    contracts.add(new PilgrimTradeScreen.PilgrimContractView(
                            contract.contractId(),
                            contract.title(),
                            contract.status(),
                            contract.descriptionLines(),
                            contract.objectiveLines(),
                            contract.rewardLines(),
                            contract.actionLabel(),
                            contract.actionEnabled(),
                            contract.previewStack()
                    ));
                }

                PilgrimTradeScreen.PilgrimTradeData data = new PilgrimTradeScreen.PilgrimTradeData(
                        payload.entityId(),
                        payload.merchantName(),
                        payload.balance(),
                        payload.despawnTicks(),
                        offers,
                        contracts
                );

                if (payload.action() == Payloads.PilgrimTradePayload.ACTION_OPEN) {
                    client.setScreen(new PilgrimTradeScreen(data));
                    return;
                }

                if (client.screen instanceof PilgrimTradeScreen screen) {
                    screen.updateData(data);
                    return;
                }

                client.setScreen(new PilgrimTradeScreen(data));
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(Payloads.QuestMasterPayload.ID, (payload, context) -> {
            var client = context.client();
            client.execute(() -> {
                if (payload.action() == Payloads.QuestMasterPayload.ACTION_CLOSE) {
                    if (client.screen instanceof QuestMasterScreen) {
                        client.setScreen(null);
                    }
                    return;
                }

                List<QuestMasterScreen.CategoryView> categories = new ArrayList<>(payload.categories().size());
                for (Payloads.QuestMasterCategoryData category : payload.categories()) {
                    categories.add(new QuestMasterScreen.CategoryView(
                            category.categoryId(),
                            category.label(),
                            category.entryCount()
                    ));
                }

                List<QuestMasterScreen.EntryView> entries = new ArrayList<>(payload.entries().size());
                for (Payloads.QuestMasterEntryData entry : payload.entries()) {
                    entries.add(new QuestMasterScreen.EntryView(
                            entry.entryId(),
                            entry.categoryId(),
                            entry.title(),
                            entry.subtitle(),
                            entry.status(),
                            entry.descriptionLines(),
                            entry.objectiveLines(),
                            entry.rewardLines(),
                            entry.primaryAction(),
                            entry.primaryLabel(),
                            entry.primaryEnabled(),
                            entry.secondaryAction(),
                            entry.secondaryLabel(),
                            entry.secondaryEnabled(),
                            entry.locked()
                    ));
                }

                QuestMasterScreen.QuestMasterData data = new QuestMasterScreen.QuestMasterData(
                        payload.entityId(),
                        payload.questMasterName(),
                        categories,
                        entries,
                        payload.storyCooldownUntil()
                );

                if (payload.action() == Payloads.QuestMasterPayload.ACTION_OPEN) {
                    client.setScreen(new QuestMasterScreen(data));
                    return;
                }

                if (client.screen instanceof QuestMasterScreen screen) {
                    screen.updateData(data);
                    return;
                }

                client.setScreen(new QuestMasterScreen(data));
            });
        });
    }
}
