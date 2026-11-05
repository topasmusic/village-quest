package de.quest.quest;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

/** Presentation identity only; never a reward, permission or progression key. */
public record TrackerEntry(String id, String category, Component title, Component shortObjective) {
    public static final int MAX_ENTRIES=32;
    public static final Set<String> CATEGORIES=Set.of("daily","weekly","story","special","pilgrim");
    public TrackerEntry {
        if (!validId(id) || !CATEGORIES.contains(category) || title==null || shortObjective==null)
            throw new IllegalArgumentException("Invalid tracker entry");
    }
    public static boolean validId(String id) {
        return id!=null && id.length()<=160 && id.matches("[a-z0-9_:-]+")
                && (CATEGORIES.contains(id) || id.startsWith("daily:") || id.startsWith("weekly:")
                || id.startsWith("story:") || id.startsWith("special:") || id.startsWith("pilgrim:"));
    }
    public static List<TrackerEntry> checked(List<TrackerEntry> entries) {
        if(entries==null || entries.size()>MAX_ENTRIES) throw new IllegalArgumentException("Too many tracker entries");
        var ids=new HashSet<String>();
        for(var entry:entries) if(entry==null || !ids.add(entry.id())) throw new IllegalArgumentException("Duplicate tracker identity");
        return List.copyOf(entries);
    }
    public static TrackerEntry read(RegistryFriendlyByteBuf buf) {
        return new TrackerEntry(buf.readUtf(160),buf.readUtf(16),ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf),
                ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf));
    }
    public static void write(RegistryFriendlyByteBuf buf,TrackerEntry entry) {
        buf.writeUtf(entry.id(),160); buf.writeUtf(entry.category(),16);
        ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf,entry.title());
        ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf,entry.shortObjective());
    }
    public static List<TrackerEntry> readList(RegistryFriendlyByteBuf buf) {
        int size=buf.readVarInt();
        if(size<0 || size>MAX_ENTRIES) throw new IllegalArgumentException("Too many tracker entries");
        var entries=new java.util.ArrayList<TrackerEntry>();
        for(int i=0;i<size;i++) entries.add(read(buf));
        return checked(entries);
    }
    public static void writeList(RegistryFriendlyByteBuf buf,List<TrackerEntry> entries) {
        entries=checked(entries); buf.writeVarInt(entries.size());
        for(var entry:entries) write(buf,entry);
    }
}
