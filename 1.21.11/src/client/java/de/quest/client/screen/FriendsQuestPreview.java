package de.quest.client.screen;

import de.quest.network.FriendsQuestPayloads.*;
import java.util.*;
import net.minecraft.network.chat.Component;

/** Read-only fixtures for the first pages of the admin UI tour. No service/world access. */
final class FriendsQuestPreview {
    private FriendsQuestPreview() {}
    private static Component tr(String suffix,Object... args){return Component.translatable("quest.village-quest.friends."+suffix,args);}
    private static Component ui(String suffix,Object... args){return Component.translatable("screen.village-quest.friends."+suffix,args);}
    static View view(String state) {
        var client=net.minecraft.client.Minecraft.getInstance();
        return view(state,client!=null && client.level!=null?client.level.registryAccess():null);
    }
    static View view(String state,net.minecraft.core.HolderLookup.Provider registries) {
        String who=state.startsWith("lutte")?"lutte":state.startsWith("marius")?"marius":state.equals("ravel_lore")?"ravel":"simon";
        int chapter=switch(state){case "simon_active","lutte_map","marius_healing"->2;case "simon_friends","simon_herbs","lutte_masterpiece","marius_raid","friends_complete"->3;case "ravel_lore"->0;default->1;};
        boolean complete=state.equals("friends_complete") || who.equals("ravel"),herbs=state.equals("simon_herbs"),active=state.equals("simon_active") || state.equals("simon_friends") || state.equals("lutte_map") || state.equals("marius_healing") || state.equals("marius_raid");
        String quest=herbs?"simon.herbs":who+"."+chapter;
        var objectives=new ArrayList<Component>();var notices=new ArrayList<Component>();var progress=new ArrayList<Progress>();var options=new ArrayList<Option>();
        if(!complete){objectives.add(tr(quest+".objective"));options.add(new Option(herbs?"herbs":active?"claim":"accept",tr(active || herbs?"turn_in":"accept"),!active));}
        if(state.equals("simon_offer") || herbs)for(var item:List.of("dandelion","poppy","fern"))progress.add(new Progress("minecraft:"+item,herbs?8:0,8));
        if(state.equals("simon_active"))objectives.add(tr("walking",12734,20000));
        if(state.equals("simon_friends"))objectives.add(tr("villages",2,2));
        if(state.equals("lutte_map")) {
            objectives.add(tr("lutte.signals",1));objectives.add(tr("lutte.targets",2));notices.add(tr("lutte.replace"));
            options.add(new Option("replace",ui("replace"),true));options.add(new Option("survey",ui("survey"),true));
        }
        if(state.equals("lutte_masterpiece")){progress.add(new Progress("minecraft:netherite_ingot",1,1));progress.add(new Progress("minecraft:redstone",16,16));progress.add(new Progress("minecraft:amethyst_shard",8,8));}
        if(state.equals("marius_healing"))objectives.add(tr("marius.healing",30));
        if(state.equals("marius_raid")){objectives.add(tr("marius.raiders",0));objectives.add(tr("marius.preparation"));options.add(new Option("ready",tr("marius.ready"),true));options.add(new Option("prepare",tr("marius.prepare"),true));}
        if(active)notices.add(ui("in_progress"));
        Component name=de.quest.caravan.NpcNames.guest(switch(who){case "simon"->"siven";case "lutte"->"lutren";case "marius"->"marven";default->"ravel";}),dialogue=who.equals("ravel")?Component.translatable("message.village-quest.guest.ravel.again",name,Component.translatable("screen.village-quest.ui_tour.sample.village")):complete || herbs?tr(who+".again"):tr(quest+".dialogue");
        if(who.equals("ravel"))dialogue=dialogue.copy().append(" ").append(Component.translatable("message.village-quest.guest.ravel.music"));
        return new View(new UUID(0,77),0,true,true,who,chapter,active,name,complete?ui("conversation"):tr(quest+".title"),dialogue,objectives,complete?Component.empty():tr(quest+".reward"),notices,progress,options,complete || registries==null?Rewards.EMPTY:de.quest.quest.special.FriendsQuestRewards.create(registries,who,chapter,herbs,new UUID(0,77)));
    }
}
