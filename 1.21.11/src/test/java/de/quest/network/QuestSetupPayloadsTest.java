package de.quest.network;

import static org.junit.jupiter.api.Assertions.*;
import de.quest.config.WorldQuestSettings;
import de.quest.config.VillageQuestServerConfig;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

final class QuestSetupPayloadsTest {
    @Test void allWorldChoicesAndPersonalVisibilityRoundTrip() {
        for (var profile : VillageQuestServerConfig.AdventureProfile.values())
            for (var mode : VillageQuestServerConfig.CaravanVisualMode.values()) {
                var settings = new WorldQuestSettings(true, profile, false, mode);
                var view = new QuestSetupPayloads.View(UUID.randomUUID(), true, false, settings, false, true, 2);
                var save = new QuestSetupPayloads.Save(view.session(), true, false, settings, false, true);
                var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
                try {
                    QuestSetupPayloads.View.CODEC.encode(buf, view);
                    assertEquals(view, QuestSetupPayloads.View.CODEC.decode(buf));
                    QuestSetupPayloads.Save.CODEC.encode(buf, save);
                    assertEquals(save, QuestSetupPayloads.Save.CODEC.decode(buf));
                    assertEquals(0, buf.readableBytes());
                } finally { buf.release(); }
            }
    }
}
