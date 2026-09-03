package de.quest.village;

import de.quest.shrine.VillageRequestType;

/** One server-authoritative Notice Board choice. */
public record VillageRequestOffer(int id, VillageRequestType request, int amount,
                                  long reward, int support, boolean primaryNeed) {}
