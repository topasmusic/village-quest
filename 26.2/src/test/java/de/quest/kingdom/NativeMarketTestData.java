package de.quest.kingdom;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import java.io.InputStreamReader;
import java.net.JarURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipFile;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagFile;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagLoader;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootTable;

/** Private registry/data fixture for pinned 26.2 resources; no server, world or resource reload. */
final class NativeMarketTestData {
    private static HolderLookup.Provider cached;
    private NativeMarketTestData() {}
    static synchronized HolderLookup.Provider lookup() {
        if(cached!=null) return cached;
        try {
            for(var item:BuiltInRegistries.ITEM) {
                try { item.builtInRegistryHolder().components(); }
                catch(NullPointerException unbound) { item.builtInRegistryHolder().bindComponents(DataComponentMap.builder()
                        .set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE,64).build()); }
            }
            var resource=Recipe.class.getResource("/data/minecraft/recipe/hay_block.json");
            if(resource==null) throw new IllegalStateException("Missing pinned Minecraft data");
            var connection=(JarURLConnection)resource.openConnection();
            try(var zip=new ZipFile(java.nio.file.Path.of(connection.getJarFileURL().toURI()).toFile())) {
                var items=new MappedRegistry<Item>(Registries.ITEM,Lifecycle.stable());
                BuiltInRegistries.ITEM.listElements().forEach(h -> {
                    var copy=items.register(h.key(),h.value(),RegistrationInfo.BUILT_IN);
                    copy.bindComponents(h.components());
                });
                items.freeze(); loadTags(zip,items,"item");
                var blocks=new MappedRegistry<Block>(Registries.BLOCK,Lifecycle.stable());
                BuiltInRegistries.BLOCK.listElements().forEach(h -> blocks.register(h.key(),h.value(),RegistrationInfo.BUILT_IN));
                blocks.freeze(); loadTags(zip,blocks,"block");
                var nativeLookup=VanillaRegistries.createLookup();
                var enchantments=new MappedRegistry<net.minecraft.world.item.enchantment.Enchantment>(Registries.ENCHANTMENT,Lifecycle.stable());
                nativeLookup.lookupOrThrow(Registries.ENCHANTMENT).listElements().forEach(h -> enchantments.register(h.key(),h.value(),RegistrationInfo.BUILT_IN));
                enchantments.freeze(); loadTags(zip,enchantments,"enchantment");
                var patterns=new MappedRegistry<net.minecraft.world.item.equipment.trim.TrimPattern>(Registries.TRIM_PATTERN,Lifecycle.stable());
                nativeLookup.lookupOrThrow(Registries.TRIM_PATTERN).listElements().forEach(h -> patterns.register(h.key(),h.value(),RegistrationInfo.BUILT_IN)); patterns.freeze();
                var materials=new MappedRegistry<net.minecraft.world.item.equipment.trim.TrimMaterial>(Registries.TRIM_MATERIAL,Lifecycle.stable());
                nativeLookup.lookupOrThrow(Registries.TRIM_MATERIAL).listElements().forEach(h -> materials.register(h.key(),h.value(),RegistrationInfo.BUILT_IN)); materials.freeze();
                var loot=new MappedRegistry<LootTable>(Registries.LOOT_TABLE,Lifecycle.stable());
                var entries=zip.stream().filter(e -> e.getName().startsWith("data/minecraft/loot_table/") && e.getName().endsWith(".json")).toList();
                var registration=loot.createRegistrationLookup();
                for(var entry:entries) registration.getOrThrow(lootKey(entry.getName()));
                var lookup=HolderLookup.Provider.create(Stream.concat(nativeLookup.listRegistries()
                        .filter(r -> !r.key().equals(Registries.ITEM) && !r.key().equals(Registries.BLOCK) && !r.key().equals(Registries.LOOT_TABLE) && !r.key().equals(Registries.ENCHANTMENT) && !r.key().equals(Registries.TRIM_PATTERN) && !r.key().equals(Registries.TRIM_MATERIAL)),
                        Stream.<HolderLookup.RegistryLookup<?>>of(items,blocks,loot,enchantments,patterns,materials)));
                var ops=RegistryOps.create(JsonOps.INSTANCE,lookup);
                for(var entry:entries) try(var reader=new InputStreamReader(zip.getInputStream(entry),StandardCharsets.UTF_8)) {
                    var decoded=LootTable.DIRECT_CODEC.parse(ops,JsonParser.parseReader(reader));
                    if(decoded.error().isPresent()) throw new IllegalStateException(entry.getName()+": "+decoded.error());
                    loot.register(lootKey(entry.getName()),decoded.getOrThrow(),RegistrationInfo.BUILT_IN);
                }
                loot.freeze(); cached=lookup; return lookup;
            }
        } catch(Exception invalid) { throw new IllegalStateException("Cannot read pinned 26.2 native data",invalid); }
    }
    /** Encode synthetic bounded-reader fixtures using the target's actual native loot codec schema. */
    static com.google.gson.JsonElement nativeLoot(com.google.gson.JsonElement data) {
        if(data==null || data.isJsonNull() || data.isJsonPrimitive()) return data;
        if(data.isJsonArray()) {
            var array=new com.google.gson.JsonArray(); for(var child:data.getAsJsonArray()) array.add(nativeLoot(child)); return array;
        }
        var result=new com.google.gson.JsonObject();
        for(var e:data.getAsJsonObject().entrySet()) {
            if(e.getKey().equals("modifier")) {
                var array=new com.google.gson.JsonArray();
                var values=e.getValue().isJsonArray()?e.getValue().getAsJsonArray().asList():List.of(e.getValue());
                for(var value:values) {
                    var function=nativeLoot(value).getAsJsonObject();
                    if(function.has("type")) function.add("function",function.remove("type")); array.add(function);
                }
                result.add("functions",array);
            } else if(e.getKey().equals("condition") && !e.getValue().isJsonPrimitive()) {
                var array=new com.google.gson.JsonArray();
                var values=e.getValue().isJsonArray()?e.getValue().getAsJsonArray().asList():List.of(e.getValue());
                for(var value:values) array.add(nativeCondition(value)); result.add("conditions",array);
            } else result.add(e.getKey(),nativeLoot(e.getValue()));
        }
        return result;
    }
    private static com.google.gson.JsonElement nativeCondition(com.google.gson.JsonElement value) {
        if(value==null || !value.isJsonObject()) return value;
        var result=nativeLoot(value).getAsJsonObject();
        if(result.has("type")) {
            var type=result.remove("type").getAsString();
            if(type.equals("minecraft:match_block")) {
                type="minecraft:block_state_property";
                if(result.has("blocks")) result.add("block",result.remove("blocks"));
                if(result.has("state")) result.add("properties",result.remove("state"));
            }
            result.addProperty("condition",type);
        }
        if(result.has("terms")) { var terms=new com.google.gson.JsonArray(); for(var term:result.getAsJsonArray("terms")) terms.add(nativeCondition(term)); result.add("terms",terms); }
        if(result.has("term")) result.add("term",nativeCondition(result.get("term")));
        return result;
    }
    static HolderLookup.Provider withoutBlockTags(HolderLookup.Provider lookup) {
        var blocks=new MappedRegistry<Block>(Registries.BLOCK,Lifecycle.stable());
        BuiltInRegistries.BLOCK.listElements().forEach(h -> blocks.register(h.key(),h.value(),RegistrationInfo.BUILT_IN)); blocks.freeze();
        return HolderLookup.Provider.create(Stream.concat(lookup.listRegistries().filter(r -> !r.key().equals(Registries.BLOCK)),Stream.of(blocks)));
    }
    static List<NativeMarketTransformerCatalog.Transform> transforms(Block output) {
        return BuiltInRegistries.BLOCK.stream().map(input -> new NativeMarketTransformerCatalog.Transform(input,output,List.of())).toList();
    }
    static net.minecraft.world.item.component.TypedEntityData<net.minecraft.world.level.block.entity.BlockEntityType<?>> signPayload(String side) {
        var text=new net.minecraft.nbt.CompoundTag();
        var messages=new net.minecraft.nbt.ListTag();
        messages.add(net.minecraft.nbt.StringTag.valueOf("{\"text\":\"claim\",\"click_event\":{\"action\":\"run_command\",\"command\":\"give @s minecraft:diamond 64\"}}"));
        text.put("messages",messages);
        var tag=new net.minecraft.nbt.CompoundTag(); tag.putString("id","minecraft:sign"); tag.put(side,text);
        return net.minecraft.world.item.component.TypedEntityData.<net.minecraft.world.level.block.entity.BlockEntityType<?>>of(net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE.getValue(net.minecraft.resources.Identifier.withDefaultNamespace("sign")),tag);
    }
    static Map<Identifier,DataComponentMap> prototypes() {
        var values=new HashMap<Identifier,DataComponentMap>();
        for(var pending:BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(lookup())) if(pending.key().equals(Registries.ITEM))
            pending.forEach((holder,components) -> values.put(holder.key().identifier(),components));
        return Map.copyOf(values);
    }
    private static ResourceKey<LootTable> lootKey(String name) {
        return ResourceKey.create(Registries.LOOT_TABLE,Identifier.withDefaultNamespace(
                name.substring("data/minecraft/loot_table/".length(),name.length()-5)));
    }
    private static <T> void loadTags(ZipFile zip,MappedRegistry<T> registry,String kind) throws Exception {
        String prefix="data/minecraft/tags/"+kind+"/";
        var raw=new HashMap<Identifier,List<TagLoader.EntryWithSource>>();
        for(var entry:zip.stream().filter(e -> e.getName().startsWith(prefix) && e.getName().endsWith(".json")).toList())
            try(var reader=new InputStreamReader(zip.getInputStream(entry),StandardCharsets.UTF_8)) {
                var tag=TagFile.CODEC.parse(JsonOps.INSTANCE,JsonParser.parseReader(reader)).getOrThrow();
                raw.put(Identifier.withDefaultNamespace(entry.getName().substring(prefix.length(),entry.getName().length()-5)),
                        tag.entries().stream().map(e -> new TagLoader.EntryWithSource(e,"pinned native jar")).toList());
            }
        var built=new TagLoader<Holder<T>>((id,required) -> registry.get(id),"tags/"+kind).build(raw);
        if(built.size()!=raw.size()) throw new IllegalStateException("Incomplete native "+kind+" tags");
        var tags=new HashMap<TagKey<T>,List<Holder<T>>>();
        built.forEach((id,values) -> tags.put(TagKey.create(registry.key(),id),values));
        registry.prepareTagReload(new TagLoader.LoadResult<>(registry.key(),tags)).apply();
    }
}
