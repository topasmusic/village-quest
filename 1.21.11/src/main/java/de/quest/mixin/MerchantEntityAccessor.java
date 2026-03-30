package de.quest.mixin;

import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.village.TradeOfferList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MerchantEntity.class)
public interface MerchantEntityAccessor {
    @Accessor("offers")
    void villageQuest$setOffers(TradeOfferList offers);
}
