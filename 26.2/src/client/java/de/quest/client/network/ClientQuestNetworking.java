package de.quest.client.network;

import de.quest.client.hud.QuestTrackerHud;
import de.quest.client.screen.AdminJournalScreen;
import de.quest.client.screen.JournalScreen;
import de.quest.client.screen.PilgrimTradeScreen;
import de.quest.client.screen.QuestMasterScreen;
import de.quest.client.screen.TradeRouteMapScreen;
import de.quest.client.hud.TradeRouteMinimapHud;
import de.quest.network.Payloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.util.ArrayList;
import java.util.List;

public final class ClientQuestNetworking {
    private ClientQuestNetworking() {}

    private static net.minecraft.client.gui.screens.Screen currentScreen(net.minecraft.client.Minecraft client) {
        return client.gui.screen();
    }

    private static void setScreen(net.minecraft.client.Minecraft client, net.minecraft.client.gui.screens.Screen screen) {
        client.gui.setScreen(screen);
    }

    public static void register() {
        Payloads.register();

        ClientPlayNetworking.registerGlobalReceiver(Payloads.JournalPayload.ID, (payload, context) -> {
            var client = context.client();
            client.execute(() -> {
                if (payload.action() == Payloads.JournalPayload.ACTION_CLOSE) {
                    if (currentScreen(client) instanceof JournalScreen) {
                        setScreen(client, null);
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
                        payload.hasCaravanLedger(),
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
                        payload.hasWatchBellProject(),
                        payload.hasCaravanYardProject()
                );

                if (payload.action() == Payloads.JournalPayload.ACTION_OPEN) {
                    setScreen(client, new JournalScreen(data));
                    return;
                }

                if (currentScreen(client) instanceof JournalScreen screen) {
                    screen.updateData(data);
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(Payloads.AdminJournalPayload.ID, (payload, context) -> {
            var client = context.client();
            client.execute(() -> setScreen(client, new AdminJournalScreen(payload.lines())));
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
                    if (currentScreen(client) instanceof PilgrimTradeScreen) {
                        setScreen(client, null);
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
                    setScreen(client, new PilgrimTradeScreen(data));
                    return;
                }

                if (currentScreen(client) instanceof PilgrimTradeScreen screen) {
                    screen.updateData(data);
                    return;
                }

                setScreen(client, new PilgrimTradeScreen(data));
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(Payloads.QuestMasterPayload.ID, (payload, context) -> {
            var client = context.client();
            client.execute(() -> {
                if (payload.action() == Payloads.QuestMasterPayload.ACTION_CLOSE) {
                    if (currentScreen(client) instanceof QuestMasterScreen) {
                        setScreen(client, null);
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
                            entry.partyShareable(),
                            entry.partyStatus(),
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

                List<QuestMasterScreen.PartyMemberView> members = new ArrayList<>(payload.party().members().size());
                for (Payloads.QuestMasterPartyMemberData member : payload.party().members()) {
                    members.add(new QuestMasterScreen.PartyMemberView(
                            member.playerId(),
                            member.name(),
                            member.leader(),
                            member.self()
                    ));
                }

                List<QuestMasterScreen.PartyCandidateView> candidates = new ArrayList<>(payload.party().candidates().size());
                for (Payloads.QuestMasterPartyCandidateData candidate : payload.party().candidates()) {
                    candidates.add(new QuestMasterScreen.PartyCandidateView(
                            candidate.playerId(),
                            candidate.name(),
                            candidate.status(),
                            candidate.inviteable()
                    ));
                }

                QuestMasterScreen.QuestMasterData data = new QuestMasterScreen.QuestMasterData(
                        payload.entityId(),
                        payload.questMasterName(),
                        categories,
                        entries,
                        new QuestMasterScreen.PartyView(
                                payload.party().hasParty(),
                                payload.party().leader(),
                                payload.party().summary(),
                                members,
                                candidates
                        )
                );

                if (payload.action() == Payloads.QuestMasterPayload.ACTION_OPEN) {
                    setScreen(client, new QuestMasterScreen(data));
                    return;
                }

                if (currentScreen(client) instanceof QuestMasterScreen screen) {
                    screen.updateData(data);
                    return;
                }

                setScreen(client, new QuestMasterScreen(data));
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(Payloads.TradeRouteMapPayload.ID, (payload, context) -> {
            var client = context.client();
            client.execute(() -> {
                if (payload.action() == Payloads.TradeRouteMapPayload.ACTION_CLOSE) {
                    if (currentScreen(client) instanceof TradeRouteMapScreen) {
                        setScreen(client, null);
                    }
                    return;
                }
                if (payload.action() == Payloads.TradeRouteMapPayload.ACTION_MINIMAP_DISABLE) {
                    TradeRouteMinimapHud.disable();
                    return;
                }
                if (payload.action() == Payloads.TradeRouteMapPayload.ACTION_MINIMAP_ENABLE) {
                    TradeRouteMinimapHud.enable(payload);
                    return;
                }
                if (payload.action() == Payloads.TradeRouteMapPayload.ACTION_MINIMAP_UPDATE) {
                    TradeRouteMinimapHud.update(payload);
                    return;
                }
                if (payload.action() == Payloads.TradeRouteMapPayload.ACTION_OPEN) {
                    setScreen(client, new TradeRouteMapScreen(payload));
                    return;
                }
                if (currentScreen(client) instanceof TradeRouteMapScreen screen) {
                    screen.updateData(payload);
                }
            });
        });
    }
}
