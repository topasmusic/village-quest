package de.quest.kingdom;

import de.quest.village.VillageCondition;
import de.quest.village.VillageNeed;

/** Deterministic answers from the saved person and a validated current need, never a GUI roll. */
public final class KingdomNegotiation {
    private KingdomNegotiation() {}
    public enum Reaction { READY, COUNTER, DELIVERY, UNKNOWN }
    public record Answer(Reaction reaction,KingdomDiplomacy.Terms preferred,VillageNeed need,int personality) {
        public Answer {
            if(reaction==null || preferred==null || personality<0 || personality>3 || reaction==Reaction.DELIVERY && need==null)
                throw new IllegalArgumentException("Invalid negotiation answer");
        }
    }
    public static Answer answer(int personality,KingdomDiplomacy.Type type,KingdomDiplomacy.Terms terms,
                                VillageNeed need,VillageCondition condition) {
        if(personality<0 || personality>3 || type==null || terms==null) throw new IllegalArgumentException();
        boolean urgent=need!=null && (condition==VillageCondition.CRISIS || condition==VillageCondition.STRAINED);
        var preferred=urgent?KingdomDiplomacy.Terms.NEEDS_FIRST:
                personality==2?KingdomDiplomacy.Terms.CAUTIOUS:personality==1?KingdomDiplomacy.Terms.NEEDS_FIRST:KingdomDiplomacy.Terms.BALANCED;
        if(type==KingdomDiplomacy.Type.SUPPLY && (need==null || condition==null))
            return new Answer(Reaction.UNKNOWN,preferred,null,personality);
        if(terms!=preferred) return new Answer(Reaction.COUNTER,preferred,need,personality);
        return new Answer(type==KingdomDiplomacy.Type.SUPPLY?Reaction.DELIVERY:Reaction.READY,preferred,
                type==KingdomDiplomacy.Type.SUPPLY?need:null,personality);
    }
}
