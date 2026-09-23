package de.quest.village;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

final class GuildCornerTemplateContractTest {
    @Test
    void validNorthAuthoredSinglePostTemplateIsAccepted() {
        assertTrue(GuildCornerTemplateContract.isValid(validTemplate()));
    }

    @Test
    void entitiesBlockEntitiesWrongOrientationAndExtraPostsAreRejected() {
        CompoundTag entity = validTemplate();
        entity.getListOrEmpty("entities").add(new CompoundTag());
        assertFalse(GuildCornerTemplateContract.isValid(entity));

        CompoundTag blockEntity = validTemplate();
        blockEntity.getListOrEmpty("blocks").getCompoundOrEmpty(0).put("nbt", new CompoundTag());
        assertFalse(GuildCornerTemplateContract.isValid(blockEntity));

        CompoundTag implicitBlockEntity = validTemplate();
        CompoundTag chest = new CompoundTag();
        chest.putString("Name", "minecraft:chest");
        implicitBlockEntity.getListOrEmpty("palette").add(chest);
        implicitBlockEntity.getListOrEmpty("blocks").add(block(2, 1, 0, 0));
        assertFalse(GuildCornerTemplateContract.isValid(implicitBlockEntity,
                name -> name.equals("minecraft:chest") || name.equals("minecraft:furnace")));

        CompoundTag furnaceWithoutNbt = validTemplate();
        CompoundTag furnace = new CompoundTag();
        furnace.putString("Name", "minecraft:furnace");
        furnaceWithoutNbt.getListOrEmpty("palette").add(furnace);
        furnaceWithoutNbt.getListOrEmpty("blocks").add(block(2, 1, 0, 0));
        assertFalse(GuildCornerTemplateContract.isValid(furnaceWithoutNbt,
                name -> name.equals("minecraft:chest") || name.equals("minecraft:furnace")));

        CompoundTag facing = validTemplate();
        facing.getListOrEmpty("palette").getCompoundOrEmpty(0)
                .getCompoundOrEmpty("Properties").putString("facing", "south");
        assertFalse(GuildCornerTemplateContract.isValid(facing));

        CompoundTag posts = validTemplate();
        posts.getListOrEmpty("blocks").add(block(0, 1, 0, 0));
        assertFalse(GuildCornerTemplateContract.isValid(posts));
    }

    @Test
    void malformedDimensionsPaletteReferencesAndMultiPalettesAreRejected() {
        CompoundTag tooLarge = validTemplate();
        tooLarge.getListOrEmpty("size").set(0, IntTag.valueOf(17));
        assertFalse(GuildCornerTemplateContract.isValid(tooLarge));

        CompoundTag badState = validTemplate();
        badState.getListOrEmpty("blocks").getCompoundOrEmpty(0).putInt("state", 8);
        assertFalse(GuildCornerTemplateContract.isValid(badState));

        CompoundTag multi = validTemplate();
        ListTag palettes = new ListTag();
        palettes.add(new ListTag());
        multi.put("palettes", palettes);
        assertFalse(GuildCornerTemplateContract.isValid(multi));
    }

    @Test
    void solidBlocksInsideTheNoticePostOverhangAreRejected() {
        CompoundTag blocked = validTemplate();
        CompoundTag stone = new CompoundTag();
        stone.putString("Name", "minecraft:stone");
        blocked.getListOrEmpty("palette").add(stone);
        blocked.getListOrEmpty("blocks").getCompoundOrEmpty(2).putInt("state", 2);

        assertFalse(GuildCornerTemplateContract.isValid(blocked));
    }

    private static CompoundTag validTemplate() {
        CompoundTag root = new CompoundTag();
        ListTag size = new ListTag();
        size.add(IntTag.valueOf(3)); size.add(IntTag.valueOf(2)); size.add(IntTag.valueOf(1));
        root.put("size", size);
        CompoundTag post = new CompoundTag();
        post.putString("Name", "village-quest:guild_notice_post");
        CompoundTag properties = new CompoundTag();
        properties.putString("facing", "north");
        post.put("Properties", properties);
        ListTag palette = new ListTag();
        palette.add(post);
        root.put("palette", palette);
        ListTag blocks = new ListTag();
        blocks.add(block(0, 1, 0, 0));
        CompoundTag air = new CompoundTag();
        air.putString("Name", "minecraft:air");
        palette.add(air);
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 2; y++) {
                if (x != 1 || y != 0) blocks.add(block(1, x, y, 0));
            }
        }
        root.put("blocks", blocks);
        root.put("entities", new ListTag());
        return root;
    }

    private static CompoundTag block(int state, int x, int y, int z) {
        CompoundTag block = new CompoundTag();
        block.putInt("state", state);
        ListTag pos = new ListTag();
        pos.add(IntTag.valueOf(x)); pos.add(IntTag.valueOf(y)); pos.add(IntTag.valueOf(z));
        block.put("pos", pos);
        return block;
    }
}
