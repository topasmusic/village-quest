package de.quest.caravan;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.google.gson.JsonParser;
import de.quest.data.PlayerQuestData;
import net.minecraft.locale.Language;
import org.junit.jupiter.api.Test;

final class NpcNamesTest {
    @Test void sameDisplayComponentChangesOnlySurnameWithClientLocale() throws Exception {
        var original=Language.getInstance();
        try {
            var name=NpcNames.component(NpcNames.generated("Mira Bellweather"));
            int index=0;
            for(var locale:new String[]{"en_us","de_de","es_es"}) {
                try(var stream=getClass().getClassLoader().getResourceAsStream("assets/village-quest/lang/"+locale+".json")) {
                    var json=JsonParser.parseReader(new java.io.InputStreamReader(stream,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                    var lang=mock(Language.class);
                    when(lang.getOrDefault(anyString(),anyString())).thenAnswer(i -> json.has(i.getArgument(0))?json.get((String)i.getArgument(0)).getAsString():i.getArgument(1));
                    when(lang.getOrDefault(anyString())).thenAnswer(i -> json.has(i.getArgument(0))?json.get((String)i.getArgument(0)).getAsString():i.getArgument(0));
                    Language.inject(lang);
                    assertEquals(new String[]{"Mira Bellweather","Mira Glockenwind","Mira Viento de Campanas"}[index++],name.getString());
                    assertTrue(NpcNames.guest("ravel").getString().startsWith("Raphael "));
                }
            }
        } finally { Language.inject(original); }
    }
    @Test void legacyKnownAndManualNamesAreNeverAdoptedAndManualEditsDisableTheirMarker() {
        var data=new PlayerQuestData(); data.setTradeRouteInt("route_count",1);
        data.setTradeRouteString("route_0_crew_master_name","Mira Bellweather");
        assertEquals("Mira Bellweather",CaravanCrewData.ensure(data,0).master());
        assertEquals("",data.getTradeRouteString("route_0_crew_master_name_v2"));
        assertTrue(CaravanCrewData.ensure(data,0).trader().startsWith("@vq2:"));
        data.setTradeRouteString("route_0_crew_trader_name","My custom trader");
        assertEquals("My custom trader",CaravanCrewData.ensure(data,0).trader());
        assertEquals("",data.getTradeRouteString("route_0_crew_trader_name_v2"));
        assertEquals("Unbekannt der Zweite",NpcNames.component("Unbekannt der Zweite").getString());
    }
    @Test void poolsHaveTheAgreedCardinalityAndNoSpecialGuestLeakingIntoOrdinaryCrew() {
        assertEquals(24,NpcNames.VILLAGE_FEMALE.size()+NpcNames.VILLAGE_MALE.size());
        assertEquals(32,NpcNames.FIRST.stream().distinct().count()); assertEquals(32,NpcNames.SURNAMES.stream().distinct().count());
        for(var guest:java.util.List.of("Lutte","Simon","Marius","Raphael")) assertFalse(NpcNames.FIRST.contains(guest));
    }
}
