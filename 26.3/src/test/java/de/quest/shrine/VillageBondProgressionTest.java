package de.quest.shrine;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

final class VillageBondProgressionTest {
    @Test void tradesOpenOnlyTheTrustedAlternativeAndNeverReplaceEightRequests() {
        int[] counts={0,1,2,7,8,9};
        VillageBondLevel[][] expected={
            {VillageBondLevel.KNOWN,VillageBondLevel.KNOWN,VillageBondLevel.KNOWN,VillageBondLevel.KNOWN,VillageBondLevel.KNOWN},
            {VillageBondLevel.KNOWN,VillageBondLevel.KNOWN,VillageBondLevel.KNOWN,VillageBondLevel.KNOWN,VillageBondLevel.TRUSTED},
            {VillageBondLevel.TRUSTED,VillageBondLevel.TRUSTED,VillageBondLevel.TRUSTED,VillageBondLevel.TRUSTED,VillageBondLevel.TRUSTED},
            {VillageBondLevel.TRUSTED,VillageBondLevel.TRUSTED,VillageBondLevel.TRUSTED,VillageBondLevel.TRUSTED,VillageBondLevel.TRUSTED},
            {VillageBondLevel.ALLIED,VillageBondLevel.ALLIED,VillageBondLevel.ALLIED,VillageBondLevel.ALLIED,VillageBondLevel.ALLIED},
            {VillageBondLevel.ALLIED,VillageBondLevel.ALLIED,VillageBondLevel.ALLIED,VillageBondLevel.ALLIED,VillageBondLevel.ALLIED}
        };
        for(int c=0;c<counts.length;c++) for(int t=0;t<5;t++)
            assertEquals(expected[c][t],VillageBondProgression.level(counts[c],t),counts[c]+"/"+t);
        assertEquals(VillageBondLevel.KNOWN,VillageBondProgression.level(-1,4));
        assertEquals(VillageBondLevel.KNOWN,VillageBondProgression.level(1,200));
        assertEquals(VillageBondLevel.KNOWN,VillageBondProgression.level(1,-1));
    }
}
