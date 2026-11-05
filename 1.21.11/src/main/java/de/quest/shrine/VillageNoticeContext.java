package de.quest.shrine;

import java.util.UUID;
import net.minecraft.core.BlockPos;

/** Server-resolved personal contact at a real board; connection grants only network effects. */
public record VillageNoticeContext(UUID villageId, int index, String dimension,
                                   VillageBondType type, BlockPos location, boolean connected,
                                   VillageBondService.VillageBondView bond) {}
