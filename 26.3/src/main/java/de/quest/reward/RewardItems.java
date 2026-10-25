package de.quest.reward;

import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.PotionContents;

/** Complete native components are frozen once with the world's registry codec. */
public final class RewardItems {
    public static List<ItemStack> create(ServerLevel world, List<VanillaRewardRegistry.Goods> goods) {
        return create(world.registryAccess(),goods);
    }
    public static List<ItemStack> create(net.minecraft.core.HolderLookup.Provider registries, List<VanillaRewardRegistry.Goods> goods) {
        List<ItemStack> result = new ArrayList<>();
        for (var g : goods) {
            var item = BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(g.item()));
            if (item == null || item == Items.AIR) throw new IllegalArgumentException("Unknown reward item " + g.item());
            int remaining = g.count();
            while (remaining > 0) {
                var stack = new ItemStack(item, Math.min(remaining, item.getDefaultInstance().getMaxStackSize()));
                if (!g.potion().isEmpty()) stack.set(DataComponents.POTION_CONTENTS, new PotionContents(
                        BuiltInRegistries.POTION.getOrThrow(ResourceKey.create(Registries.POTION, Identifier.withDefaultNamespace(g.potion())))));
                for (var enchantment : g.enchantments().entrySet()) stack.enchant(
                        registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(ResourceKey.create(
                                Registries.ENCHANTMENT, Identifier.withDefaultNamespace(enchantment.getKey()))), enchantment.getValue());
                result.add(stack); remaining -= stack.getCount();
            }
        }
        return List.copyOf(result);
    }
    public static ListTag encode(ServerLevel world, List<ItemStack> items) {
        return encode(world.registryAccess(), items);
    }
    static ListTag encode(net.minecraft.core.HolderLookup.Provider registries, List<ItemStack> items) {
        var ops = RegistryOps.create(NbtOps.INSTANCE, registries); var list = new ListTag();
        for (var stack : items) if (!stack.isEmpty()) list.add(ItemStack.CODEC.encodeStart(ops, stack).getOrThrow());
        return list;
    }
    public static ItemStack decode(ServerLevel world, Tag tag) {
        return decode(world.registryAccess(), tag);
    }
    static ItemStack decode(net.minecraft.core.HolderLookup.Provider registries, Tag tag) {
        return ItemStack.CODEC.parse(RegistryOps.create(NbtOps.INSTANCE, registries), tag).getOrThrow();
    }
    public static List<ItemStack> lutteTool(net.minecraft.core.HolderLookup.Provider registries, VanillaRewardRegistry.Option option) {
        var items=create(registries,option.goods());
        for(var item:items)item.set(DataComponents.ITEM_NAME,de.quest.quest.special.FriendsQuestService.tr("lutte.tool."+option.id()));
        return items;
    }
    private RewardItems() { }
}
