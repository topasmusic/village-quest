package de.quest.client.screen;

import net.minecraft.network.chat.Component;

import java.util.List;

/** Immutable journal content card, independent from rendering and input state. */
record JournalCard(String id, Component title, Component subtitle, List<Component> details,
                   int accent, int cancelAction) {}
