package de.quest.quest;

import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

/** Full journal details accompany the same identity and short objective used by the HUD. */
public record JournalActiveQuest(TrackerEntry entry,List<Component> details,int cancelAction) {
    public JournalActiveQuest {
        if(entry==null || details==null || details.size()>64 || cancelAction< -1 || cancelAction>2)
            throw new IllegalArgumentException("Invalid active journal quest");
        details=List.copyOf(details);
    }
    public static List<JournalActiveQuest> checked(List<JournalActiveQuest> quests) {
        if(quests==null) throw new IllegalArgumentException("Missing journal quests");
        TrackerEntry.checked(quests.stream().map(JournalActiveQuest::entry).toList());
        return List.copyOf(quests);
    }
    public static List<JournalActiveQuest> readList(RegistryFriendlyByteBuf buf) {
        int count=buf.readVarInt();
        if(count<0 || count>TrackerEntry.MAX_ENTRIES) throw new IllegalArgumentException("Too many journal quests");
        var quests=new java.util.ArrayList<JournalActiveQuest>();
        for(int i=0;i<count;i++) {
            var entry=TrackerEntry.read(buf); int size=buf.readVarInt();
            if(size<0 || size>64) throw new IllegalArgumentException("Too many journal details");
            var lines=new java.util.ArrayList<Component>();
            for(int n=0;n<size;n++) lines.add(ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf));
            quests.add(new JournalActiveQuest(entry,lines,buf.readVarInt()));
        }
        return checked(quests);
    }
    public static void writeList(RegistryFriendlyByteBuf buf,List<JournalActiveQuest> quests) {
        quests=checked(quests); buf.writeVarInt(quests.size());
        for(var quest:quests) {
            TrackerEntry.write(buf,quest.entry()); buf.writeVarInt(quest.details().size());
            for(var line:quest.details()) ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf,line);
            buf.writeVarInt(quest.cancelAction());
        }
    }
}
