package de.quest.caravan;

import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;

/** Explicit V2 wire names. Persisted literals are never inferred or migrated by spelling. */
public final class NpcNames {
    private NpcNames() {}
    private static final String PREFIX="@vq2:";
    public static final List<String> VILLAGE_FEMALE=List.of("Mira","Greta","Elina","Maren","Nora","Leni","Juna","Freya","Elsbeth","Ida","Tessa","Selma");
    public static final List<String> VILLAGE_MALE=List.of("Ruben","Tomas","Emrik","Joren","Ivo","Leoric","Arne","Milo","Falk","Leif","Konrad","Alwin");
    public static final List<String> FIRST=List.of("Mira","Elric","Torren","Hanna","Bram","Cira","Alda","Doran","Esme","Fenn","Rowan","Liora","Kellan","Nessa","Osric","Tilda","Marin","Soren","Edric","Anwen","Beric","Lyra","Corin","Mara","Greta","Ivo","Brynn","Runa","Ysolda","Garrick","Oren","Tavin");
    public static final List<String> SURNAMES=List.of("Bellweather","Fenner","Ashford","Westmere","Cartwright","Wayfarer","Oakridge","Fairbrook","Hearthwick","Stonewell","Bracken","Thornfield","Riverbend","Mosswood","Dunford","Briarwood","Ashbrook","Whitlock","Meadowcroft","Vale","Kestrel","Brambleford","Alderwick","Northfield","Silverbrook","Ravenmere","Dawnridge","Willowford","Ironvale","Redfern","Mistwood","Goldfield");
    public static final List<String> GUESTS=List.of("lutren","siven","marven","ravel");
    public static String generated(String literal) {
        String[] parts=literal.split(" ",2);
        return parts.length==2 && FIRST.contains(parts[0]) && SURNAMES.contains(parts[1])?PREFIX+parts[0]+":"+parts[1]:literal;
    }
    public static Component component(String wire) {
        if(wire!=null && wire.startsWith(PREFIX)) {
            String[] parts=wire.substring(PREFIX.length()).split(":",-1);
            if(parts.length==2 && FIRST.contains(parts[0]) && SURNAMES.contains(parts[1]))
                return Component.literal(parts[0]+" ").append(Component.translatableWithFallback("entity.village-quest.npc.surname."+parts[1].toLowerCase(Locale.ROOT),parts[1]));
        }
        return Component.literal(wire==null?"":wire);
    }
    public static Component guest(String id) {
        if(!GUESTS.contains(id)) throw new IllegalArgumentException("Unknown guest");
        return Component.translatable("entity.village-quest.npc.guest."+id);
    }
}
