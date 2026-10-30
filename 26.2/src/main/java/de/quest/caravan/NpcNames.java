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
    /** Separate ambient pools: existing caravan/village rolls and saved names stay unchanged. */
    public record AmbientProfile(String id,String first,String surname,boolean female) {
        public String texture() { return "ambient/"+id; }
        public Component displayName() { return component(generated(first+" "+surname)); }
    }
    public static final List<AmbientProfile> WANDERERS=List.of(
            new AmbientProfile("wanderer_hanno","Hanno","Mosswood",false),
            new AmbientProfile("wanderer_bennet","Bennet","Fairbrook",false),
            new AmbientProfile("wanderer_alva","Alva","Willowford",true));
    public static final List<AmbientProfile> NIGHTRUNNERS=List.of(
            new AmbientProfile("nightrunner_ronan","Ronan","Ironvale",false),
            new AmbientProfile("nightrunner_silas","Silas","Ravenmere",false),
            new AmbientProfile("nightrunner_enna","Enna","Ashford",true));
    public static List<AmbientProfile> ambientProfiles() {
        return java.util.stream.Stream.concat(WANDERERS.stream(),NIGHTRUNNERS.stream()).toList();
    }
    private static boolean knownFirst(String first) {
        return FIRST.contains(first) || WANDERERS.stream().anyMatch(p -> p.first().equals(first))
                || NIGHTRUNNERS.stream().anyMatch(p -> p.first().equals(first));
    }
    public static String generated(String literal) {
        String[] parts=literal.split(" ",2);
        return parts.length==2 && knownFirst(parts[0]) && SURNAMES.contains(parts[1])?PREFIX+parts[0]+":"+parts[1]:literal;
    }
    public static Component component(String wire) {
        if(wire!=null && wire.startsWith(PREFIX)) {
            String[] parts=wire.substring(PREFIX.length()).split(":",-1);
            if(parts.length==2 && knownFirst(parts[0]) && SURNAMES.contains(parts[1]))
                return Component.literal(parts[0]+" ").append(Component.translatableWithFallback("entity.village-quest.npc.surname."+parts[1].toLowerCase(Locale.ROOT),parts[1]));
        }
        return Component.literal(wire==null?"":wire);
    }
    public static Component guest(String id) {
        if(!GUESTS.contains(id)) throw new IllegalArgumentException("Unknown guest");
        return Component.translatable("entity.village-quest.npc.guest."+id);
    }
}
