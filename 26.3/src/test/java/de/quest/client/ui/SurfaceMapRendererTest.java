package de.quest.client.ui;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.mojang.blaze3d.platform.NativeImage;
import de.quest.map.FantasyMapStyle.Terrain;
import de.quest.map.MapWorkBudget;
import java.lang.reflect.*;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Runs only JVM mocks and pure pixels; does not construct a client or a world. */
class SurfaceMapRendererTest {
    @org.junit.jupiter.api.BeforeAll static void registries() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }
    private static final Class<?> SAMPLE = nested("Sample"), RASTER = nested("Raster");
    private static Class<?> nested(String name) {
        try { return Class.forName(SurfaceMapRenderer.class.getName() + "$" + name); }
        catch (Exception error) { throw new AssertionError(error); }
    }
    private static Object field(Object object, String name) throws Exception {
        Field field = object instanceof Class<?> type ? type.getDeclaredField(name) : object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object instanceof Class<?> ? null : object);
    }
    private static void set(Object object, String name, Object value) throws Exception {
        Field f = object.getClass().getDeclaredField(name); f.setAccessible(true); f.set(object, value);
        if(name.equals("uploadedRaster")) {
            // Manually installed image fixtures represent the supplied terrain, unlike the real mutable-image test.
            boolean[] known=null;
            if(value!=null) {
                var samples=(Object[])field(value,"samples");known=new boolean[samples.length];
                for(int i=0;i<known.length;i++) known[i]=field(samples[i],"terrain")!=Terrain.UNKNOWN;
            }
            Field ground=object.getClass().getDeclaredField("knownGround");ground.setAccessible(true);ground.set(object,known);
        }
    }
    private static Object invoke(Object object, String name, Object... args) throws Exception {
        Class<?> type = object instanceof Class<?> c ? c : object.getClass();
        for (Method method : type.getDeclaredMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == args.length) {
                method.setAccessible(true); return method.invoke(object instanceof Class<?> ? null : object, args);
            }
        }
        throw new NoSuchMethodException(name);
    }
    private static Object raster(ClientLevel level, int x, int z, int width, int height, Terrain terrain) throws Exception {
        return rasterAtScale(level,x,z,width,height,terrain,4);
    }
    private static Object rasterAtScale(ClientLevel level, int x, int z, int width, int height, Terrain terrain,int cellSize) throws Exception {
        Constructor<?> sample = SAMPLE.getDeclaredConstructors()[0]; sample.setAccessible(true);
        Object array = Array.newInstance(SAMPLE, width * height);
        for (int row = 0; row < height; row++) for (int col = 0; col < width; col++)
            Array.set(array, row * width + col, sample.newInstance(terrain, 70, 0, (x + col) * cellSize + cellSize/2, (z + row) * cellSize + cellSize/2));
        Constructor<?> constructor = RASTER.getDeclaredConstructors()[0]; constructor.setAccessible(true);
        return constructor.newInstance(level, x * cellSize, (x + width) * cellSize, z * cellSize, (z + height) * cellSize,
                x, z, cellSize, 4, width, height, array, 0L);
    }
    private static MapWorkBudget work() throws Exception { return (MapWorkBudget) field(SurfaceMapRenderer.class, "WORK"); }

    @Test void thinSurfaceAboveAirIsKnownAcrossZoomsAndSurvivesChunkUnloading() throws Exception {
        var level = mock(ClientLevel.class);
        var chunk = mock(net.minecraft.world.level.chunk.ChunkAccess.class);
        when(level.hasChunk(anyInt(), anyInt())).thenReturn(true);
        when(level.getMinY()).thenReturn(-64);
        when(level.getChunk(anyInt(), anyInt(), eq(net.minecraft.world.level.chunk.status.ChunkStatus.FULL), eq(false)))
                .thenReturn(chunk);
        when(chunk.getHeight(eq(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE), anyInt(), anyInt()))
                .thenReturn(70);
        when(chunk.getBlockState(any(net.minecraft.core.BlockPos.class))).thenAnswer(call ->
                ((net.minecraft.core.BlockPos) call.getArgument(0)).getY() == 70
                        ? net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState()
                        : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        for (int cellSize : new int[]{4, 8, 16, 32}) {
            Object raster = rasterAtScale(level, 0, 0, 1, 1, Terrain.UNKNOWN, cellSize);
            work().begin(7000 + cellSize);
            invoke(SurfaceMapRenderer.class, "refreshRaster", raster, 7000L + cellSize, 50L);
            Object sample = ((Object[]) field(raster, "samples"))[0];
            assertEquals(Terrain.GRASS, field(sample, "terrain"), "Visible surface must not become parchment at cell size " + cellSize);
            assertEquals(70, field(sample, "height"));
        }
        when(level.hasChunk(anyInt(), anyInt())).thenReturn(false);
        Object remembered = invoke(SurfaceMapRenderer.class, "sample", level, 2, 2);
        assertEquals(Terrain.GRASS, field(remembered, "terrain"));
        assertEquals(70, field(remembered, "height"));
    }

    @Test void lowestWorldSurfaceAndNegativeChunkCoordinatesDoNotLoseTheirOnlyBlock() throws Exception {
        var level = mock(ClientLevel.class);
        var chunk = mock(net.minecraft.world.level.chunk.ChunkAccess.class);
        when(level.hasChunk(-1, -2)).thenReturn(true);
        when(level.getMinY()).thenReturn(-64);
        when(level.getChunk(-1, -2, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false)).thenReturn(chunk);
        when(chunk.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, 14, 14)).thenReturn(-64);
        when(chunk.getBlockState(any(net.minecraft.core.BlockPos.class)))
                .thenReturn(net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        Object sample = invoke(SurfaceMapRenderer.class, "sampleLoaded", level, -2, -18);
        assertNotEquals(Terrain.UNKNOWN, field(sample, "terrain"));
        assertEquals(-64, field(sample, "height"));
        verify(chunk, atLeastOnce()).getBlockState(argThat(position -> position.getX() == -2 && position.getY() == -64 && position.getZ() == -18));
        verify(level).getChunk(-1, -2, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false);
    }

    @Test void oldV1DiskSamplesRemainReadableAndBadTerrainIdsStayUnknown(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        var path = dir.resolve("tile_-1_0.vqm");
        try (var out = new java.io.DataOutputStream(java.nio.file.Files.newOutputStream(path))) {
            out.writeInt(0x56514D50); out.writeInt(1); out.writeInt(2);
            out.writeInt(-2); out.writeInt(6); out.writeByte(3); out.writeInt(140); out.writeInt(0x5A7949);
            out.writeInt(-6); out.writeInt(6); out.writeByte(99); out.writeInt(63); out.writeInt(0);
        }
        var samples = (java.util.List<?>) invoke(SurfaceMapRenderer.class, "readTile", path);
        assertEquals(2, samples.size());
        assertEquals(Terrain.FOREST, field(samples.getFirst(), "terrain"));
        assertEquals(Terrain.UNKNOWN, field(samples.getLast(), "terrain"));
    }

    @Test void cacheWritesUseTheOldMemoryGridDuringAQualityTransition() throws Exception {
        Field grid = SurfaceMapRenderer.class.getDeclaredField("memoryGrid"); grid.setAccessible(true);
        int previous = grid.getInt(null);
        try {
            grid.setInt(null, 8);
            assertEquals(0, invoke(SurfaceMapRenderer.class, "tileX", 200));
            assertEquals(-1, invoke(SurfaceMapRenderer.class, "tileZ", -200));
        } finally { grid.setInt(null, previous); }
    }

    @AfterEach void clean() throws Exception {
        ((Map<?, ?>) field(SurfaceMapRenderer.class, "SURFACE_MEMORY")).clear();
        ((Map<?, ?>) field(SurfaceMapRenderer.class, "PAINT_TILES")).clear();
    }

    @Test void refreshHasHardSharedSampleLimitAndNeverRequestsUnloadedChunks() throws Exception {
        ClientLevel level = mock(ClientLevel.class);
        Object raster = raster(level, -32, -16, 64, 32, Terrain.UNKNOWN);
        work().begin(200);
        invoke(SurfaceMapRenderer.class, "refreshRaster", raster, 200L, 50L);
        assertEquals(MapWorkBudget.SAMPLES, field(raster, "cursor"));
        invoke(SurfaceMapRenderer.class, "refreshRaster", raster, 200L, 50L);
        assertEquals(MapWorkBudget.SAMPLES, field(raster, "cursor"));
        verify(level, times(MapWorkBudget.SAMPLES)).hasChunk(anyInt(), anyInt());
        verifyNoMoreInteractions(level);
        work().begin(201);
        invoke(SurfaceMapRenderer.class, "refreshRaster", raster, 201L, 50L);
        assertEquals(MapWorkBudget.SAMPLES * 2, field(raster, "cursor"));
    }

    @Test void panningReusesWorldTilesAndTheirHaloInvalidatesOnNeighbourChanges() throws Exception {
        Class<?> keyType = nested("PaintKey");
        Constructor<?> keyConstructor = keyType.getDeclaredConstructors()[0]; keyConstructor.setAccessible(true);
        Object key = keyConstructor.newInstance(-1, -1, 4, 4, true, 255);
        Object first = raster(null, -28, -28, 41, 41, Terrain.FOREST);
        Object second = raster(null, -29, -29, 43, 43, Terrain.FOREST);
        Object tile = invoke(SurfaceMapRenderer.class, "paintTile", first, key);
        assertSame(tile, invoke(SurfaceMapRenderer.class, "paintTile", second, key));
        Object samples = field(second, "samples");
        Constructor<?> constructor = SAMPLE.getDeclaredConstructors()[0]; constructor.setAccessible(true);
        // West halo of cell -16,-16, in the adjacent tile.
        Array.set(samples, 13 * 43 + 12, constructor.newInstance(Terrain.WATER, 63, 0, -66, -62));
        assertNotSame(tile, invoke(SurfaceMapRenderer.class, "paintTile", second, key));
    }

    @Test void tileCacheIsCappedAndUnalignedRasterMarksEveryIntersectingTile() throws Exception {
        Object raster = raster(null, -1, -1, 34, 34, Terrain.GRASS);
        assertEquals(16, ((Set<?>) field(raster, "dirtyPaintTiles")).size());
        Class<?> keyType = nested("PaintKey");
        Constructor<?> constructor = keyType.getDeclaredConstructors()[0]; constructor.setAccessible(true);
        for (int i = 0; i < 140; i++) invoke(SurfaceMapRenderer.class, "paintTile", raster,
                constructor.newInstance(i, 0, 4, 4, true, 255));
        assertEquals(128, ((Map<?, ?>) field(SurfaceMapRenderer.class, "PAINT_TILES")).size());
    }

    @Test void unchangedRasterDoesNotUploadAgainAndOpacityRepaints() throws Exception {
        Object raster = raster(null, 0, 0, 1, 1, Terrain.FOREST);
        Object texture = field(SurfaceMapRenderer.class, "SCREEN_TEXTURE");
        DynamicTexture dynamic = mock(DynamicTexture.class);
        NativeImage image = mock(NativeImage.class);
        when(dynamic.getPixels()).thenReturn(image);
        set(texture, "texture", dynamic); set(texture, "width", 8); set(texture, "height", 8);
        work().begin(300);
        invoke(texture, "upload", raster, 8, 8, true);
        work().begin(310);
        invoke(texture, "upload", raster, 8, 8, true);
        verify(dynamic, times(1)).upload();
        invoke(texture, "upload", raster, 8, 8, false);
        verify(dynamic, times(2)).upload();
        Object shifted = raster(null, 1, 0, 1, 1, Terrain.FOREST);
        invoke(texture, "upload", shifted, 8, 8, false);
        assertSame(raster, field(texture, "displayedRaster"));
        work().begin(315);
        invoke(texture, "upload", shifted, 8, 8, false);
        assertSame(shifted, field(texture, "displayedRaster"));
        set(texture, "texture", null); set(texture, "uploadedRaster", null);
        set(texture, "displayedRaster", null);
    }

    @Test void panPreservesKnownOverlapEvenWhenPaintingBudgetIsExhausted() throws Exception {
        Object previous = raster(null, -16, -16, 32, 16, Terrain.FOREST);
        Object shifted = raster(null, 0, -16, 32, 16, Terrain.FOREST);
        Object texture = field(SurfaceMapRenderer.class, "SCREEN_TEXTURE");
        DynamicTexture dynamic = mock(DynamicTexture.class);
        NativeImage image = mock(NativeImage.class);
        when(dynamic.getPixels()).thenReturn(image);
        when(image.getPixel(anyInt(), anyInt())).thenReturn(0xFF316947);
        set(texture, "texture", dynamic); set(texture, "width", 256); set(texture, "height", 128);
        set(texture, "uploadedRaster", previous); set(texture, "displayedRaster", previous);
        set(texture, "decorated", true); set(texture, "alpha", 255);
        work().begin(500);
        for (int i = 0; i < MapWorkBudget.TILES; i++) assertTrue(work().tile());
        try {
            invoke(texture, "upload", shifted, 256, 128, true);
            // World cells 0..15 were already visible. No paper frame is permitted there.
            verify(image).setPixel(0, 0, 0xFF316947);
            verify(image).setPixel(127, 127, 0xFF316947);
            verify(image, never()).setPixel(eq(128), anyInt(), eq(0xFF316947));
        } finally {
            set(texture, "texture", null); set(texture, "uploadedRaster", null);
            set(texture, "displayedRaster", null);
        }
    }
    @Test void zoomReprojectsOverlapButWorldAndQualityChangesNeverReuseIt() throws Exception {
        for (int mode=0;mode<4;mode++) {
            Object previous=raster(null,-16,-16,32,16,Terrain.FOREST);
            Object next=raster(null,0,-16,32,16,Terrain.FOREST);
            if(mode==0)set(next,"cellSize",8); // Zoom, same observed sample grid.
            if(mode==1)set(next,"level",mock(ClientLevel.class));
            if(mode==2)set(next,"gridSize",8);
            Object texture=field(SurfaceMapRenderer.class,"SCREEN_TEXTURE");
            DynamicTexture dynamic=mock(DynamicTexture.class);
            NativeImage image=mock(NativeImage.class);
            when(dynamic.getPixels()).thenReturn(image);
            when(image.getPixel(anyInt(),anyInt())).thenReturn(0xFF316947);
            set(texture,"texture",dynamic);set(texture,"width",256);set(texture,"height",128);
            set(texture,"uploadedRaster",previous);set(texture,"displayedRaster",previous);
            set(texture,"decorated",true);set(texture,"alpha",255);
            work().begin(800+mode*10);
            assertTrue(work().upload());
            for(int i=0;i<MapWorkBudget.TILES;i++)assertTrue(work().tile());
            try {
                invoke(texture,"upload",next,256,128,mode!=3);
                if(mode==0) {
                    verify(image).setPixel(0,0,0xFF316947);
                    verify(image).setPixel(63,63,0xFF316947);
                    verify(image,never()).setPixel(eq(64),anyInt(),eq(0xFF316947));
                } else {
                    verify(image,never()).setPixel(anyInt(),anyInt(),eq(0xFF316947));
                    assertNull(field(texture,"displayedRaster"),"Old world/quality must not remain visible while upload is throttled");
                }
            } finally {
                set(texture,"texture",null);set(texture,"uploadedRaster",null);set(texture,"displayedRaster",null);
            }
        }
    }    @Test void resizeKeepsThePublishedTextureUntilAReplacementCanUpload() throws Exception {
        Object previous=raster(null,-16,-16,32,16,Terrain.FOREST);
        Object resized=raster(null,0,-16,16,16,Terrain.FOREST);
        Object texture=field(SurfaceMapRenderer.class,"SCREEN_TEXTURE");
        DynamicTexture dynamic=mock(DynamicTexture.class);NativeImage image=mock(NativeImage.class);
        when(dynamic.getPixels()).thenReturn(image);
        set(texture,"texture",dynamic);set(texture,"width",256);set(texture,"height",128);
        set(texture,"uploadedRaster",previous);set(texture,"displayedRaster",previous);
        set(texture,"decorated",true);set(texture,"alpha",255);
        try(var client=mockStatic(net.minecraft.client.Minecraft.class);
            var created=mockConstruction(DynamicTexture.class,(m,c)->when(m.getPixels()).thenReturn(mock(NativeImage.class)))) {
            var mc=mock(net.minecraft.client.Minecraft.class);client.when(net.minecraft.client.Minecraft::getInstance).thenReturn(mc);
            when(mc.getTextureManager()).thenReturn(mock(net.minecraft.client.renderer.texture.TextureManager.class));
            work().begin(900);assertTrue(work().upload());
            invoke(texture,"upload",resized,128,128,true);
            assertSame(previous,field(texture,"displayedRaster"));
            assertSame(dynamic,field(texture,"texture"));
            assertTrue(created.constructed().isEmpty(),"Do not discard a published image while its replacement is throttled");
        } finally {
            set(texture,"texture",null);set(texture,"uploadedRaster",null);set(texture,"displayedRaster",null);
        }
    }
    @Test void retainedUnknownNeverErasesNewKnownGroundAndKnownNeverLeaksIntoNewUnknown() throws Exception {
        Object texture=field(SurfaceMapRenderer.class,"SCREEN_TEXTURE");
        try {
            for(int cellSize:new int[]{2,4,8}) for(int pan:new int[]{-1,1}) for(int mode=0;mode<3;mode++) {
                Object previous=raster(null,pan,pan,16,16,mode==0?Terrain.UNKNOWN:Terrain.FOREST);
                Object next=rasterAtScale(null,0,0,16,16,mode==1?Terrain.UNKNOWN:Terrain.FOREST,cellSize);
                DynamicTexture dynamic=mock(DynamicTexture.class);NativeImage image=mock(NativeImage.class);
                when(dynamic.getPixels()).thenReturn(image);when(image.getPixel(anyInt(),anyInt())).thenReturn(0xFF316947);
                set(texture,"texture",dynamic);set(texture,"width",128);set(texture,"height",128);
                set(texture,"uploadedRaster",previous);set(texture,"displayedRaster",previous);
                set(texture,"decorated",true);set(texture,"alpha",255);
                work().begin(1300+cellSize*100+(pan+1)*30+mode*10);
                for(int i=0;i<MapWorkBudget.TILES;i++) assertTrue(work().tile());
                invoke(texture,"upload",next,128,128,true);
                if(mode<2) verify(image,never()).setPixel(anyInt(),anyInt(),eq(0xFF316947));
                else verify(image).setPixel(16,16,0xFF316947);
                verify(dynamic).upload();
            }
        } finally {
            set(texture,"texture",null);set(texture,"uploadedRaster",null);set(texture,"displayedRaster",null);
        }
    }
    @Test void knownGroundNeverPublishesParchmentHolesWhenDetailedTileBudgetIsExhausted() throws Exception {
        Object texture=field(SurfaceMapRenderer.class,"SCREEN_TEXTURE");
        try {
            for(boolean known:new boolean[]{true,false}) {
                DynamicTexture dynamic=mock(DynamicTexture.class);NativeImage image=mock(NativeImage.class);
                when(dynamic.getPixels()).thenReturn(image);
                set(texture,"texture",dynamic);set(texture,"width",128);set(texture,"height",128);
                set(texture,"uploadedRaster",null);set(texture,"displayedRaster",null);
                work().begin(1200+(known?0:10));
                for(int i=0;i<MapWorkBudget.TILES;i++)assertTrue(work().tile());
                var raster=raster(null,0,0,16,16,known?Terrain.FOREST:Terrain.UNKNOWN);
                invoke(texture,"upload",raster,128,128,true);
                if(known) {
                    verify(image).fillRect(0,0,8,8,0xFF7D994C);
                    verify(image).fillRect(120,120,8,8,0xFF7D994C);
                    verify(image,times(256)).fillRect(anyInt(),anyInt(),eq(8),eq(8),eq(0xFF7D994C));
                } else verify(image,never()).fillRect(anyInt(),anyInt(),eq(8),eq(8),anyInt());
                verify(dynamic).upload();
            }
        } finally {
            set(texture,"texture",null);set(texture,"uploadedRaster",null);set(texture,"displayedRaster",null);
        }
    }
    @Test void refreshedSampleCannotTurnOldUnpaintedPaperIntoRetainableTerrain() throws Exception {
        Object texture=field(SurfaceMapRenderer.class,"SCREEN_TEXTURE");
        var previous=raster(null,0,0,16,16,Terrain.UNKNOWN);var next=raster(null,1,0,16,16,Terrain.FOREST);
        DynamicTexture dynamic=mock(DynamicTexture.class);NativeImage image=mock(NativeImage.class);
        when(dynamic.getPixels()).thenReturn(image);when(image.getPixel(anyInt(),anyInt())).thenReturn(0xFFD8B978);
        set(texture,"texture",dynamic);set(texture,"width",128);set(texture,"height",128);
        set(texture,"uploadedRaster",null);set(texture,"displayedRaster",null);
        work().begin(3000);for(int i=0;i<MapWorkBudget.TILES;i++)assertTrue(work().tile());
        try {
            invoke(texture,"upload",previous,128,128,true);
            // The sampler updates mutable Raster.samples before this old image receives any detailed paint.
            var known=raster(null,0,0,16,16,Terrain.FOREST);
            System.arraycopy(field(known,"samples"),0,field(previous,"samples"),0,256);
            clearInvocations(image);work().begin(3010);for(int i=0;i<MapWorkBudget.TILES;i++)assertTrue(work().tile());
            invoke(texture,"upload",next,128,128,true);
            verify(image,never()).setPixel(anyInt(),anyInt(),eq(0xFFD8B978));
        } finally {set(texture,"texture",null);set(texture,"uploadedRaster",null);set(texture,"displayedRaster",null);}
    }
    @Test void sameRasterPublishesNewlyObservedGroundAndWithdrawsLostGroundWithoutDetailBudget() throws Exception {
        Object texture=field(SurfaceMapRenderer.class,"SCREEN_TEXTURE");var current=raster(null,0,0,16,16,Terrain.UNKNOWN);
        var unknown=((Object[])field(current,"samples")).clone();var known=field(raster(null,0,0,16,16,Terrain.FOREST),"samples");
        DynamicTexture dynamic=mock(DynamicTexture.class);NativeImage image=mock(NativeImage.class);when(dynamic.getPixels()).thenReturn(image);
        set(texture,"texture",dynamic);set(texture,"width",128);set(texture,"height",128);
        set(texture,"uploadedRaster",null);set(texture,"displayedRaster",null);
        try {
            for(int phase=0;phase<3;phase++) {
                if(phase>0)System.arraycopy(phase==1?known:unknown,0,field(current,"samples"),0,256);
                work().begin(3100+phase*10);for(int i=0;i<MapWorkBudget.TILES;i++)assertTrue(work().tile());clearInvocations(image);
                invoke(texture,"upload",current,128,128,true);
                if(phase==1)verify(image).fillRect(0,0,8,8,0xFF7D994C);
                if(phase==2)verify(image).fillRect(0,0,8,8,0xFFD8B978);
            }
        } finally {set(texture,"texture",null);set(texture,"uploadedRaster",null);set(texture,"displayedRaster",null);}
    }
    @Test void lateDiskReadRestartsFinishedPausedRasterSweep() throws Exception {
        var raster=raster(mock(ClientLevel.class),0,0,1,1,Terrain.UNKNOWN);
        set(raster,"cursor",1); set(raster,"sampledAt",100L);
        var reads=(Map<Long,java.util.concurrent.CompletableFuture<java.util.List<?>>>)field(SurfaceMapRenderer.class,"TILE_READS");
        var sample=((Object[])field(raster(null,0,0,1,1,Terrain.FOREST),"samples"))[0];
        var screen=SurfaceMapRenderer.class.getDeclaredField("screenRaster"); screen.setAccessible(true);
        var old=screen.get(null); screen.set(null,raster);
        try {
            reads.put(0L,java.util.concurrent.CompletableFuture.completedFuture(java.util.List.of(sample)));
            invoke(SurfaceMapRenderer.class,"applyTileReads");
            work().begin(6000);
            // World time has not advanced while the UI is open.
            invoke(SurfaceMapRenderer.class,"refreshRaster",raster,100L,50L);
            assertEquals(Terrain.FOREST,field(((Object[])field(raster,"samples"))[0],"terrain"));
        } finally { screen.set(null,old); reads.clear(); }
    }}
