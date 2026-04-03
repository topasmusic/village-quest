package de.quest.shop;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public record ShopOffer(
        String id,
        String categoryId,
        Component title,
        Component description,
        long price,
        PreviewStackFactory previewStackFactory,
        PurchaseHandler purchaseHandler
) {
    public ShopOffer {
        title = title == null ? Component.empty() : title;
        description = description == null ? Component.empty() : description;
        price = Math.max(0L, price);
        previewStackFactory = previewStackFactory == null ? world -> ItemStack.EMPTY : previewStackFactory;
    }

    @FunctionalInterface
    public interface PurchaseHandler {
        PurchaseResult purchase(ServerLevel world, ServerPlayer player);
    }

    @FunctionalInterface
    public interface PreviewStackFactory {
        ItemStack createPreview(ServerLevel world);
    }

    public ItemStack previewStack(ServerLevel world) {
        ItemStack preview = previewStackFactory.createPreview(world);
        return preview == null ? ItemStack.EMPTY : preview.copy();
    }

    public record PurchaseResult(boolean success, Component message) {
        public PurchaseResult {
            message = message == null ? Component.empty() : message;
        }

        public static PurchaseResult success(Component message) {
            return new PurchaseResult(true, message);
        }

        public static PurchaseResult fail(Component message) {
            return new PurchaseResult(false, message);
        }
    }
}
