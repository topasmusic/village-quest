package de.quest.kingdom;

import java.util.List;

/** Reviewed request candidates, not a source bypass. Zero source credit keeps a SKU unavailable. */
final class RegionalBuybackPolicy {
    private RegionalBuybackPolicy() {}
    record Candidate(String item,int lot,long sale) {}
    static final List<Candidate> CANDIDATES=List.of(
        new Candidate("wheat",32,4),new Candidate("wheat_seeds",64,4),new Candidate("potato",32,4),new Candidate("bread",32,6),
        new Candidate("coal",32,6),new Candidate("copper_ingot",16,7),new Candidate("iron_ingot",8,8),
        new Candidate("hay_block",8,4),new Candidate("white_wool",16,4),new Candidate("leather",8,4),
        new Candidate("dandelion",32,4),new Candidate("honey_bottle",16,8),new Candidate("honeycomb",8,4),
        new Candidate("paper",32,4),new Candidate("feather",32,6),new Candidate("ink_sac",8,4));
}
