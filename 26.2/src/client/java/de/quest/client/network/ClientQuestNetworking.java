package de.quest.client.network;

import de.quest.client.config.VillageQuestClientConfig;
import de.quest.client.hud.QuestTrackerHud;
import de.quest.client.screen.AdminJournalScreen;
import de.quest.client.screen.JournalScreen;
import de.quest.client.screen.PilgrimTradeScreen;
import de.quest.client.screen.ProsperityScreen;
import de.quest.client.screen.QuestMasterScreen;
import de.quest.client.screen.TradeRouteMapScreen;
import de.quest.client.screen.WayshrineScreen;
import de.quest.client.screen.GuildNoticeBoardScreen;
import de.quest.client.screen.GuildPathScreen;
import de.quest.client.hud.TradeRouteMinimapHud;
import de.quest.network.Payloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

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

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(() -> {
            VillageQuestClientConfig config = VillageQuestClientConfig.get();
            if (ClientPlayNetworking.canSend(Payloads.ClientPreferencesPayload.ID)) {
                ClientPlayNetworking.send(new Payloads.ClientPreferencesPayload(
                        config.questTrackerEnabledByDefault(),
                        config.questAvailableChatNotifications(),
                        config.caravanEventNotifications(),
                        config.questProgressSounds(),
                        config.questProgressSoundVolume()));
            }
            if (config.minimapEnabledByDefault()
                    && ClientPlayNetworking.canSend(Payloads.TradeRouteActionPayload.ID)) {
                ClientPlayNetworking.send(new Payloads.TradeRouteActionPayload(
                        Payloads.TradeRouteActionPayload.ACTION_MINIMAP_TOGGLE, -1));
            }
        }));

        ClientPlayNetworking.registerGlobalReceiver(Payloads.QuestFeedbackPayload.ID, (payload, context) -> {
            var client = context.client();
            client.execute(() -> playQuestFeedback(client, payload.tier()));
        });

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
                        payload.hasCaravanYardProject(),
                        payload.hasWayshrineNetworkProject(),
                        payload.guildPathNodes()
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

        ClientPlayNetworking.registerGlobalReceiver(Payloads.EconomyPayload.ID, (payload, context) -> {
            var client = context.client();
            client.execute(() -> {
                if (payload.action() == Payloads.EconomyPayload.ACTION_OPEN) {
                    setScreen(client, new ProsperityScreen(payload));
                    return;
                }
                if (currentScreen(client) instanceof ProsperityScreen screen) {
                    screen.updateData(payload);
                    return;
                }
                setScreen(client, new ProsperityScreen(payload));
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
                        ),
                        payload.storyCooldownUntil()
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

        ClientPlayNetworking.registerGlobalReceiver(Payloads.WayshrinePayload.ID, (payload, context) -> {
            var client = context.client();
            client.execute(() -> {
                if (currentScreen(client) instanceof WayshrineScreen screen) {
                    screen.updateData(payload);
                } else {
                    setScreen(client, new WayshrineScreen(payload));
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(Payloads.NoticeBoardPayload.ID, (payload, context) -> {
            var client = context.client();
            client.execute(() -> {
                if (currentScreen(client) instanceof GuildNoticeBoardScreen screen) {
                    screen.updateData(payload);
                } else {
                    setScreen(client, new GuildNoticeBoardScreen(payload));
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(Payloads.GuildPathPayload.ID, (payload, context) -> {
            var client = context.client();
            client.execute(() -> {
                if (currentScreen(client) instanceof GuildPathScreen screen) {
                    screen.updateData(payload);
                } else {
                    setScreen(client, new GuildPathScreen(payload));
                }
            });
        });
    }

    private static void playQuestFeedback(net.minecraft.client.Minecraft client, int tier) {
        VillageQuestClientConfig config = VillageQuestClientConfig.get();
        if (client == null || !config.questProgressSounds() || config.questProgressSoundVolume() <= 0.0f) {
            return;
        }
        float volume = config.questProgressSoundVolume();
        switch (tier) {
            case Payloads.QuestFeedbackPayload.PROGRESS -> client.getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.45f, 0.12f * volume));
            case Payloads.QuestFeedbackPayload.OBJECTIVE -> client.getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.35f, 0.24f * volume));
            case Payloads.QuestFeedbackPayload.ACCEPTED -> client.getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 0.24f * volume));
            case Payloads.QuestFeedbackPayload.STAGE -> {
                client.getSoundManager().play(
                        SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.15f, 0.30f * volume));
                client.getSoundManager().play(
                        SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.80f, 0.16f * volume));
            }
            case Payloads.QuestFeedbackPayload.AVAILABILITY -> client.getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.VILLAGER_YES, 1.08f, 0.28f * volume));
            default -> {
            }
        }
    }
}
