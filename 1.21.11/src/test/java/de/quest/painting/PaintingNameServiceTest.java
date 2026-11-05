package de.quest.painting;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.junit.jupiter.api.*;

final class PaintingNameServiceTest {
    @BeforeAll static void bootstrap() { net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap(); }
    @AfterEach void clear() { PaintingNameService.clear(); }
    @Test void cleanupDoesNotWalkUnrelatedEntitiesWhenNoPaintingsAreLoaded() {
        var server = mock(MinecraftServer.class); var world = mock(ServerLevel.class);
        when(server.getTickCount()).thenReturn(20); when(server.getAllLevels()).thenReturn(java.util.List.of(world));
        when(world.getAllEntities()).thenReturn(() -> { throw new AssertionError("Unrelated entity collection traversed"); });
        PaintingNameService.onServerTick(server);
    }

    @Test void onlyLoadedProjectPaintingsLoseTheirNamesAndUnloadedPaintingsAreForgotten() {
        var server = mock(MinecraftServer.class); var world = mock(ServerLevel.class);
        when(server.getTickCount()).thenReturn(20); when(server.getAllLevels()).thenReturn(java.util.List.of(world));
        var project = new NamedPainting("village-quest"); var vanilla = new NamedPainting("minecraft");
        PaintingNameService.onEntityLoad(project.entity, world); PaintingNameService.onEntityLoad(vanilla.entity, world);
        assertNull(project.name.get()); assertFalse(project.visible.get());
        assertNotNull(vanilla.name.get()); assertTrue(vanilla.visible.get());
        project.name.set(net.minecraft.network.chat.Component.literal("Changed after load")); project.visible.set(true);
        PaintingNameService.onServerTick(server); assertNull(project.name.get()); assertFalse(project.visible.get());
        PaintingNameService.onEntityUnload(project.entity, world);
        project.name.set(net.minecraft.network.chat.Component.literal("Unloaded")); project.visible.set(true);
        PaintingNameService.onServerTick(server); assertNotNull(project.name.get()); assertTrue(project.visible.get());
    }

    private static final class NamedPainting {
        final net.minecraft.world.entity.decoration.painting.Painting entity = mock(net.minecraft.world.entity.decoration.painting.Painting.class);
        final java.util.concurrent.atomic.AtomicReference<net.minecraft.network.chat.Component> name = new java.util.concurrent.atomic.AtomicReference<>(net.minecraft.network.chat.Component.literal("Named"));
        final java.util.concurrent.atomic.AtomicBoolean visible = new java.util.concurrent.atomic.AtomicBoolean(true);
        @SuppressWarnings("unchecked") NamedPainting(String namespace) {
            var holder = (net.minecraft.core.Holder<net.minecraft.world.entity.decoration.painting.PaintingVariant>) mock(net.minecraft.core.Holder.Reference.class);
            when(holder.unwrapKey()).thenReturn(java.util.Optional.of(net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.PAINTING_VARIANT, net.minecraft.resources.Identifier.fromNamespaceAndPath(namespace, "test"))));
            when(entity.getVariant()).thenReturn(holder); when(entity.getCustomName()).thenAnswer(call -> name.get());
            when(entity.isCustomNameVisible()).thenAnswer(call -> visible.get());
            doAnswer(call -> { name.set(call.getArgument(0)); return null; }).when(entity).setCustomName(any());
            doAnswer(call -> { visible.set(call.getArgument(0)); return null; }).when(entity).setCustomNameVisible(anyBoolean());
        }
    }
}
