package de.quest.quest.special;

import de.quest.network.FriendsQuestPayloads.Rewards;
import de.quest.reward.*;
import de.quest.registry.ModItems;
import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.*;
import net.minecraft.world.item.component.CustomData;

/** Read-only native reward construction shared with the existing delivery paths. */
public final class FriendsQuestRewards {
    private FriendsQuestRewards() {}
    public static Rewards create(HolderLookup.Provider registries,String who,int chapter,boolean herbs,UUID owner) {
        if(chapter<1 || chapter>3 || who.equals("ravel"))return Rewards.EMPTY;
        long currency=herbs?0:who.equals("simon")?new int[]{10,30,25}[chapter-1]:chapter<3?new int[]{12,20}[chapter-1]:0;
        double xp=herbs?0:who.equals("simon")?chapter==1?1.5:4:chapter<3?chapter+1:0;
        var items=new ArrayList<ItemStack>();boolean choice=who.equals("lutte") && chapter==3;
        if(who.equals("simon")) {
            if(herbs || chapter==3)items.add(new ItemStack(ModItems.WEATHER_TONIC,herbs?1:3));
            else if(chapter==1) {
                var potion=new ItemStack(Items.POTION);potion.set(DataComponents.POTION_CONTENTS,new PotionContents(Potions.STRONG_SWIFTNESS));
                items.add(potion.copy());items.add(potion.copy());items.add(potion.copy());
            } else {
                items.add(WayfarersTokenService.create());
            }
        } else if(who.equals("lutte") && chapter==2)items.addAll(RewardItems.create(registries,List.of(new VanillaRewardRegistry.Goods("enchanted_book",1,"",Map.of("unbreaking",3)))));
        else if(choice)for(var option:VanillaRewardRegistry.options("friends:lutte",null))items.addAll(RewardItems.lutteTool(registries,option));
        else if(who.equals("marius") && chapter==3) {
            var seal=new ItemStack(ModItems.VILLAGE_WATCH_SEAL);CustomData.update(DataComponents.CUSTOM_DATA,seal,t->t.putString("watchOwner",owner.toString()));items.add(seal);
        }
        return new Rewards(currency,xp,items,choice);
    }
}
