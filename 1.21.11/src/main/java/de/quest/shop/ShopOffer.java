package de.quest.shop;

import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

public record ShopOffer(
        String id,
        String categoryId,
        Text title,
        Text description,
        long price,
        PreviewStackFactory previewStackFactory,
        PurchaseHandler purchaseHandler
) {
    public ShopOffer {
        title = title == null ? Text.empty() : title;
        description = description == null ? Text.empty() : description;
        price = Math.max(0L, price);
        previewStackFactory = previewStackFactory == null ? world -> ItemStack.EMPTY : previewStackFactory;
    }

    @FunctionalInterface
    public interface PurchaseHandler {
        PurchaseResult purchase(ServerWorld world, ServerPlayerEntity player);
    }

    @FunctionalInterface
    public interface PreviewStackFactory {
        ItemStack createPreview(ServerWorld world);
    }

    public ItemStack previewStack(ServerWorld world) {
        ItemStack preview = previewStackFactory.createPreview(world);
        return preview == null ? ItemStack.EMPTY : preview.copy();
    }

    public record PurchaseResult(boolean success, Text message) {
        public PurchaseResult {
            message = message == null ? Text.empty() : message;
        }

        public static PurchaseResult success(Text message) {
            return new PurchaseResult(true, message);
        }

        public static PurchaseResult fail(Text message) {
            return new PurchaseResult(false, message);
        }
    }
}
