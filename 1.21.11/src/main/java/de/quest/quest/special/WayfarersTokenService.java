package de.quest.quest.special;

import de.quest.data.QuestState;
import java.util.List;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

/** Transferable reward; cooldown belongs to the user, never to a particular feather. */
public final class WayfarersTokenService {
    public static final int DURATION=30*20, COOLDOWN=5*60*20;
    private static final String NAME="quest.village-quest.friends.simon.keepsake";
    private static final String READY="simon.token.readyAt";
    private WayfarersTokenService() {}

    public static void register() {
        UseItemCallback.EVENT.register((player,level,hand)->{
            var stack=player.getItemInHand(hand);
            if(!isToken(stack))return InteractionResult.PASS;
            if(!(level instanceof ServerLevel world) || !(player instanceof ServerPlayer actor))return InteractionResult.SUCCESS;
            return use(world,actor,stack)?InteractionResult.SUCCESS:InteractionResult.FAIL;
        });
    }
    public static boolean isToken(ItemStack stack) {
        if(stack.isEmpty() || !stack.is(Items.FEATHER))return false;
        if(stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getBooleanOr("wayfarersToken",false))return true;
        var name=stack.get(DataComponents.ITEM_NAME);
        return name!=null && name.getContents() instanceof TranslatableContents translated && NAME.equals(translated.getKey());
    }
    public static ItemStack create() {
        var stack=new ItemStack(Items.FEATHER);
        stack.set(DataComponents.ITEM_NAME,Component.translatable(NAME));
        refresh(stack);return stack;
    }
    public static void refresh(ItemStack stack) {
        if(!isToken(stack))return;
        var name=Component.translatable(NAME).withStyle(ChatFormatting.GREEN);
        var lore=new ItemLore(List.of(FriendsQuestService.tr("simon.token.lore.1").withStyle(ChatFormatting.GRAY),
                FriendsQuestService.tr("simon.token.lore.2").withStyle(ChatFormatting.GRAY)));
        if(!name.equals(stack.get(DataComponents.ITEM_NAME)))stack.set(DataComponents.ITEM_NAME,name);
        if(!lore.equals(stack.get(DataComponents.LORE)))stack.set(DataComponents.LORE,lore);
        if(!stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getBooleanOr("wayfarersToken",false))
            CustomData.update(DataComponents.CUSTOM_DATA,stack,t->t.putBoolean("wayfarersToken",true));
    }
    public static void refreshInventory(ServerPlayer player) {
        var inventory=player.getInventory();
        for(int slot=0;slot<inventory.getContainerSize();slot++)refresh(inventory.getItem(slot));
        refresh(player.getOffhandItem());
    }
    public static long remaining(FriendsQuestProgress progress,long now) {
        String raw=progress.text(READY);
        if(raw.isBlank())return 0;
        try {long ready=Long.parseLong(raw);return ready<0 || now<0?-1:Math.max(0,ready-now);}
        catch(NumberFormatException invalid){return -1;}
    }
    public static boolean use(ServerLevel world,ServerPlayer player,ItemStack stack) {
        if(!isToken(stack))return false;
        if(!player.isAlive() || player.isSpectator())return false;
        var progress=FriendsQuestService.progress(world,player.getUUID());
        long now=world.getServer().overworld().getGameTime(),remaining=remaining(progress,now);
        if(remaining!=0 || now<0 || now>Long.MAX_VALUE-COOLDOWN) {
            player.sendSystemMessage(remaining>0?FriendsQuestService.tr("simon.token.recharging",MariusQuestService.activeTime(remaining)):
                    FriendsQuestService.tr("simon.token.unavailable"),false);return false;
        }
        refresh(stack);
        progress.text(READY,Long.toString(now+COOLDOWN));QuestState.get(world.getServer()).setDirty();
        player.addEffect(new MobEffectInstance(MobEffects.SPEED,DURATION,1,false,false));
        player.sendSystemMessage(FriendsQuestService.tr("simon.token.used"),false);return true;
    }
}
