package de.quest.resource;
import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Guards missing effect keys and excessively long individual tooltip rows in all locales. */
final class NewItemLoreTest {
 @Test void newItemsHaveCompleteShortLoreAndOtherNumberedItemRowsStayReadable() throws Exception {
  for(String locale:new String[]{"en_us","de_de","es_es"}) {
   try(var in=getClass().getResourceAsStream("/assets/village-quest/lang/"+locale+".json")) {
    assertNotNull(in);var catalog=JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();
    for(String item:new String[]{"weather_tonic","village_watch_seal","village_check"}) {
     int count=item.equals("weather_tonic")?2:3;
     for(int row=1;row<=count;row++) {
      String key="item.village-quest."+item+".lore."+row;
      assertTrue(catalog.has(key),locale+":"+key);assertFalse(catalog.get(key).getAsString().isBlank());
     }
    }
    assertTrue(catalog.has("item.village-quest.village_watch_seal.lore"));
    for(var entry:catalog.entrySet())if(entry.getKey().startsWith("item.") && entry.getKey().matches(".*\\.lore\\.[0-9]+")) {
     String row=entry.getValue().getAsString();assertTrue(row.length()<=65,locale+":"+entry.getKey()+" has "+row.length()+" characters");
    }
   }
  }
 }
}