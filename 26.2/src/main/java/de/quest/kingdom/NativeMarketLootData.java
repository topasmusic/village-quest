package de.quest.kingdom;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Translate the native 26.2 loot spelling into the bounded reader's representation.
 * Unknown fields survive translation so the strict reader can reject them. */
final class NativeMarketLootData {
    private NativeMarketLootData() {}
    static JsonElement normalize(JsonElement data) { return normalize(data,0,new int[]{16384}); }
    private static JsonElement normalize(JsonElement data,int depth,int[] budget) {
        if(depth>32 || --budget[0]<0) throw new IllegalArgumentException("Unbounded native loot data");
        if(data==null || data.isJsonNull() || data.isJsonPrimitive()) return data;
        if(data.isJsonArray()) {
            var result=new JsonArray();
            for(var child:data.getAsJsonArray()) result.add(normalize(child,depth+1,budget));
            return result;
        }
        var result=new JsonObject();
        for(var entry:data.getAsJsonObject().entrySet()) result.add(entry.getKey(),normalize(entry.getValue(),depth+1,budget));
        rename(result,"function","type");
        if(result.has("condition") && result.get("condition").isJsonPrimitive()
                && result.get("condition").getAsJsonPrimitive().isString()) rename(result,"condition","type");
        rename(result,"functions","modifier");
        if(result.has("conditions")) {
            if(result.has("condition")) throw new IllegalArgumentException("Ambiguous conditions");
            var terms=result.remove("conditions").getAsJsonArray();
            if(terms.size()==1) result.add("condition",terms.get(0));
            else { var all=new JsonObject(); all.addProperty("type","minecraft:all_of"); all.add("terms",terms); result.add("condition",all); }
        }
        if(result.has("type") && result.get("type").isJsonPrimitive()
                && result.get("type").getAsString().equals("minecraft:block_state_property")) {
            result.addProperty("type","minecraft:match_block");
            rename(result,"block","blocks"); rename(result,"properties","state");
        }
        return result;
    }
    private static void rename(JsonObject data,String from,String to) {
        if(!data.has(from)) return;
        if(data.has(to)) throw new IllegalArgumentException("Ambiguous native loot metadata");
        data.add(to,data.remove(from));
    }
}
