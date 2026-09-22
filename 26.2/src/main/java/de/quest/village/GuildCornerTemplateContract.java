package de.quest.village;

import java.util.HashSet;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/** Runtime-safe validation shared by bundled and datapack-overridden Guild Corner templates. */
final class GuildCornerTemplateContract {
    private static final String NOTICE_POST = "village-quest:guild_notice_post";

    private GuildCornerTemplateContract() {}

    static boolean isValid(CompoundTag template) {
        return isValid(template, ignored -> false);
    }

    static boolean isValid(CompoundTag template, Predicate<String> blockEntityState) {
        if (template == null || template.isEmpty() || !template.getListOrEmpty("palettes").isEmpty()) return false;
        ListTag size = template.getListOrEmpty("size");
        if (size.size() != 3) return false;
        int sx = size.getIntOr(0, -1);
        int sy = size.getIntOr(1, -1);
        int sz = size.getIntOr(2, -1);
        if (sx < 1 || sy < 1 || sz < 1 || sx > 16 || sy > 16 || sz > 16
                || !template.getListOrEmpty("entities").isEmpty()) return false;

        ListTag palette = template.getListOrEmpty("palette");
        if (palette.isEmpty()) return false;
        Set<Integer> postStates = new HashSet<>();
        List<String> paletteNames = new ArrayList<>(palette.size());
        for (int index = 0; index < palette.size(); index++) {
            CompoundTag state = palette.getCompoundOrEmpty(index);
            String blockName = state.getStringOr("Name", "");
            paletteNames.add(blockName);
            if (NOTICE_POST.equals(blockName)) {
                if (!"north".equals(state.getCompoundOrEmpty("Properties").getStringOr("facing", ""))) return false;
                postStates.add(index);
            } else if (blockName.isBlank() || blockEntityState.test(blockName)) return false;
        }
        if (postStates.isEmpty()) return false;

        int posts = 0;
        int postX = -1;
        int postY = -1;
        int postZ = -1;
        Map<String, Integer> statesByPosition = new HashMap<>();
        ListTag blocks = template.getListOrEmpty("blocks");
        for (int index = 0; index < blocks.size(); index++) {
            CompoundTag block = blocks.getCompoundOrEmpty(index);
            int state = block.getIntOr("state", -1);
            if (state < 0 || state >= palette.size() || block.contains("nbt")) return false;
            ListTag pos = block.getListOrEmpty("pos");
            if (pos.size() != 3 || pos.getIntOr(0, -1) < 0 || pos.getIntOr(0, sx) >= sx
                    || pos.getIntOr(1, -1) < 0 || pos.getIntOr(1, sy) >= sy
                    || pos.getIntOr(2, -1) < 0 || pos.getIntOr(2, sz) >= sz) return false;
            int x = pos.getIntOr(0, -1);
            int y = pos.getIntOr(1, -1);
            int z = pos.getIntOr(2, -1);
            if (statesByPosition.put(positionKey(x, y, z), state) != null) return false;
            if (postStates.contains(state)) {
                posts++;
                postX = x;
                postY = y;
                postZ = z;
            }
        }
        if (posts != 1) return false;
        for (int xOffset = -1; xOffset <= 1; xOffset++) {
            for (int yOffset = 0; yOffset <= 1; yOffset++) {
                if (xOffset == 0 && yOffset == 0) continue;
                int x = postX + xOffset;
                int y = postY + yOffset;
                if (x < 0 || x >= sx || y < 0 || y >= sy) return false;
                Integer state = statesByPosition.get(positionKey(x, y, postZ));
                if (state == null || !"minecraft:air".equals(paletteNames.get(state))) return false;
            }
        }
        return true;
    }

    private static String positionKey(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

}
