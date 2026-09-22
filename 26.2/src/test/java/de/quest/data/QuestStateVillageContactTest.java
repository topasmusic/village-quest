package de.quest.data;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.quest.shrine.VillageBondType;
import de.quest.shrine.VillageContactService;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class QuestStateVillageContactTest {
    private static final UUID PLAYER = UUID.fromString("4cb70271-ee3a-40a7-9ec8-111e0af8cb6f");

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void contactSurvivesSaveAndReloadWithoutCreatingARoute() {
        QuestState state = QuestState.fromNbt(new CompoundTag());
        VillageContactService.establish(state.getPlayerData(PLAYER), 512, 768, VillageBondType.ARCHIVE);

        QuestState loaded = QuestState.fromNbt(QuestState.toNbt(state));
        PlayerQuestData restored = loaded.getPlayerData(PLAYER);
        VillageContactService.VillageContact contact = VillageContactService.read(restored, 0);

        assertEquals(1, VillageContactService.contactCount(restored));
        assertEquals(512, contact.x());
        assertEquals(768, contact.z());
        assertEquals(VillageBondType.ARCHIVE, contact.type());
        assertEquals(0, restored.getTradeRouteInt("route_count"));
    }
}
