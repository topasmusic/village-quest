package de.quest.client.screen;

import static org.mockito.Mockito.*;
import com.google.gson.*;
import de.quest.client.compat.GuiGraphics;
import de.quest.network.Payloads;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.*;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2fStack;
import org.junit.jupiter.api.Test;

/** Offline composition proof from real draw calls. AWT text is NOT the Minecraft font or Ingame approval. */
final class JournalV3RenderPreviewTest {
    @org.junit.jupiter.api.BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        for(var item:net.minecraft.core.registries.BuiltInRegistries.ITEM)
            item.builtInRegistryHolder().bindComponents(net.minecraft.core.component.DataComponentMap.builder()
                .set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE,64)
                .set(net.minecraft.core.component.DataComponents.ENCHANTMENTS,net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY)
                .set(net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS,net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY)
                .set(net.minecraft.core.component.DataComponents.ITEM_NAME,Component.translatable(item.getDescriptionId())).build());
    }
    private static final class Canvas {
        final BufferedImage image=new BufferedImage(700,440,BufferedImage.TYPE_INT_ARGB);
        final Matrix3x2fStack pose=new Matrix3x2fStack(24);
        final java.util.List<String> renderedText=new ArrayList<>();
        final Deque<Shape> clips=new LinkedList<>();Shape clip;
        static final Map<String,BufferedImage> textures=new HashMap<>();
        Graphics2D pen() {
            var p=image.createGraphics();p.setClip(clip);
            p.transform(new AffineTransform(pose.m00(),pose.m01(),pose.m10(),pose.m11(),pose.m20(),pose.m21()));
            p.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            return p;
        }
        GuiGraphics graphics() throws Exception {
            var g=mock(GuiGraphics.class);when(g.pose()).thenReturn(pose);
            doAnswer(a->{var p=pen();p.setColor(new Color((int)a.getArgument(4),true));int x=a.getArgument(0),y=a.getArgument(1);
                p.fillRect(x,y,(int)a.getArgument(2)-x,(int)a.getArgument(3)-y);p.dispose();return null;
            }).when(g).fill(anyInt(),anyInt(),anyInt(),anyInt(),anyInt());
            doAnswer(a->{String name=a.getArgument(1).toString();var tex=textures.get(name);
                if(tex==null) {try(var stream=getClass().getClassLoader().getResourceAsStream("assets/"+name.replace(':','/'))) {
                    if(stream==null)return null;tex=ImageIO.read(stream);textures.put(name,tex);}}
                int x=a.getArgument(2),y=a.getArgument(3),sw=a.getArgument(6),sh=a.getArgument(7);
                int u=Math.round((float)a.getArgument(4)),v=Math.round((float)a.getArgument(5));
                var p=pen();p.drawImage(tex,x,y,x+sw,y+sh,u,v,u+sw,v+sh,null);p.dispose();return null;
            }).when(g).blit(any(),any(),anyInt(),anyInt(),anyFloat(),anyFloat(),anyInt(),anyInt(),anyInt(),anyInt());
            doAnswer(a->{var p=pen();p.setColor(new Color((int)a.getArgument(4),true));p.setFont(new java.awt.Font("Monospaced",java.awt.Font.PLAIN,9));
                renderedText.add((String)a.getArgument(1));
                p.drawString((String)a.getArgument(1),(int)a.getArgument(2),(int)a.getArgument(3)+8);p.dispose();return 0;
            }).when(g).drawString(any(Font.class),anyString(),anyInt(),anyInt(),anyInt(),anyBoolean());
            var extractor=mock(net.minecraft.client.gui.GuiGraphicsExtractor.class);when(g.unwrap()).thenReturn(extractor);
            doAnswer(a->{var seq=(FormattedCharSequence)a.getArgument(1);var value=new StringBuilder();
                seq.accept((i,style,cp)->{value.appendCodePoint(cp);return true;});
                renderedText.add(value.toString());
                var p=pen();p.setColor(new Color((int)a.getArgument(4),true));p.setFont(new java.awt.Font("Monospaced",java.awt.Font.PLAIN,9));
                p.drawString(value.toString(),(int)a.getArgument(2),(int)a.getArgument(3)+8);p.dispose();return null;
            }).when(extractor).text(any(Font.class),any(FormattedCharSequence.class),anyInt(),anyInt(),anyInt(),anyBoolean());
            doAnswer(a->{clips.push(clip==null?new Rectangle(0,0,700,440):clip);
                var transform=new AffineTransform(pose.m00(),pose.m01(),pose.m10(),pose.m11(),pose.m20(),pose.m21());
                clip=transform.createTransformedShape(new Rectangle((int)a.getArgument(0),(int)a.getArgument(1),
                    (int)a.getArgument(2)-(int)a.getArgument(0),(int)a.getArgument(3)-(int)a.getArgument(1)));return null;
            }).when(g).enableScissor(anyInt(),anyInt(),anyInt(),anyInt());
            doAnswer(a->{clip=clips.pop();return null;}).when(g).disableScissor();return g;
        }
    }
    private Language language(String locale)throws Exception {
        var json=JsonParser.parseReader(new java.io.InputStreamReader(getClass().getClassLoader().getResourceAsStream(
                "assets/village-quest/lang/"+locale+".json"),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        String[] friendIds={"block.minecraft.dandelion","block.minecraft.poppy","block.minecraft.fern","item.minecraft.netherite_ingot","item.minecraft.redstone","item.minecraft.amethyst_shard"};
        String[] friendNames=locale.equals("de_de")?new String[]{"Löwenzahn","Mohn","Farn","Netheritbarren","Redstone","Amethystscherbe"}:locale.equals("es_es")?new String[]{"Diente de león","Amapola","Helecho","Lingote de netherita","Redstone","Fragmento de amatista"}:new String[]{"Dandelion","Poppy","Fern","Netherite Ingot","Redstone Dust","Amethyst Shard"};
        for(int i=0;i<friendIds.length;i++)json.addProperty(friendIds[i],friendNames[i]);
        json.addProperty("item.minecraft.potion.effect.swiftness",locale.equals("de_de")?"Trank der Schnelligkeit":locale.equals("es_es")?"Poción de rapidez":"Potion of Swiftness");
        json.addProperty("item.minecraft.enchanted_book",locale.equals("de_de")?"Verzaubertes Buch":locale.equals("es_es")?"Libro encantado":"Enchanted Book");
        json.addProperty("item.minecraft.honey_bottle",json.get("item.village-quest.weather_tonic").getAsString());
        json.addProperty("item.minecraft.gold_ingot",json.get("item.village-quest.village_watch_seal").getAsString());
        var lang=mock(Language.class);
        String[] itemNames=locale.equals("de_de")?new String[]{"Fass","Eichenholzzaun","Strohballen","Weizen","Buch"}:
            locale.equals("es_es")?new String[]{"Barril","Valla de roble","Bala de heno","Trigo","Libro"}:new String[]{"Barrel","Oak Fence","Hay Bale","Wheat","Book"};
        String[] ids={"barrel","oak_fence","hay_block","wheat","book"};for(int i=0;i<ids.length;i++)json.addProperty("block.minecraft."+ids[i],itemNames[i]);
        json.addProperty("item.minecraft.iron_pickaxe",locale.equals("de_de")?"Eisenspitzhacke":locale.equals("es_es")?"Pico de hierro":"Iron Pickaxe");
        json.addProperty("item.minecraft.bread",locale.equals("de_de")?"Brot":locale.equals("es_es")?"Pan":"Bread");
        json.addProperty("block.minecraft.coal_ore",locale.equals("de_de")?"Kohleerz":locale.equals("es_es")?"Mena de carbón":"Coal Ore");
        json.addProperty("entity.minecraft.sheep",locale.equals("de_de")?"Schafe":locale.equals("es_es")?"Ovejas":"Sheep");
        json.addProperty("item.minecraft.wheat",itemNames[3]);json.addProperty("item.minecraft.book",itemNames[4]);
        when(lang.getOrDefault(anyString())).thenAnswer(a->json.has((String)a.getArgument(0))?json.get((String)a.getArgument(0)).getAsString():a.getArgument(0));
        when(lang.getOrDefault(anyString(),anyString())).thenAnswer(a->json.has((String)a.getArgument(0))?json.get((String)a.getArgument(0)).getAsString():a.getArgument(1));
        when(lang.has(anyString())).thenAnswer(a->json.has((String)a.getArgument(0)));
        when(lang.getVisualOrder(any(FormattedText.class))).thenAnswer(a->FormattedCharSequence.forward(((FormattedText)a.getArgument(0)).getString(),Style.EMPTY));
        return lang;
    }
    @Test void exportActualJournalDrawCommandsForReferenceReview()throws Exception {
        var previous=Language.getInstance();
        var crown=de.quest.registry.ModItems.CROWN;var silver=de.quest.registry.ModItems.SILVERMARK;var tonic=de.quest.registry.ModItems.WEATHER_TONIC;var seal=de.quest.registry.ModItems.VILLAGE_WATCH_SEAL;
        de.quest.registry.ModItems.CROWN=net.minecraft.world.item.Items.GOLD_NUGGET;
        de.quest.registry.ModItems.SILVERMARK=net.minecraft.world.item.Items.IRON_NUGGET;
        de.quest.registry.ModItems.WEATHER_TONIC=net.minecraft.world.item.Items.HONEY_BOTTLE;de.quest.registry.ModItems.VILLAGE_WATCH_SEAL=net.minecraft.world.item.Items.GOLD_INGOT;
        try(var client=mockStatic(Minecraft.class);
            var tutorial=mockStatic(de.quest.client.ui.InventoryJournalTutorialState.class);
            var tour=mockStatic(UiTestTour.class);
            var surface=mockStatic(de.quest.client.ui.SurfaceMapRenderer.class);
            var kingdom=mockStatic(de.quest.client.ui.KingdomUiAssets.class,a->a.getMethod().getName().equals("money")?null:a.callRealMethod());
            var theme=mockStatic(de.quest.client.ui.VillageUiTheme.class,org.mockito.Answers.CALLS_REAL_METHODS)) {
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
            tour.when(UiTestTour::active).thenReturn(true);
            var friendRegistries=net.minecraft.data.registries.VanillaRegistries.createLookup();
            for(String locale:java.util.List.of("de_de","en_us","es_es")) {
                Language.inject(language(locale));
                var font=mock(Font.class);when(font.width(anyString())).thenAnswer(a->((String)a.getArgument(0)).length()*6);
                when(font.split(any(FormattedText.class),anyInt())).thenAnswer(a->{
                    String value=((FormattedText)a.getArgument(0)).getString();int max=Math.max(1,(int)a.getArgument(1));
                    var result=new ArrayList<FormattedCharSequence>();String line="";
                    for(String word:value.split("\\s+")) {String next=line.isEmpty()?word:line+" "+word;
                        if(!line.isEmpty() && next.length()*6>max) {result.add(FormattedCharSequence.forward(line,Style.EMPTY));line=word;} else line=next;}
                    if(!line.isEmpty())result.add(FormattedCharSequence.forward(line,Style.EMPTY));return result;
                });
                when(font.plainSubstrByWidth(anyString(),anyInt())).thenAnswer(a -> a.getArgument(0));
                when(font.plainSubstrByWidth(anyString(),anyInt(),anyBoolean())).thenAnswer(a -> a.getArgument(0));
                var lineHeight=Font.class.getDeclaredField("lineHeight");lineHeight.setAccessible(true);lineHeight.setInt(font,9);
                var c=Component.literal("Oakfield");
                var data=new JournalScreenData(37,12,4,2,240,12,8,5,18,3,true,false,false,false,true,true,
                    true,Component.translatable("quest.village-quest.daily.wheat.title"),Component.literal("Weizen: 12/32"),
                    true,Component.translatable("quest.village-quest.daily.coal.title"),Component.literal("Kohle: 4/16"),
                    false,c,c,false,c,c,false,c,c,true,false,true,true,false,true,true,true,java.util.List.of(),c,
                    new Payloads.NetworkSummaryData(3,1,0,c,c,c),java.util.List.of(),java.util.List.of());
                for(String section:locale.equals("de_de")?java.util.List.of("overview","network","quests","guide"):java.util.List.of("overview")) {
                    var screen=new JournalScreen(data);screen.width=700;screen.height=440;screen.selectTourSection(section);
                    var f=Screen.class.getDeclaredField("font");f.setAccessible(true);f.set(screen,font);
                    var canvas=new Canvas();screen.render(canvas.graphics(),0,0,0);
                    var folder=Path.of("build/reports/journal-v3-preview");Files.createDirectories(folder);
                    ImageIO.write(canvas.image,"png",folder.resolve(locale+"-"+section+".png").toFile());
                    theme.clearInvocations();kingdom.clearInvocations();tour.clearInvocations();client.clearInvocations();
                }
                var building=new de.quest.network.BuildingPayloads.View(UUID.randomUUID(),0,true,net.minecraft.core.BlockPos.ZERO,
                    de.quest.kingdom.BuildingScanner.Role.COUNCIL,de.quest.kingdom.BuildingScanner.Range.BASIC,
                    de.quest.kingdom.BuildingScanner.Status.VALID,UUID.randomUUID(),0,UUID.randomUUID(),0,false,false,3,3,true,"Oakfield",
                    java.util.List.of(),java.util.List.of(),"",de.quest.network.BuildingPayloads.MarketLayout.EMPTY,
                    de.quest.network.BuildingPayloads.Recognition.EMPTY,de.quest.network.BuildingPayloads.Diplomacy.EMPTY);
                var extra=new java.util.LinkedHashMap<String,CompatScreen>();
                for(String page:java.util.List.of("TRUST","CHARTERS")) {
                    var atlas=(JournalScreen)UiTestTourScreens.create(new de.quest.commands.UiTestTourSteps.Step("journal","atlas"));
                    var field=JournalScreen.class.getDeclaredField("atlasPage");field.setAccessible(true);
                    for(Object value:field.getType().getEnumConstants()) if(value.toString().equals(page))field.set(atlas,value);
                    extra.put("atlas-"+page.toLowerCase(java.util.Locale.ROOT),atlas);
                }
                for(int i=0;i<UiTestTourScreens.nodes().size();i++) {
                    String id=UiTestTourScreens.nodes().get(i).nodeId();
                    var path=new GuildPathScreen(new Payloads.GuildPathPayload(-1,240,UiTestTourScreens.nodes()));
                    for(String fieldName:java.util.List.of("selected","mapInitialized")) {
                        var field=GuildPathScreen.class.getDeclaredField(fieldName);field.setAccessible(true);
                        if(fieldName.equals("selected"))field.setInt(path,i);else field.setBoolean(path,true);
                    }
                    var center=GuildPathScreen.class.getDeclaredMethod("centerOnIndex",int.class);center.setAccessible(true);center.invoke(path,i);
                    extra.put("path-"+id,path);
                    var atlas=(JournalScreen)UiTestTourScreens.create(new de.quest.commands.UiTestTourSteps.Step("journal","atlas"));
                    var nodesMethod=JournalScreen.class.getDeclaredMethod("atlasNodes");nodesMethod.setAccessible(true);
                    var nodes=(java.util.List<?>)nodesMethod.invoke(atlas);Object node=nodes.get(i);
                    var landmarkMethod=node.getClass().getDeclaredMethod("landmark");landmarkMethod.setAccessible(true);Object landmark=landmarkMethod.invoke(node);
                    var initialized=JournalScreen.class.getDeclaredField("atlasInitialized");initialized.setAccessible(true);((boolean[])initialized.get(atlas))[0]=true;
                    for(String axis:java.util.List.of("X","Y")) {
                        var coordinate=landmark.getClass().getDeclaredMethod(axis.toLowerCase(java.util.Locale.ROOT));coordinate.setAccessible(true);
                        float fraction=(float)coordinate.invoke(landmark);
                        var offset=JournalScreen.class.getDeclaredField("atlasOffset"+axis);offset.setAccessible(true);
                        ((double[])offset.get(atlas))[0]=(axis.equals("X")?388/2.:168/2.)-fraction*(axis.equals("X")?780:390);
                    }
                    var clamp=JournalScreen.class.getDeclaredMethod("clampAtlasOffset");clamp.setAccessible(true);clamp.invoke(atlas);
                    var hovered=JournalScreen.class.getDeclaredField("atlasHoveredNode");hovered.setAccessible(true);hovered.setInt(atlas,i);
                    extra.put("atlas-path-"+id,atlas);
                }
                for(boolean editable:new boolean[]{false,true}) extra.put("setup-"+(editable?"owner":"member"),new QuestSetupScreen(
                    new de.quest.network.QuestSetupPayloads.View(UUID.randomUUID(),true,editable,de.quest.config.WorldQuestSettings.recommended(),true,false,0)));
                extra.put("building",new BuildingScreen(building));
                extra.put("diagnosis",new VillageCheckScreen(new de.quest.network.VillageCheckPayload("minecraft:overworld",java.util.List.of(
                    new de.quest.network.VillageCheckPayload.Marker(net.minecraft.core.BlockPos.ZERO,de.quest.kingdom.BuildingScanner.Status.VALID)))));
                extra.put("reward",new VanillaRewardScreen(new de.quest.network.RewardPayloads.View(UUID.randomUUID(),"daily:SMITH_SMELTING",
                    java.util.List.of(new de.quest.network.RewardPayloads.Choice("supplies",java.util.List.of(Component.literal("16 iron ingots"))),
                    new de.quest.network.RewardPayloads.Choice("coins",java.util.List.of(Component.literal("12 Silvermarks")))),"supplies"),null));
                extra.put("admin",new AdminJournalScreen(java.util.List.of("Village Quest", "[Preview] Offline composition fixture", "Source data remains server-owned")));
                for(var entry:extra.entrySet()) {
                    var screen=entry.getValue();
                    if(screen instanceof QuestSetupScreen) { screen=spy(screen);doNothing().when(screen).renderBackground(any(),anyInt(),anyInt(),anyFloat()); }
                    screen.width=700;screen.height=440;
                    var f=Screen.class.getDeclaredField("font");f.setAccessible(true);f.set(screen,font);
                    if(screen instanceof AdminJournalScreen admin)admin.init();
                    var canvas=new Canvas();screen.render(canvas.graphics(),0,0,0);
                    var folder=Path.of("build/reports/extra-v3-preview");Files.createDirectories(folder);
                    ImageIO.write(canvas.image,"png",folder.resolve(locale+"-"+entry.getKey()+".png").toFile());
                    theme.clearInvocations();kingdom.clearInvocations();tour.clearInvocations();client.clearInvocations();surface.clearInvocations();
                }
                for(int step:java.util.stream.IntStream.range(0,de.quest.commands.UiTestTourSteps.ALL.size()).toArray()) {
                    var tourStep=de.quest.commands.UiTestTourSteps.ALL.get(step);
                    var screen=tourStep.family().equals("friends")?new FriendsQuestScreen(FriendsQuestPreview.view(tourStep.state(),friendRegistries)):UiTestTourScreens.create(tourStep);
                    screen.width=700;screen.height=440;
                    if(screen instanceof QuestMasterScreen q){var d=QuestMasterScreen.class.getDeclaredField("data");d.setAccessible(true);q.updateData((QuestMasterScreen.QuestMasterData)d.get(q));}
                    var f=Screen.class.getDeclaredField("font");f.setAccessible(true);f.set(screen,font);
                    if(screen instanceof PilgrimTradeScreen pilgrim)pilgrim.init();
                    if(screen instanceof BuildingScreen buildingPreview)buildingPreview.init();
                    if(screen instanceof AdminJournalScreen admin)admin.init();
                    boolean resetPreview=de.quest.commands.UiTestTourSteps.ALL.get(step).family().equals("journal")
                            && de.quest.commands.UiTestTourSteps.ALL.get(step).state().equals("quests_reset");
                    if(resetPreview) {
                        var expanded=JournalScreen.class.getDeclaredField("expandedCardId");expanded.setAccessible(true);expanded.set(screen,"daily_reset");
                    }
                    var canvas=new Canvas();((CompatScreen)screen).render(canvas.graphics(),0,0,0);
                    for(String drawn:canvas.renderedText) org.junit.jupiter.api.Assertions.assertFalse(
                            drawn.matches(".*(?:screen|reward|message|text|quest)\\.village-quest\\..*"),
                            locale+" tour "+(step+1)+" contains an unresolved translation: "+drawn);
                    var folder=Path.of("build/reports/family-v3-preview");Files.createDirectories(folder);
                    ImageIO.write(canvas.image,"png",folder.resolve(locale+"-tour-"+(step+1)+".png").toFile());
                    if(resetPreview) {
                        var expanded=JournalScreen.class.getDeclaredField("expandedCardId");expanded.setAccessible(true);expanded.set(screen,"weekly_reset");
                        var weeklyCanvas=new Canvas();((CompatScreen)screen).render(weeklyCanvas.graphics(),0,0,0);
                        for(String drawn:weeklyCanvas.renderedText) org.junit.jupiter.api.Assertions.assertFalse(
                                drawn.matches(".*(?:screen|reward|message|text|quest)\\.village-quest\\..*"),locale+" expanded weekly contains an unresolved translation: "+drawn);
                        ImageIO.write(weeklyCanvas.image,"png",folder.resolve(locale+"-weekly-reset-expanded.png").toFile());
                    }
                    theme.clearInvocations();kingdom.clearInvocations();tour.clearInvocations();client.clearInvocations();surface.clearInvocations();
                }
            }
        }finally{Language.inject(previous);de.quest.registry.ModItems.CROWN=crown;de.quest.registry.ModItems.SILVERMARK=silver;de.quest.registry.ModItems.WEATHER_TONIC=tonic;de.quest.registry.ModItems.VILLAGE_WATCH_SEAL=seal;}
    }
}
