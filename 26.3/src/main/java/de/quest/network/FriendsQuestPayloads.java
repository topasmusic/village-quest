package de.quest.network;

import java.util.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Presentation snapshots only. Every action is authorized again on the server. */
public final class FriendsQuestPayloads {
    private FriendsQuestPayloads() {}
    public static final Set<String> ACTIONS=Set.of("accept","claim","replace","survey","ready","prepare","herbs","close");
    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> id(String name) {
        return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("village-quest",name));
    }
    public record Option(String action,Component label,boolean enabled) {
        public Option { if(!ACTIONS.contains(action) || action.equals("close"))throw new IllegalArgumentException("Invalid friend action"); Objects.requireNonNull(label); }
    }
    public record Progress(ItemStack stack,int carried,int required) {
        public Progress { if(stack.isEmpty() || carried<0 || required<1 || required>64)throw new IllegalArgumentException("Invalid friend progress");stack=stack.copy(); }
        public Progress(String id,int carried,int required){this(FriendsQuestPayloads.stack(id),carried,required);}
        @Override public boolean equals(Object value){return value instanceof Progress p && carried==p.carried && required==p.required && ItemStack.matches(stack,p.stack);}
        @Override public int hashCode(){return Objects.hash(carried,required);}
    }
    public record Rewards(long currency,double experience,List<ItemStack> items,boolean choice) {
        public static final Rewards EMPTY=new Rewards(0,0,List.of(),false);
        public Rewards { if(currency<0 || currency>1000 || !Double.isFinite(experience) || experience<0 || experience>100)throw new IllegalArgumentException("Invalid friend rewards");count(items.size(),8);if(items.stream().anyMatch(ItemStack::isEmpty))throw new IllegalArgumentException("Empty reward");items=items.stream().map(ItemStack::copy).toList(); }
        @Override public boolean equals(Object value){if(!(value instanceof Rewards r) || currency!=r.currency || experience!=r.experience || choice!=r.choice || items.size()!=r.items.size())return false;for(int i=0;i<items.size();i++)if(!ItemStack.matches(items.get(i),r.items.get(i)))return false;return true;}
        @Override public int hashCode(){return Objects.hash(currency,experience,choice,items.size());}
    }
    private static ItemStack stack(String id){var key=Identifier.tryParse(id);if(key==null)throw new IllegalArgumentException("Invalid item id");return new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(key));}
    public record View(UUID session,long revision,boolean initial,boolean valid,String who,int chapter,boolean active,
                       Component name,Component title,Component dialogue,List<Component> objectives,Component reward,
                       List<Component> notices,List<Progress> progress,List<Option> options,Rewards rewards) implements CustomPacketPayload {
        public View(UUID session,long revision,boolean initial,boolean valid,String who,int chapter,boolean active,Component name,Component title,Component dialogue,List<Component> objectives,Component reward,List<Component> notices,List<Progress> progress,List<Option> options) {
            this(session,revision,initial,valid,who,chapter,active,name,title,dialogue,objectives,reward,notices,progress,options,Rewards.EMPTY);
        }
        public View {
            Objects.requireNonNull(rewards);
            Objects.requireNonNull(session);Objects.requireNonNull(name);Objects.requireNonNull(title);Objects.requireNonNull(dialogue);Objects.requireNonNull(reward);
            if(!Set.of("simon","lutte","marius","ravel").contains(who) || chapter<0 || chapter>3 || revision<0)throw new IllegalArgumentException("Invalid friend view");
            objectives=List.copyOf(objectives);notices=List.copyOf(notices);progress=List.copyOf(progress);options=List.copyOf(options);
            count(objectives.size(),12);count(notices.size(),12);count(progress.size(),3);count(options.size(),3);
            if(options.stream().map(Option::action).distinct().count()!=options.size())throw new IllegalArgumentException("Duplicate friend action");
        }
        public static final Type<View> ID=id("friends_quest_view");
        public static final StreamCodec<RegistryFriendlyByteBuf,View> CODEC=StreamCodec.of((b,v)-> {
            b.writeUUID(v.session);b.writeVarLong(v.revision);b.writeBoolean(v.initial);b.writeBoolean(v.valid);b.writeUtf(v.who,16);b.writeVarInt(v.chapter);b.writeBoolean(v.active);
            component(b,v.name);component(b,v.title);component(b,v.dialogue);components(b,v.objectives);component(b,v.reward);components(b,v.notices);
            b.writeVarInt(v.progress.size());for(var p:v.progress){ItemStack.STREAM_CODEC.encode(b,p.stack);b.writeVarInt(p.carried);b.writeVarInt(p.required);}
            b.writeVarLong(v.rewards.currency);b.writeDouble(v.rewards.experience);b.writeBoolean(v.rewards.choice);b.writeVarInt(v.rewards.items.size());for(var item:v.rewards.items)ItemStack.STREAM_CODEC.encode(b,item);
            b.writeVarInt(v.options.size());for(var o:v.options){b.writeUtf(o.action,16);component(b,o.label);b.writeBoolean(o.enabled);}
        },b->{
            UUID session=b.readUUID();long revision=b.readVarLong();boolean initial=b.readBoolean(),valid=b.readBoolean();String who=b.readUtf(16);int chapter=b.readVarInt();boolean active=b.readBoolean();
            var name=component(b);var title=component(b);var dialogue=component(b);var objectives=components(b);var reward=component(b);var notices=components(b);
            int n=count(b.readVarInt(),3);var progress=new ArrayList<Progress>(n);for(int i=0;i<n;i++)progress.add(new Progress(ItemStack.STREAM_CODEC.decode(b),b.readVarInt(),b.readVarInt()));
            long currency=b.readVarLong();double xp=b.readDouble();boolean choice=b.readBoolean();n=count(b.readVarInt(),8);var items=new ArrayList<ItemStack>(n);for(int i=0;i<n;i++)items.add(ItemStack.STREAM_CODEC.decode(b));var rewards=new Rewards(currency,xp,items,choice);
            n=count(b.readVarInt(),3);var options=new ArrayList<Option>(n);for(int i=0;i<n;i++)options.add(new Option(b.readUtf(16),component(b),b.readBoolean()));
            return new View(session,revision,initial,valid,who,chapter,active,name,title,dialogue,objectives,reward,notices,progress,options,rewards);
        });
        @Override public Type<? extends CustomPacketPayload> type(){return ID;}
    }
    public record Action(UUID session,long revision,String action) implements CustomPacketPayload {
        public Action {Objects.requireNonNull(session);if(revision<0 || !ACTIONS.contains(action))throw new IllegalArgumentException("Invalid friend action");}
        public static final Type<Action> ID=id("friends_quest_action");
        public static final StreamCodec<RegistryFriendlyByteBuf,Action> CODEC=StreamCodec.of((b,v)->{b.writeUUID(v.session);b.writeVarLong(v.revision);b.writeUtf(v.action,16);},b->new Action(b.readUUID(),b.readVarLong(),b.readUtf(16)));
        @Override public Type<? extends CustomPacketPayload> type(){return ID;}
    }
    private static int count(int n,int max){if(n<0 || n>max)throw new IllegalArgumentException("Friend collection limit");return n;}
    private static void component(RegistryFriendlyByteBuf b,Component c){ComponentSerialization.TRUSTED_STREAM_CODEC.encode(b,c);}
    private static Component component(RegistryFriendlyByteBuf b){return ComponentSerialization.TRUSTED_STREAM_CODEC.decode(b);}
    private static void components(RegistryFriendlyByteBuf b,List<Component> cs){b.writeVarInt(cs.size());for(var c:cs)component(b,c);}
    private static List<Component> components(RegistryFriendlyByteBuf b){int n=count(b.readVarInt(),12);var cs=new ArrayList<Component>(n);for(int i=0;i<n;i++)cs.add(component(b));return cs;}
}
