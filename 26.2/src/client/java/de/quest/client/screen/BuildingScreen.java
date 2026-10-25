package de.quest.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import de.quest.client.compat.GuiGraphics;
import de.quest.client.ui.VillageUiTheme;
import de.quest.client.ui.KingdomUiAssets;
import de.quest.client.ui.JournalV3Visuals;
import de.quest.client.ui.KingdomUiHints;
import de.quest.kingdom.BuildingScanner;
import de.quest.network.BuildingPayloads;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;

/** Private checklist: the server supplies evidence, choices and permissions. */
public final class BuildingScreen extends CompatScreen {
    private final KingdomUiHints hints=new KingdomUiHints();
    private static final String TEXT = "screen.village-quest.building.";
    private static final int W = 448, H = 366, INK = 0xFF3E2918, MUTED = 0xFF80694F;
    private static List<BlockPos> highlights = List.of();
    private static ClientLevel highlightLevel;
    private static int highlightTicks;
    private BuildingPayloads.View data;
    private final List<Control> controls = new ArrayList<>();
    private int page;
    private EditBox nameField;
    private BuildingPayloads.Kind naming;
    private boolean releasing, closed;
    private boolean namingPending;
    private boolean marketEditing, villageEditing;
    private boolean travelEditing;
    private int travelIndex,travelDetailPage;
    private boolean projectEditing;
    private boolean diplomacyEditing;
    private boolean crisisEditing;
    private boolean relationEditing,relationHome,relationDetails;
    private boolean relationList=true;
    private int relationDetailPage;
    private int partnerIndex,dealType,dealTerms;
    private long offerPendingRevision=-1;
    private int offerPendingTicks,offerPendingType;
    private UUID offerPendingPartner;
    private BuildingPayloads.Deal offerPendingDeal;
    private int placeSlot,placePage;
    private int checkHelp=-1;

    public BuildingScreen(BuildingPayloads.View data) {
        super(Component.translatable(TEXT + "title")); this.data = data;
    }
    void selectTourPage(String page) {
        marketEditing=page.equals("places"); travelEditing=page.equals("travel"); projectEditing=page.equals("projects");
        relationEditing=page.equals("relations") || page.equals("relations_net"); relationList=!page.equals("relations_net");
        diplomacyEditing=page.equals("diplomacy"); crisisEditing=page.equals("crisis"); villageEditing=page.equals("village_choice");
        releasing=page.equals("release"); if(page.equals("check_help")) checkHelp=0;
        if(page.equals("naming")) naming=BuildingPayloads.Kind.RENAME;
    }
    public UUID session() { return data.session(); }
    public void updateData(BuildingPayloads.View updated) {
        if (!updated.session().equals(session()) || updated.revision() < data.revision()) return;
        if(offerPendingRevision>=0 && updated.revision()>offerPendingRevision && offerPendingPartner!=null)
            updated.diplomacy().partners().stream().filter(p -> p.village().equals(offerPendingPartner))
                    .filter(p -> !p.deals().get(offerPendingType).equals(offerPendingDeal)).findFirst()
                    .ifPresent(p -> { offerPendingRevision=-1;offerPendingTicks=0;offerPendingPartner=null;offerPendingDeal=null; });
        if(naming!=null && (!updated.selectedVillage().equals(data.selectedVillage())
                || naming!=BuildingPayloads.Kind.FOUND && !updated.manageable())) cancelName();
        if(naming==BuildingPayloads.Kind.CONFIRM && updated.binding()==BuildingPayloads.Binding.REGISTERED) cancelName();
        // A routine UNKNOWN/disabled preview is not a cancellation of the player's draft.
        if((naming==BuildingPayloads.Kind.FOUND || naming==BuildingPayloads.Kind.CONFIRM) && namingPending) namingPending=updated.feedback().equals(TEXT+"checking");
        UUID previousTraveler=data.marketLayout().travel().isEmpty()?BuildingPayloads.NONE:data.marketLayout().travel().get(Math.min(travelIndex,data.marketLayout().travel().size()-1)).person();
        UUID previous=data.diplomacy().partners().isEmpty()?BuildingPayloads.NONE:data.diplomacy().partners().get(Math.min(partnerIndex,data.diplomacy().partners().size()-1)).village();
        data = updated; page = Math.min(page, Math.max(0, (data.villages().size() - 1) / 4));
        for(int i=0;i<data.marketLayout().travel().size();i++) if(data.marketLayout().travel().get(i).person().equals(previousTraveler)) { travelIndex=i; break; }
        for(int i=0;i<data.diplomacy().partners().size();i++) if(data.diplomacy().partners().get(i).village().equals(previous)) { partnerIndex=i; break; }
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override protected boolean suppressInventoryKeyClose() { return naming != null && nameField != null && nameField.isFocused(); }
    @Override protected void init() {
        String old = nameField == null ? data.name() : nameField.getValue();
        nameField = new EditBox(font, (width-W)/2+16+80, (height-H)/2+10+159, 256, 20, Component.translatable(TEXT + "name"));
        nameField.setMaxLength(96); nameField.setBordered(false); nameField.setTextColor(INK);
        nameField.setTextShadow(false); nameField.setTextColorUneditable(MUTED); nameField.setValue(old);
        nameField.setVisible(naming != null); addRenderableWidget(nameField);
    }
    private static String text(String suffix, Object... args) { return Component.translatable(TEXT + suffix, args).getString(); }
    private void line(GuiGraphics g, String value, int x, int y, int width, int color) {
        VillageUiTheme.drawStringScaled(g, font, VillageUiTheme.ellipsize(font, value, (int)(width/.8f)), x, y, color, .8f);
    }
    private void button(GuiGraphics g, int left, int top, int x, int y, int w, String label, boolean enabled,
                        boolean active, int mx, int my, Runnable action) {
        if(label.equals(text("close"))) {
            var box=KingdomUiAssets.closeBounds(left-16,top-10,W,H);
            KingdomUiAssets.closeButton(g,left-16,top-10,W,H,box.contains(mx,my));
            if(box.contains(mx,my)) hints.setTooltipForNextFrame(font,Component.literal(text(naming!=null||releasing||marketEditing||villageEditing||travelEditing||projectEditing||diplomacyEditing||crisisEditing||relationEditing||checkHelp>=0?"back":"close")),mx,my);
            controls.add(new Control(box.x(),box.y(),box.width(),enabled,action,box.height())); return;
        }
        if(label.equals(text(data.range()==BuildingScanner.Range.BASIC?"extend":"basic")) && within(mx,my,left+x,top+y+24,w,18))
            hints.setTooltipForNextFrame(font,Component.literal(text("scan_dimensions",2*data.range().horizontal+1,2*data.range().vertical+1,
                    2*data.range().horizontal+1,data.range().horizontal,data.range().vertical)),mx,my);
        if(y>=42)y+=24;
        boolean hover = within(mx, my, left+x, top+y, w, 18);
        VillageUiTheme.drawButton(g, font, left+x, top+y, w, 18, label, enabled, hover, active);
        if (hover) {
            String reason="";
            if(!enabled) {
                if(label.equals(text("found"))) reason=foundingNext();
                else if(label.equals(buildingAction())) reason=text(data.feedback().equals(TEXT+"checking")?"checking":data.selectedVillage().equals(BuildingPayloads.NONE)?"auto_no_village":!data.manageable()?"mandate_required":"found_missing");
                else if(label.equals(text("recognition_confirm"))) reason=text(!data.manageable()?"mandate_required":"recognition_missing");
                else if(label.equals(text("save")) && namingPending) reason=text("name_checking");
                else if(label.equals(text("mark"))) reason=text("check_no_targets");
                else if(label.equals(text("release")) || label.equals(text("places"))) reason=text("anchor_required");
            }
            hints.setTooltipForNextFrame(font,Component.literal(label+(reason.isBlank()?"":"\n"+reason)),mx,my);
            if(label.equals(text(data.range()==BuildingScanner.Range.BASIC?"extend":"basic")))
                hints.setTooltipForNextFrame(font,Component.literal(label+"\n"+text("scan_dimensions",2*data.range().horizontal+1,
                        2*data.range().vertical+1,2*data.range().horizontal+1,data.range().horizontal,data.range().vertical)),mx,my);
        }
        controls.add(new Control(left+x, top+y, w, enabled, action));
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        VillageUiTheme.drawScreenShade(g, width, height); controls.clear(); hints.clear();
        int mx = responsiveMouseX(mouseX, W, H), my = responsiveMouseY(mouseY, W, H);
        float scale = beginResponsivePanel(g, W, H);
        try {
            int l = (width-W)/2+16, t = (height-H)/2+10;
            JournalV3Visuals.insetBoard(g,l-16,t-10,W,H);
            if(data.role()==BuildingScanner.Role.COUNCIL) renderCouncil(g,l,t,mx,my);
            else renderAnchor(g,l,t,mx,my);
            if (naming!=null || releasing) {
                controls.clear(); subpage(g,l,t,text(naming!=null?"name":"release"),KingdomUiAssets.DetailIcon.THRONE); KingdomUiAssets.card(g,l+62,t+122,292,110,false);
                button(g,l,t,376,10,24,text("close"),true,false,mx,my,() -> { cancelName(); releasing=false; });
                if (naming!=null) {
                    line(g,text("name"),l+80,t+137,256,INK);
                    g.fill(l+76,t+154,l+340,t+177,0xFFE2CFAB);
                    boolean validName=de.quest.kingdom.LivingKingdomsState.validName(nameField.getValue());
                    String normalized=java.text.Normalizer.normalize(nameField.getValue().strip(),java.text.Normalizer.Form.NFC);
                    line(g,text("name_count",normalized.codePointCount(0,normalized.length())),l+244,t+137,92,validName?MUTED:0xFF9A3D32);
                    VillageUiTheme.drawWrappedScaled(g,font,text(validName?"name_help":"name_invalid"),l+80,t+182,256,
                            validName?MUTED:0xFF9A3D32,.72f,2);
                    button(g,l,t,79,183,123,text("save"),validName&&!namingPending,false,mx,my,this::saveName);
                    if(naming==BuildingPayloads.Kind.FOUND || naming==BuildingPayloads.Kind.CONFIRM) {
                        String progress=namingPending?text("name_checking"):!data.feedback().isBlank()?Component.translatable(data.feedback()).getString():naming==BuildingPayloads.Kind.FOUND&&!data.canFound()?foundingNext():text("name_submit_hint");
                        VillageUiTheme.drawWrappedScaled(g,font,progress,l+70,t+238,276,INK,.72f,4);
                    }
                } else {
                    VillageUiTheme.drawWrappedScaled(g,font,text("release_question"),l+80,t+137,256,INK,.8f,4);
                    button(g,l,t,79,183,123,text("release"),true,false,mx,my,() -> { action(BuildingPayloads.Kind.RELEASE); releasing=false; });
                }
                button(g,l,t,214,183,123,text("cancel"),true,false,mx,my,() -> { cancelName(); releasing=false; });
            }
            if(villageEditing) renderVillageChoices(g,l,t,mx,my);
            if(marketEditing) renderMarketPlaces(g,l,t,mx,my);
            if(travelEditing) renderMarketTravel(g,l,t,mx,my);
            if(projectEditing) renderProjects(g,l,t,mx,my);
            if(diplomacyEditing) renderDiplomacy(g,l,t,mx,my);
            if(crisisEditing) renderCrisis(g,l,t,mx,my);
            if(relationEditing) renderRelations(g,l,t,mx,my);
            if(checkHelp>=0) renderCheckHelp(g,l,t,mx,my);
            super.render(g,mx,my,delta);
            hints.draw(g,font,l,t,W,H);
        } finally { endResponsivePanel(g,scale); }
    }
    private void subpage(GuiGraphics g,int l,int t,String title,KingdomUiAssets.DetailIcon icon) {
        hints.clear();
        JournalV3Visuals.insetBoard(g,l-16,t-10,W,H); KingdomUiAssets.managementHeader(g,font,title,icon,l-16,t-10,W);
        KingdomUiAssets.card(g,l+26,t+94,363,190,false);
    }
    private void renderAnchor(GuiGraphics g,int l,int t,int mx,int my) {
        KingdomUiAssets.managementHeader(g,font,text("role."+data.role().name().toLowerCase(Locale.ROOT)),KingdomUiAssets.buildingIcon(data.role()),l-16,t-10,W);
        button(g,l,t,376,10,24,text("close"),true,false,mx,my,this::onClose);
        line(g,buildingStatus(),l+18,t+72,380,data.status()==BuildingScanner.Status.VALID?0xFF306C42:MUTED);
        if(within(mx,my,l+18,t+66,380,20)) hints.setTooltipForNextFrame(font,List.of(
                Component.literal(buildingStatus()),Component.literal(membershipHelp())),mx,my);
        KingdomUiAssets.card(g,l+18,t+92,228,153,false);
        KingdomUiAssets.section(g,font,text("building_check"),l+18,t+92,228,data.status()==BuildingScanner.Status.VALID);
        for(int i=0;i<data.rows().size();i++) drawCheckRow(g,l,t,mx,my,i,228);
        if(data.rows().isEmpty()) VillageUiTheme.drawWrappedScaled(g,font,text("checking"),l+26,t+120,212,MUTED,.8f,4);
        KingdomUiAssets.card(g,l+254,t+92,144,217,false);
        KingdomUiAssets.section(g,font,text("village_membership"),l+254,t+92,144,registered());
        boolean selected=!data.selectedVillage().equals(BuildingPayloads.NONE),official=registered();
        VillageUiTheme.drawWrappedScaled(g,font,selected?data.name():text("unassigned"),l+262,t+117,128,INK,.8f,3);
        VillageUiTheme.drawWrappedScaled(g,font,membershipHelp(),l+262,t+144,128,MUTED,.72f,5);
        if(needsVillageChoice()) button(g,l,t,262,168,128,text("choose_village"),true,false,mx,my,() -> villageEditing=true);
        if(data.binding()!=BuildingPayloads.Binding.REGISTERED) button(g,l,t,262,190,128,buildingAction(),selected&&data.manageable()&&data.status()==BuildingScanner.Status.VALID&&!data.feedback().equals(TEXT+"checking"),false,mx,my,() -> action(BuildingPayloads.Kind.CONFIRM));
        else VillageUiTheme.drawWrappedScaled(g,font,text("registered_to",data.name()),l+262,t+204,128,0xFF306C42,.75f,3);
        if(selected&&!data.manageable()) button(g,l,t,262,212,128,text("mandate"),true,false,mx,my,() -> action(BuildingPayloads.Kind.MANDATE));
        if(data.role()==BuildingScanner.Role.MARKET&&official) button(g,l,t,262,234,128,text("places"),true,false,mx,my,() -> marketEditing=true);
        if(data.manageable()&&official) button(g,l,t,262,256,128,text("release"),true,false,mx,my,() -> releasing=true);
        button(g,l,t,18,226,110,text("recheck"),true,false,mx,my,() -> action(BuildingPayloads.Kind.RECHECK));
        button(g,l,t,134,226,112,text(data.range()==BuildingScanner.Range.BASIC?"extend":"basic"),true,false,mx,my,() -> action(BuildingPayloads.Kind.EXTEND));
        if (!data.innOpening().getString().isBlank()) {
            VillageUiTheme.drawWrappedScaled(g,font,data.innOpening().getString(),l+22,t+251,220,INK,.72f,3);
            if(within(mx,my,l+18,t+249,228,25)) hints.setTooltipForNextFrame(font,List.of(data.innOpening(),
                    Component.translatable("screen.village-quest.inn.opening.conditions")),mx,my);
        }
        String feedback=data.feedback().isBlank()?(data.status()!=BuildingScanner.Status.VALID?text("found_missing"):""):Component.translatable(data.feedback()).getString();
        VillageUiTheme.drawWrappedScaled(g,font,feedback,l+22,t+277,220,INK,.72f,4);
        if(within(mx,my,l+18,t+273,228,39)) hints.setTooltipForNextFrame(font,Component.literal(feedback),mx,my);
    }
    private boolean registered() { return data.binding()==BuildingPayloads.Binding.REGISTERED || data.binding()==BuildingPayloads.Binding.CHANGED; }
    private boolean needsVillageChoice() { return !registered() && data.villages().size()>1; }
    private String buildingAction() {
        return text(switch(data.binding()) {
            case CHANGED -> "apply_area";
            case ELSEWHERE -> "move_building";
            default -> data.role()==BuildingScanner.Role.COUNCIL?"open_council":"open_building";
        },text("role."+data.role().name().toLowerCase(Locale.ROOT)));
    }
    private String buildingStatus() {
        return registered()?text(data.status()==BuildingScanner.Status.VALID?"registered_ready":data.status()==BuildingScanner.Status.UNKNOWN?"registered_checking":"registered_repairs")
                :text("status."+data.status().name().toLowerCase(Locale.ROOT));
    }
    private String membershipHelp() {
        return text(data.binding()==BuildingPayloads.Binding.CHANGED?"area_pending":registered()?"membership_saved":data.villages().isEmpty()?"auto_no_village":data.selectedVillage().equals(BuildingPayloads.NONE)?"auto_ambiguous":!data.manageable()?"mandate_required":"auto_detected");
    }
    private void drawCheckRow(GuiGraphics g,int l,int t,int mx,int my,int i,int w) {
        var row=data.rows().get(i); int y=89+i*16;
        if(i%2==1) g.fill(l+20,t+y+24-1,l+16+w,t+y+24+15,0x18A68C56);
        drawCheckIcon(g,row.check(),l+22,t+y+24-1);
        String label=text("check."+row.check().name().toLowerCase(Locale.ROOT));
        VillageUiTheme.drawWrappedScaled(g,font,label,l+40,t+y+24,w-72,INK,.7f,2);
        line(g,row.found()+"/"+row.required(),l+w-27,t+y+24+3,26,row.passed()?0xFF306C42:0xFF9A3D32);
        controls.add(new Control(l+20,t+y+24-1,w-4,true,() -> checkHelp=i,16));
        if(within(mx,my,l+20,t+y+24-1,w-4,16)) hints.setTooltipForNextFrame(font,List.of(Component.literal(label),
                Component.literal(checkHint(data.role(),row.check())),Component.translatable(TEXT+"open_details")),mx,my);
    }
    public static String checkHint(BuildingScanner.Role role,BuildingScanner.Check check) {
        String key=check.name().toLowerCase(Locale.ROOT);
        if(check==BuildingScanner.Check.SEATS || check==BuildingScanner.Check.CONTAINERS)
            return text("hint."+role.name().toLowerCase(Locale.ROOT)+"."+key);
        return text("hint."+key);
    }
    private String foundingNext() {
        if(data.status()!=BuildingScanner.Status.VALID) return text("found_missing");

        return text(data.canFound()?"found_ready":"found_locked");
    }
    private void renderCouncil(GuiGraphics g,int l,int t,int mx,int my) {
        boolean founding=data.selectedVillage().equals(BuildingPayloads.NONE) && data.villages().isEmpty();
        KingdomUiAssets.managementHeader(g,font,text("council_title"),KingdomUiAssets.DetailIcon.THRONE,l-16,t-10,W);
        button(g,l,t,376,10,24,text("close"),true,false,mx,my,this::onClose);
        VillageUiTheme.drawWrappedScaled(g,font,text(founding?"council_intro":registered()?"council_registered_intro":"council_open_intro"),l+18,t+69,246,INK,.7f,2);
        if(needsVillageChoice()) button(g,l,t,278,44,120,text("choose_village"),true,false,mx,my,() -> villageEditing=true);
        KingdomUiAssets.card(g,l+18,t+92,180,119,false);
        KingdomUiAssets.card(g,l+206,t+92,192,119,false);
        KingdomUiAssets.section(g,font,text("building_check"),l+18,t+92,180,data.status()==BuildingScanner.Status.VALID);
        KingdomUiAssets.section(g,font,text(founding?"population_later":"village_membership"),l+206,t+92,192,founding?data.canFound():registered());
        for(int i=0;i<data.rows().size();i++) drawCheckRow(g,l,t,mx,my,i,180);
        if(data.rows().isEmpty()) VillageUiTheme.drawWrappedScaled(g,font,text("checking"),l+28,t+123,160,MUTED,.8f,4);
        if(founding) {
            requirement(g,l,t,89,KingdomUiAssets.DetailIcon.ADULT,text("require_adults"),data.foundingObserved()?data.adults()+" / 3":"?",data.adults()>=3&&data.foundingObserved());
            requirement(g,l,t,109,KingdomUiAssets.DetailIcon.BED,text("require_beds"),data.foundingObserved()?data.beds()+" / 3":"?",data.beds()>=3&&data.foundingObserved());
            requirement(g,l,t,129,KingdomUiAssets.DetailIcon.JOB,text("require_job"),data.foundingObserved()?(data.job()?"1 / 1":"0 / 1"):"?",data.job()&&data.foundingObserved());
            VillageUiTheme.drawWrappedScaled(g,font,text("founding_help"),l+214,t+175,176,INK,.7f,4);
            if(within(mx,my,l+210,t+111,184,58)) hints.setTooltipForNextFrame(font,Component.literal(text(my<t+131?"found_adults":my<t+151?"found_beds":"found_job")),mx,my);
        } else {
            KingdomUiAssets.detailIcon(g,KingdomUiAssets.DetailIcon.THRONE,l+214,t+114,24);
            VillageUiTheme.drawWrappedScaled(g,font,data.name(),l+244,t+116,144,INK,.8f,3);
            VillageUiTheme.drawWrappedScaled(g,font,membershipHelp(),l+214,t+155,176,INK,.75f,6);
        }
        button(g,l,t,18,191,120,text("recheck"),true,false,mx,my,() -> action(BuildingPayloads.Kind.RECHECK));
        button(g,l,t,144,191,120,text(data.range()==BuildingScanner.Range.BASIC?"extend":"basic"),true,false,mx,my,() -> action(BuildingPayloads.Kind.EXTEND));
        KingdomUiAssets.section(g,font,text(founding?"found_step":registered()?"building_state":"open_step"),l+18,t+240,246,registered());
        boolean ready=data.status()==BuildingScanner.Status.VALID && (founding?data.canFound():!data.selectedVillage().equals(BuildingPayloads.NONE)&&data.manageable());
        if(data.binding()!=BuildingPayloads.Binding.REGISTERED) button(g,l,t,18,242,246,founding?text("found"):buildingAction(),ready&&!data.feedback().equals(TEXT+"checking"),false,mx,my,
                () -> { if(founding) name(BuildingPayloads.Kind.FOUND); else if(data.binding()==BuildingPayloads.Binding.UNREGISTERED) name(BuildingPayloads.Kind.CONFIRM); else action(BuildingPayloads.Kind.CONFIRM); });
        else VillageUiTheme.drawWrappedScaled(g,font,text("registered_to",data.name()),l+22,t+266,238,0xFF306C42,.8f,2);
        String feedback=data.feedback().isBlank()?(founding?foundingNext():registered()?buildingStatus():membershipHelp()):Component.translatable(data.feedback()).getString();
        VillageUiTheme.drawWrappedScaled(g,font,feedback,l+22,t+289,238,ready?0xFF306C42:INK,.7f,3);
        if(within(mx,my,l+18,t+287,246,25)) hints.setTooltipForNextFrame(font,Component.literal(feedback),mx,my);
        if(founding) {
            KingdomUiAssets.card(g,l+278,t+221,120,88,false);
            VillageUiTheme.drawWrappedScaled(g,font,text("found_next"),l+286,t+229,104,INK,.75f,8);
        } else if(registered()) {
            button(g,l,t,278,191,120,text(data.manageable()?"projects":"mandate"),true,false,mx,my,() -> { if(data.manageable()) projectEditing=true; else action(BuildingPayloads.Kind.MANDATE); });
            button(g,l,t,278,211,120,text("relations"),true,false,mx,my,() -> {relationEditing=true;relationHome=false;relationDetailPage=0;});
            if(data.manageable()) {
                button(g,l,t,278,231,120,text("rename"),true,false,mx,my,() -> name(BuildingPayloads.Kind.RENAME));
                button(g,l,t,278,251,120,text("release"),registered(),false,mx,my,() -> releasing=true);
                button(g,l,t,278,271,120,text("story_target"),true,false,mx,my,() -> action(BuildingPayloads.Kind.REBIND));
            }
        } else if(!data.selectedVillage().equals(BuildingPayloads.NONE) && !data.manageable())
            button(g,l,t,278,191,120,text("mandate"),true,false,mx,my,() -> action(BuildingPayloads.Kind.MANDATE));
    }
    private void requirement(GuiGraphics g,int l,int t,int y,KingdomUiAssets.DetailIcon icon,String label,String count,boolean passed) {
        if(icon==KingdomUiAssets.DetailIcon.JOB) g.renderItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COMPOSTER),l+214,t+y+24-2);
        else KingdomUiAssets.detailIcon(g,icon,l+214,t+y+24-2,16);
        line(g,label,l+235,t+y+24+2,72,INK); line(g,count,l+311,t+y+24+2,34,passed?0xFF306C42:0xFF9A3D32);
        g.fill(l+348,t+y+24,l+391,t+y+24+13,!data.foundingObserved()?0xFFE2CFAB:passed?0xFFC0D99F:0xFFF2BEAA);
        VillageUiTheme.drawStringScaled(g,font,text(!data.foundingObserved()?"require_checking":passed?"require_ok":"require_missing"),l+352,t+y+24+4,!data.foundingObserved()?MUTED:passed?0xFF285B31:0xFF943E30,.65f);
    }
    private static KingdomUiAssets.DetailIcon checkIcon(BuildingScanner.Check check) {
        return switch(check) {
            case ENTRY -> KingdomUiAssets.DetailIcon.LANTERN; case LIGHT -> KingdomUiAssets.DetailIcon.TORCH;
            case ROOF -> KingdomUiAssets.DetailIcon.ROOF; case TABLE -> KingdomUiAssets.DetailIcon.TABLE;
            case SEATS -> KingdomUiAssets.DetailIcon.CHAIR; case BOOKS -> KingdomUiAssets.DetailIcon.BOOK;
            case BED -> KingdomUiAssets.DetailIcon.BED; default -> KingdomUiAssets.DetailIcon.JOB;
        };
    }
    private static void drawCheckIcon(GuiGraphics g,BuildingScanner.Check check,int x,int y) {
        var item=switch(check) {
            case ENTRY -> net.minecraft.world.item.Items.OAK_DOOR;
            case BARRELS -> net.minecraft.world.item.Items.BARREL;
            case KITCHEN -> net.minecraft.world.item.Items.SMOKER;
            case CONTAINERS -> net.minecraft.world.item.Items.CHEST;
            case COUNTER -> net.minecraft.world.item.Items.OAK_SLAB;
            case WORKSTATION -> net.minecraft.world.item.Items.CRAFTING_TABLE;
            case HANDOVER -> net.minecraft.world.item.Items.HOPPER;
            default -> null;
        };
        if(item==null) KingdomUiAssets.detailIcon(g,checkIcon(check),x,y,14);
        else g.renderItem(new net.minecraft.world.item.ItemStack(item),x,y);
    }
    private void renderCheckHelp(GuiGraphics g,int l,int t,int mx,int my) {
        if(checkHelp>=data.rows().size()) { checkHelp=-1; return; }
        var row=data.rows().get(checkHelp); String key=row.check().name().toLowerCase(Locale.ROOT);
        controls.clear(); hints.clear(); JournalV3Visuals.insetBoard(g,l-16,t-10,W,H); KingdomUiAssets.managementHeader(g,font,text("check."+key),checkIcon(data.rows().get(checkHelp).check()),l-16,t-10,W);
        KingdomUiAssets.card(g,l+26,t+94,363,190,false);
        button(g,l,t,376,10,24,text("close"),true,false,mx,my,() -> checkHelp=-1);
        drawCheckIcon(g,row.check(),l+35,t+109);
        line(g,text("check_count",row.found(),row.required(),Math.max(0,row.required()-row.found())),l+62,t+114,315,INK);
        line(g,text(row.passed()?"require_ok":"require_missing"),l+62,t+128,315,row.passed()?0xFF306C42:0xFF9A3D32);
        VillageUiTheme.drawWrappedScaled(g,font,checkHint(data.role(),row.check()),l+35,t+141,340,INK,.8f,6);
        String positions=row.targets().isEmpty()?text("check_no_targets"):text("check_targets",String.join(" · ",row.targets().stream().limit(4).map(BuildingScreen::coordinates).toList()));
        VillageUiTheme.drawWrappedScaled(g,font,positions,l+35,t+205,340,MUTED,.75f,4);
        button(g,l,t,35,232,340,text("mark"),!row.targets().isEmpty(),false,mx,my,() -> mark(checkHelp));
        button(g,l,t,35,273,120,text("back"),true,false,mx,my,() -> checkHelp=-1);
    }
    private void openNetworkMap() { UiTestTour.command("vq routes"); }
    private void renderVillageChoices(GuiGraphics g,int l,int t,int mx,int my) {
        controls.clear(); hints.clear(); JournalV3Visuals.insetBoard(g,l-16,t-10,W,H); KingdomUiAssets.managementHeader(g,font,text("village"),KingdomUiAssets.DetailIcon.FOUNDING,l-16,t-10,W);
        button(g,l,t,376,10,24,text("close"),true,false,mx,my,() -> villageEditing=false);
        KingdomUiAssets.card(g,l+30,t+89,355,157,false);
        if(data.villages().isEmpty()) VillageUiTheme.drawWrappedScaled(g,font,text("village_none"),l+44,t+107,325,INK,.8f,4);
        for(int n=0;n<4;n++) {
            int i=page*4+n; if(i>=data.villages().size()) break; var choice=data.villages().get(i);
            button(g,l,t,44,79+n*31,327,choice.name(),true,data.selectedVillage().equals(choice.id()),mx,my,
                    () -> {action(BuildingPayloads.Kind.SELECT,choice.id(),"");villageEditing=false;});
        }
        button(g,l,t,30,239,28,"<",page>0,false,mx,my,() -> page--);
        line(g,(page+1)+" / "+Math.max(1,(data.villages().size()+3)/4),l+82,t+268,80,MUTED);
        button(g,l,t,180,239,28,">",(page+1)*4<data.villages().size(),false,mx,my,() -> page++);
    }
    private static final int RELATION_CONTACTS = 4;
    private static final int RELATION_DETAIL_LINES = 14;
    private void renderRelations(GuiGraphics g,int l,int t,int mx,int my) {
        controls.clear(); subpage(g,l,t,text("relations"),KingdomUiAssets.DetailIcon.BOOK);
        var diplomacy=data.diplomacy(); var partners=diplomacy.partners();
        button(g,l,t,30,44,160,text("network_map"),true,false,mx,my,this::openNetworkMap);
        button(g,l,t,200,44,69,text("relations_net"),true,!relationList,mx,my,() -> relationList=false);
        button(g,l,t,275,44,69,text("relations_list"),true,relationList,mx,my,() -> relationList=true);
        button(g,l,t,376,10,24,text("close"),true,false,mx,my,() -> relationEditing=false);
        if(partners.isEmpty()) {
            KingdomUiAssets.card(g,l+26,t+96,363,190,false);
            KingdomUiAssets.section(g,font,text("relations_empty"),l+38,t+109,339,false);
            VillageUiTheme.drawWrappedScaled(g,font,text("diplomacy_none"),l+42,t+136,330,MUTED,.8f,5);
            return;
        }
        KingdomUiAssets.card(g,l+26,t+96,181,190,false);
        KingdomUiAssets.card(g,l+218,t+96,171,190,false);
        partnerIndex=Math.min(partnerIndex,partners.size()-1); var partner=partners.get(partnerIndex);
        int group=partnerIndex/RELATION_CONTACTS,groups=(partners.size()+RELATION_CONTACTS-1)/RELATION_CONTACTS;
        int first=group*RELATION_CONTACTS,count=Math.min(RELATION_CONTACTS,partners.size()-first);
        button(g,l,t,30,76,24,"<",group>0 || diplomacy.page()>0,false,mx,my,() -> relationPage(-1));
        line(g,text("relations_page_compact",first+1,first+count),l+60,t+106,111,MUTED);
        button(g,l,t,179,76,24,">",group+1<groups || diplomacy.page()+1<diplomacy.pages(),false,mx,my,() -> relationPage(1));
        if(relationList) {
            for(int i=0;i<count;i++) {
                int index=first+i; var row=partners.get(index); int x=l+30,y=t+126+i*39;
                boolean selected=!relationHome && partnerIndex==index;
                KingdomUiAssets.card(g,x,y,173,36,selected);
                KingdomUiAssets.detailIcon(g,KingdomUiAssets.DetailIcon.FOUNDING,x+4,y+8,18);
                if(selected) g.fill(x+1,y+1,x+3,y+35,0xFFE2B04D);
                VillageUiTheme.drawWrappedScaled(g,font,row.name(),x+26,y+3,143,INK,.72f,3);
                VillageUiTheme.drawStringScaled(g,font,text("relation_state."+row.relation().state()),x+26,y+28,MUTED,.55f);
                controls.add(new Control(x,y,173,true,() -> selectRelation(index),36));
                if(within(mx,my,x,y,173,36)) hints.setTooltipForNextFrame(font,List.of(
                        Component.literal(row.name()),Component.literal(text("relation_state."+row.relation().state()))),mx,my);
            }
        } else {
            // A tree keeps every village label readable and gives each agreement its own stroke.
            KingdomUiAssets.card(g,l+43,t+126,160,22,relationHome);
            VillageUiTheme.drawWrappedScaled(g,font,data.name(),l+48,t+129,150,INK,.7f,2);
            controls.add(new Control(l+43,t+126,160,true,() -> {relationHome=true;relationDetailPage=0;},22));
            if(within(mx,my,l+43,t+126,160,22)) hints.setTooltipForNextFrame(font,Component.literal(data.name()),mx,my);
            for(int i=0;i<count;i++) {
                int index=first+i; var row=partners.get(index); int y=t+155+i*30;
                for(int type=0;type<3;type++) if(row.deals().get(type).status()==2) {
                    int color=type==2?0xFF677DAC:type==1?0xFF8E743D:0xFF3E795A;
                    relationLine(g,l+34+type*2,t+137,l+34+type*2,y+14,color);
                    relationLine(g,l+34+type*2,y+14,l+44,y+14,color);
                }
                if(row.deals().stream().noneMatch(d -> d.status()==2)) {
                    int color=row.relation().state().equals("strained")?0xFFAB4D40:0xFFB3A58C;
                    relationLine(g,l+34,t+137,l+34,y+14,color);relationLine(g,l+34,y+14,l+44,y+14,color);
                }
                KingdomUiAssets.card(g,l+43,y,160,28,!relationHome&&partnerIndex==index);
                VillageUiTheme.drawWrappedScaled(g,font,row.name(),l+48,y+3,150,INK,.7f,3);
                controls.add(new Control(l+43,y,160,true,() -> selectRelation(index),28));
                if(within(mx,my,l+32,y,171,28)) hints.setTooltipForNextFrame(font,List.of(
                        Component.literal(row.name()),Component.literal(text("relations_legend"))),mx,my);
            }
        }
        String title=relationHome?data.name():partner.name();
        VillageUiTheme.drawWrappedScaled(g,font,title,l+224,t+102,160,INK,.75f,3);
        g.fill(l+224,t+129,l+383,t+130,0xFFBC9454);
        if(within(mx,my,l+220,t+100,167,30)) hints.setTooltipForNextFrame(font,Component.literal(title),mx,my);
        if(relationHome || relationDetails) {
            String detail=relationHome?text("relations_scope"):relationDescription(partner);
            var detailLines=relationLines(font,detail);
            int detailPages=Math.max(1,(detailLines.size()+RELATION_DETAIL_LINES-1)/RELATION_DETAIL_LINES);
            relationDetailPage=Math.max(0,Math.min(relationDetailPage,detailPages-1));
            for(int i=0;i<RELATION_DETAIL_LINES && relationDetailPage*RELATION_DETAIL_LINES+i<detailLines.size();i++)
                VillageUiTheme.drawStringScaled(g,font,detailLines.get(relationDetailPage*RELATION_DETAIL_LINES+i),l+224,t+136+i*8,INK,.72f);
            button(g,l,t,224,237,24,"<",relationDetailPage>0,false,mx,my,() -> relationDetailPage--);
            button(g,l,t,361,237,24,">",relationDetailPage+1<detailPages,false,mx,my,() -> relationDetailPage++);
        } else {
            line(g,text("relation_state."+partner.relation().state()),l+224,t+136,160,INK);
            var icons=new KingdomUiAssets.DetailIcon[]{KingdomUiAssets.DetailIcon.STALL_GREEN,KingdomUiAssets.DetailIcon.JOB,KingdomUiAssets.DetailIcon.ADULT};
            for(int i=0;i<3;i++) {
                var agreement=partner.deals().get(i);
                KingdomUiAssets.metric(g,font,icons[i],text("diplomacy_type."+i),agreementSummary(agreement),l+224,t+151+i*28,160,26,agreement.status()==2);
                if(within(mx,my,l+224,t+151+i*28,160,26)) hints.setTooltipForNextFrame(font,List.of(
                        Component.literal(text("diplomacy_type."+i)),Component.literal(agreementStatus(agreement))),mx,my);
            }
            String route=partner.connection().equals("none")?text("diplomacy_route_none"):
                    Component.translatable("screen.village-quest.trade_route.availability."+partner.connection()).getString();
            VillageUiTheme.drawWrappedScaled(g,font,route,l+224,t+238,160,MUTED,.7f,2);
            if(within(mx,my,l+224,t+236,160,20)) hints.setTooltipForNextFrame(font,Component.literal(route),mx,my);
        }
        button(g,l,t,254,237,100,text(relationDetails?"relations_summary":"relations_details"),!relationHome,false,mx,my,() -> {relationDetails=!relationDetails;relationDetailPage=0;});
        button(g,l,t,30,267,173,text("relations_scope_short"),true,relationHome,mx,my,() -> {relationHome=true;relationDetailPage=0;});
        button(g,l,t,224,267,161,text("diplomacy"),!relationHome,false,mx,my,() -> {
            relationEditing=false; projectEditing=false; diplomacyEditing=true;
        });
    }
    static List<String> relationLines(net.minecraft.client.gui.Font font,String detail) {
        var result=new ArrayList<String>();
        for(String paragraph:detail.split("\n")) result.addAll(VillageUiTheme.wrapScaledLines(font,paragraph,160,.72f));
        return List.copyOf(result);
    }
    private static String agreementStatus(BuildingPayloads.Deal deal) {
        return text("diplomacy_status."+(deal.status()==0?(deal.incoming()?"incoming":"outgoing"):Integer.toString(deal.status())));
    }
    private static String agreementSummary(BuildingPayloads.Deal deal) {
        return text("relation_agreement."+(deal.status()==0?(deal.incoming()?"incoming":"outgoing"):Integer.toString(deal.status())));
    }
    private void selectRelation(int index) { partnerIndex=index; relationHome=false; relationDetailPage=0; relationDetails=false; }
    @Override public boolean keyPressed(KeyEvent key) {
        if(checkHelp>=0 && key.key()==InputConstants.KEY_ESCAPE) { checkHelp=-1; return true; }
        if(villageEditing && key.key()==InputConstants.KEY_ESCAPE) { villageEditing=false; return true; }
        if(naming!=null) {
            if(key.key()==InputConstants.KEY_ESCAPE) { cancelName(); return true; }
            if(key.key()==InputConstants.KEY_RETURN || key.key()==InputConstants.KEY_NUMPADENTER) { saveName(); return true; }
        }
        if(releasing && key.key()==InputConstants.KEY_ESCAPE) { releasing=false; return true; }
        if(travelEditing) {
            if(key.key()==InputConstants.KEY_ESCAPE) { travelEditing=false; return true; }
            if(key.key()==InputConstants.KEY_LEFT || key.key()==InputConstants.KEY_RIGHT) {
                int size=data.marketLayout().travel().size(); if(size>0) travelIndex=Math.floorMod(travelIndex+(key.key()==InputConstants.KEY_LEFT?-1:1),size);
                travelDetailPage=0;
                return true;
            }
            if(key.key()==InputConstants.KEY_PAGEUP || key.key()==InputConstants.KEY_PAGEDOWN) {
                travelDetailPage=Math.max(0,travelDetailPage+(key.key()==InputConstants.KEY_PAGEUP?-1:1)); return true;
            }
        }
        if(relationEditing) {
            switch(key.key()) {
                case InputConstants.KEY_ESCAPE -> { relationEditing=false; return true; }
                case InputConstants.KEY_TAB -> { relationList=!relationList; return true; }
                case InputConstants.KEY_UP,InputConstants.KEY_DOWN -> {
                    int direction=key.key()==InputConstants.KEY_UP?-1:1;
                    if(!data.diplomacy().partners().isEmpty()) selectRelation(Math.floorMod(partnerIndex+direction,data.diplomacy().partners().size()));
                    return true;
                }
                case InputConstants.KEY_PAGEUP,InputConstants.KEY_PAGEDOWN -> {
                    int direction=key.key()==InputConstants.KEY_PAGEUP?-1:1;
                    if(direction<0 && (partnerIndex/RELATION_CONTACTS>0 || data.diplomacy().page()>0)
                            || direction>0 && ((partnerIndex/RELATION_CONTACTS+1)*RELATION_CONTACTS<data.diplomacy().partners().size() || data.diplomacy().page()+1<data.diplomacy().pages())) relationPage(direction);
                    return true;
                }
                case InputConstants.KEY_LEFT,InputConstants.KEY_RIGHT -> { relationDetailPage=Math.max(0,relationDetailPage+(key.key()==InputConstants.KEY_LEFT?-1:1)); return true; }
                case InputConstants.KEY_RETURN -> { if(!relationHome && !data.diplomacy().partners().isEmpty()) {relationEditing=false;projectEditing=false;diplomacyEditing=true;} return true; }
                default -> { }
            }
        }
        if(key.key()==InputConstants.KEY_ESCAPE) {
            if(crisisEditing) { crisisEditing=false; return true; }
            if(diplomacyEditing) { diplomacyEditing=false; projectEditing=true; return true; }
            if(projectEditing) { projectEditing=false; return true; }
            if(marketEditing) { marketEditing=false; return true; }
        }
        return super.keyPressed(key);
    }
    private String relationDescription(BuildingPayloads.Partner partner) {
        var relation=partner.relation();
        var parts=new ArrayList<String>();
        parts.add(text("relation_state."+relation.state())); parts.add(text("relation_trend."+relation.trend()));
        for(var cause:relation.causes()) parts.add(text("relation_cause."+cause,relation.repairVillage()));
        parts.add(text("relation_repair."+relation.repair(),relation.repairVillage(),relation.need().equals("none")?"":
                Component.translatable("text.village-quest.village_network.need."+relation.need())));
        return String.join("\n",parts);
    }
    private void relationPage(int direction) {
        int group=partnerIndex/RELATION_CONTACTS+direction,groups=(data.diplomacy().partners().size()+RELATION_CONTACTS-1)/RELATION_CONTACTS;
        relationHome=false; relationDetailPage=0;
        if(group>=0 && group<groups) partnerIndex=group*RELATION_CONTACTS;
        else diplomacyPage(data.diplomacy().page()+direction);
    }
    private static void relationLine(GuiGraphics g,int x,int y,int targetX,int targetY,int color) {
        int steps=Math.max(Math.abs(targetX-x),Math.abs(targetY-y));
        for(int i=0;i<=steps;i++) { int px=x+(targetX-x)*i/Math.max(1,steps),py=y+(targetY-y)*i/Math.max(1,steps); g.fill(px,py,px+1,py+1,color); }
    }
    private static KingdomUiAssets.DetailIcon recognitionIcon(String key) {
        return switch(key) {
            case "council" -> KingdomUiAssets.DetailIcon.THRONE; case "inn" -> KingdomUiAssets.DetailIcon.LANTERN;
            case "market","two_stands","three_stands","four_stands","five_stands","active_visit" -> KingdomUiAssets.DetailIcon.STALL_GREEN;
            case "voices","meeting","aid" -> KingdomUiAssets.DetailIcon.ADULT;
            case "trade","route_agreement","two_pairs" -> KingdomUiAssets.DetailIcon.FOUNDING;
            default -> KingdomUiAssets.DetailIcon.JOB;
        };
    }
    private void renderProjects(GuiGraphics g,int l,int t,int mx,int my) {
        controls.clear(); subpage(g,l,t,text("projects"),KingdomUiAssets.DetailIcon.FOUNDING);
        var project=data.recognition();
        button(g,l,t,263,44,90,text("diplomacy"),data.manageable(),false,mx,my,() -> { projectEditing=false; diplomacyEditing=true; });
        button(g,l,t,376,10,24,text("close"),true,false,mx,my,() -> projectEditing=false);
        KingdomUiAssets.section(g,font,text("recognition_status",project.level(),5,text("recognition_level."+project.level())),l+30,t+94,355,project.level()>=5);
        if(project.level()>=5) { KingdomUiAssets.detailIcon(g,KingdomUiAssets.DetailIcon.THRONE,l+165,t+144,64);
            VillageUiTheme.drawWrappedScaled(g,font,text("recognition_complete"),l+40,t+224,330,INK,.8f,4); return; }
        line(g,text("project."+project.level()),l+30,t+119,355,INK);
        var requirements=de.quest.kingdom.KingdomRecognition.requirements(project.level()).stream().sorted().toList();
        int done=project.readOnly()?0:requirements.size()-project.missing().size();
        line(g,text("project_progress",done,requirements.size()),l+30,t+132,355,MUTED);
        for(int i=0;i<requirements.size();i++) {
            String key=requirements.get(i).name().toLowerCase(Locale.ROOT); boolean passed=!project.readOnly()&&!project.missing().contains(key);
            int x=l+30+(i%2)*181,y=t+148+(i/2)*33;
            KingdomUiAssets.metric(g,font,recognitionIcon(key),text("recognition_short."+key),text(project.readOnly()?"require_checking":passed?"require_ok":"require_missing"),x,y,174,29,passed);
            if(within(mx,my,x,y,174,29)) {
                if(key.equals("voices") && !project.readOnly()) {
                    var lines=new java.util.ArrayList<Component>();
                    lines.add(Component.literal(text(project.missingConversations().isEmpty()?"conversations_complete":"conversations_missing")));
                    for(var roleKey:project.missingConversations()) lines.add(Component.literal(text("conversation_role."+roleKey)));
                    hints.setTooltipForNextFrame(font,lines,mx,my);
                } else hints.setTooltipForNextFrame(font,Component.literal(text("recognition_need."+key)+(key.equals("worksite")?"\n"+text("worksite_check"):"")),mx,my);
            }
            if(key.equals("worksite")) controls.add(new Control(x,y,174,data.manageable()&&!project.readOnly(),() -> action(BuildingPayloads.Kind.WORKSITE_CHECK),29));
        }
        boolean ready=data.manageable() && !project.readOnly() && project.missing().isEmpty();
        button(g,l,t,30,267,355,text("recognition_confirm"),ready,false,mx,my,() -> {
            if(!closed && ClientPlayNetworking.canSend(BuildingPayloads.Action.ID)) UiTestTour.send(new BuildingPayloads.Action(
                    session(),data.revision(),BuildingPayloads.Kind.PROJECT,data.selectedVillage(),Long.toString(project.revision()),project.level()));
        });
    }
    private void renderDiplomacy(GuiGraphics g,int l,int t,int mx,int my) {
        controls.clear(); subpage(g,l,t,text("diplomacy"),KingdomUiAssets.DetailIcon.THRONE);
        var diplomacy=data.diplomacy(); var partners=diplomacy.partners();
        line(g,text("diplomacy_choose"),l+30,t+73,200,INK);
        button(g,l,t,238,44,115,text("crisis_panel"),!diplomacy.crisis().phase().equals("none"),false,mx,my,() -> crisisEditing=true);
        button(g,l,t,376,10,24,text("close"),true,false,mx,my,() -> { diplomacyEditing=false; projectEditing=true; });
        if(partners.isEmpty()) { VillageUiTheme.drawWrappedScaled(g,font,text("diplomacy_none"),l+30,t+109,350,MUTED,.8f,5); return; }
        partnerIndex=Math.min(partnerIndex,partners.size()-1); var partner=partners.get(partnerIndex); var deal=partner.deals().get(dealType);
        button(g,l,t,30,76,28,"<",partnerIndex>0 || diplomacy.page()>0,false,mx,my,() -> {
            if(partnerIndex>0) partnerIndex--;else diplomacyPage(diplomacy.page()-1);
        });
        line(g,partner.name(),l+68,t+106,273,INK);
        if(within(mx,my,l+65,t+98,280,22)) hints.setTooltipForNextFrame(font,Component.literal(partner.name()+"\n"+text("diplomacy_page",diplomacy.page()+1,diplomacy.pages(),partnerIndex+1,partners.size())),mx,my);
        button(g,l,t,357,76,28,">",partnerIndex+1<partners.size() || diplomacy.page()+1<diplomacy.pages(),false,mx,my,() -> {
            if(partnerIndex+1<partners.size()) partnerIndex++;else diplomacyPage(diplomacy.page()+1);
        });
        var icons=new KingdomUiAssets.DetailIcon[]{KingdomUiAssets.DetailIcon.STALL_GREEN,KingdomUiAssets.DetailIcon.JOB,KingdomUiAssets.DetailIcon.ADULT};
        for(int i=0;i<3;i++) {
            int choice=i,x=l+30+i*120,y=t+134;
            KingdomUiAssets.metric(g,font,icons[i],text("diplomacy_type."+i),text("diplomacy_goal."+i),x,y,112,42,dealType==i);
            controls.add(new Control(x,y,112,true,() -> dealType=choice,42));
            if(within(mx,my,x,y,112,42)) hints.setTooltipForNextFrame(font,Component.literal(text("diplomacy_help."+i)),mx,my);
            button(g,l,t,30+i*120,162,112,text("diplomacy_terms."+i),true,dealTerms==i,mx,my,() -> dealTerms=choice);
        }
        VillageUiTheme.drawWrappedScaled(g,font,text("diplomacy_help."+dealType)+" "+text("diplomacy_terms_help."+dealTerms),l+30,t+212,355,INK,.7f,3);
        String status=deal.status()==0?(deal.incoming()?"incoming":"outgoing"):Integer.toString(deal.status());
        line(g,text("diplomacy_status."+status),l+30,t+242,355,INK);
        if(deal.status()==0 && !deal.reaction().equals("none")) {
            String response=text("diplomacy_response."+deal.personality()+"."+deal.reaction(),text("diplomacy_terms."+deal.preferred()),
                    deal.need().equals("none")?"":Component.translatable("text.village-quest.village_network.need."+deal.need()));
            line(g,response,l+30,t+255,355,MUTED);
            if(within(mx,my,l+28,t+252,360,16)) hints.setTooltipForNextFrame(font,Component.literal(response),mx,my);
        } else if(deal.status()>=0) line(g,text("diplomacy_current",text("diplomacy_terms."+deal.terms())),l+30,t+255,355,MUTED);
        var route=partner.connection().equals("none")?text("diplomacy_route_none"):Component.translatable("screen.village-quest.trade_route.availability."+partner.connection()).getString();
        line(g,text("diplomacy_route",route),l+30,t+272,355,MUTED);
        boolean editable=data.manageable() && !diplomacy.readOnly();
        boolean meeting=dealType==2 && (deal.status()==1 || deal.status()==3 && diplomacy.crisis().kind().equals("loyalty") && diplomacy.crisis().phase().equals("reconciling"));
        if(meeting) {
            button(g,l,t,30,267,220,text(partner.meeting()?"diplomacy_meeting_confirm":"diplomacy_meeting_invite"),editable,false,mx,my,
                    () -> diplomacyAction(BuildingPayloads.Kind.DIPLOMACY_MEETING,partner,deal,partner.meeting()?1:0));
            if(partner.meeting()) button(g,l,t,257,267,128,text("diplomacy_meeting_cancel"),editable,false,mx,my,
                    () -> diplomacyAction(BuildingPayloads.Kind.DIPLOMACY_MEETING,partner,deal,2));
        } else {
            boolean outgoing=deal.status()==0 && !deal.incoming();
            boolean unchanged=outgoing && dealTerms==deal.terms();
            boolean retry=unchanged && deal.reaction().equals("unknown");
            boolean recover=offerPendingRevision>=0 && offerPendingTicks>=200;
            String offerLabel=recover?"diplomacy_offer_reopen":offerPendingRevision>=0?"diplomacy_offer_sending":retry?"diplomacy_offer_retry":unchanged?"diplomacy_offer_sent":
                    deal.incoming()?"diplomacy_counter":outgoing?"diplomacy_offer_change":"diplomacy_offer";
            button(g,l,t,30,267,170,text(offerLabel),recover || editable&&deal.status()<=0&&offerPendingRevision<0&&(!unchanged||retry),false,mx,my,
                    () -> { if(recover) {closeSession();super.onClose();}
                        else diplomacyAction(BuildingPayloads.Kind.DIPLOMACY_OFFER,partner,deal,dealType*3+dealTerms); });
            if(recover && within(mx,my,l+30,t+291,170,18)) hints.setTooltipForNextFrame(font,Component.literal(text("diplomacy_offer_reopen_hint")),mx,my);
            if(!recover && outgoing && within(mx,my,l+30,t+291,170,18)) hints.setTooltipForNextFrame(font,Component.literal(text("diplomacy_offer_next")),mx,my);
            button(g,l,t,215,267,170,text("diplomacy_accept"),editable&&deal.status()==0&&deal.incoming()
                    && (deal.reaction().equals("none") || deal.reaction().equals("ready")),false,mx,my,
                    () -> diplomacyAction(BuildingPayloads.Kind.DIPLOMACY_ACCEPT,partner,deal,dealType));
        }
    }
    private void diplomacyPage(int page) {
        partnerIndex=0;
        if(!closed && ClientPlayNetworking.canSend(BuildingPayloads.Action.ID)) UiTestTour.send(new BuildingPayloads.Action(
                session(),data.revision(),BuildingPayloads.Kind.DIPLOMACY_PAGE,data.selectedVillage(),"",page));
    }
    private void renderCrisis(GuiGraphics g,int l,int t,int mx,int my) {
        controls.clear(); subpage(g,l,t,text(data.diplomacy().crisis().kind().equals("loyalty")?"loyalty_panel":"crisis_panel"),KingdomUiAssets.DetailIcon.LANTERN);
        var crisis=data.diplomacy().crisis(); boolean loyalty=crisis.kind().equals("loyalty");
        button(g,l,t,376,10,24,text("close"),true,false,mx,my,() -> crisisEditing=false);
        if(crisis.phase().equals("none")) return;
        line(g,text("crisis_pair",crisis.host(),crisis.other()),l+30,t+102,355,INK);
        KingdomUiAssets.section(g,font,text("crisis_phase."+crisis.phase()),l+30,t+120,355,false);
        var need=Component.translatable("text.village-quest.village_network.need."+crisis.need());
        line(g,text(crisis.competing()?"crisis_competing":"crisis_need",need),l+30,t+144,355,MUTED);
        if(crisis.phase().equals("open") || crisis.phase().equals("aid"))
            line(g,text("crisis_time",(crisis.remaining()+1199)/1200),l+30,t+164,355,MUTED);
        String instruction=text((loyalty?"loyalty":"crisis")+"_instruction."+crisis.phase(),crisis.host(),crisis.other(),need);
        if(crisis.phase().equals("reconciling")) instruction+=" "+text(loyalty&&crisis.personality()==2?"loyalty_repair.2":"crisis_repair."+crisis.personality());
        VillageUiTheme.drawWrappedScaled(g,font,instruction,l+30,t+186,355,INK,.75f,5);
        if(within(mx,my,l+28,t+183,360,66)) hints.setTooltipForNextFrame(font,Component.literal(instruction),mx,my);
        boolean editable=data.manageable() && !data.diplomacy().readOnly();
        if(crisis.phase().equals("open")) {
            for(int i:new int[]{0,1,4}) {
                int choice=i,slot=i==4?2:i;
                button(g,l,t,30+slot*120,237,112,text("crisis_choice."+choice),editable&&(!loyalty||choice==4||crisis.heard()==3),false,mx,my,() -> crisisAction(crisis,choice));
            }
        } else if(crisis.phase().equals("withdrawn")) button(g,l,t,30,237,355,text("crisis_choice.2"),editable&&(crisis.heard()&2)!=0,false,mx,my,() -> crisisAction(crisis,2));
        else if(crisis.phase().equals("ready")) button(g,l,t,30,237,355,text("crisis_choice.3"),editable,false,mx,my,() -> crisisAction(crisis,3));
        line(g,text("crisis_heard",(crisis.heard()&1)!=0?1:0,(crisis.heard()&2)!=0?1:0),l+30,t+286,355,MUTED);
    }
    private void crisisAction(BuildingPayloads.Crisis crisis,int choice) {
        if(!closed && ClientPlayNetworking.canSend(BuildingPayloads.Action.ID)) UiTestTour.send(new BuildingPayloads.Action(
                session(),data.revision(),BuildingPayloads.Kind.CRISIS_CHOICE,crisis.id(),Long.toString(crisis.revision()),choice));
    }
    private void diplomacyAction(BuildingPayloads.Kind kind,BuildingPayloads.Partner partner,BuildingPayloads.Deal deal,int choice) {
        if(kind==BuildingPayloads.Kind.DIPLOMACY_OFFER && offerPendingRevision>=0) return;
        if(!closed && ClientPlayNetworking.canSend(BuildingPayloads.Action.ID)) {
            if(kind==BuildingPayloads.Kind.DIPLOMACY_OFFER) {
                offerPendingRevision=data.revision();offerPendingTicks=0;
                offerPendingPartner=partner.village();offerPendingType=choice/3;offerPendingDeal=deal;
            }
            UiTestTour.send(new BuildingPayloads.Action(session(),data.revision(),kind,partner.village(),Long.toString(deal.revision()),choice));
        }
    }
    private void renderMarketPlaces(GuiGraphics g,int l,int t,int mx,int my) {
        controls.clear(); hints.clear(); JournalV3Visuals.insetBoard(g,l-16,t-10,W,H); KingdomUiAssets.managementHeader(g,font,text("places"),KingdomUiAssets.DetailIcon.STALL_GREEN,l-16,t-10,W);

        button(g,l,t,376,10,24,text("close"),true,false,mx,my,() -> marketEditing=false);
        VillageUiTheme.drawWrappedScaled(g,font,text("places_intro"),l+26,t+68,363,INK,.7f,1);
        for(int i=0;i<5;i++) {
            int index=i,x=26+i*74; boolean selected=placeSlot==i;
            var occupied=data.marketLayout().assigned().stream().filter(p -> p.slot()==index).findFirst();
            VillageUiTheme.drawButton(g,font,l+x,t+81,68,45,"",true,within(mx,my,l+x,t+81,68,45),selected);
            KingdomUiAssets.detailIcon(g,KingdomUiAssets.stall(i),l+x+22,t+84,24);
            VillageUiTheme.drawStringScaled(g,font,text("place_slot",i+1),l+x+10,t+108,0xFFFFE9BE,.72f);
            g.fill(l+x+5,t+118,l+x+63,t+124,occupied.isPresent()?0xFF91BC6B:0xFFCBB995);
            VillageUiTheme.drawStringScaled(g,font,text(occupied.isPresent()?"place_assigned":"place_free"),l+x+9,t+118,0xFF294B28,.6f);
            controls.add(new Control(l+x,t+81,68,true,() -> placeSlot=index,45));
        }
        var assigned=data.marketLayout().assigned().stream().filter(p -> p.slot()==placeSlot).findFirst();
        KingdomUiAssets.card(g,l+26,t+132,363,25,assigned.isPresent());
        KingdomUiAssets.detailIcon(g,KingdomUiAssets.stall(placeSlot),l+29,t+134,20);
        line(g,text("place_slot",placeSlot+1)+" · "+text(assigned.isPresent()?"place_assigned":"place_free"),l+53,t+136,232,INK);
        VillageUiTheme.drawStringScaled(g,font,assigned.map(p -> text(p.usable()?"place_current":"place_pending",coordinates(p.position()))).orElse(text("place_empty")),l+53,t+147,MUTED,.65f);
        button(g,l,t,294,111,91,text("place_remove"),data.manageable()&&assigned.isPresent(),false,mx,my,
                () -> placeAction(BuildingPayloads.Kind.PLACE_REMOVE,0));
        KingdomUiAssets.section(g,font,text("places_available"),l+26,t+162,363,false);
        if(within(mx,my,l+26,t+162,363,17)) hints.setTooltipForNextFrame(font,Component.literal(text("places_hint")),mx,my);
        var candidates=data.marketLayout().candidates(); placePage=Math.min(placePage,Math.max(0,(candidates.size()-1)/3));
        for(int n=0;n<3;n++) {
            int index=placePage*3+n; if(index>=candidates.size()) break; var p=candidates.get(index); int y=158+n*34;
            boolean current=assigned.filter(a -> samePlace(a,p)).isPresent();
            var used=data.marketLayout().assigned().stream().filter(a -> a.slot()!=placeSlot && (a.position().equals(p.position()) || a.counter().equals(p.counter()) || a.container().equals(p.container()))).findFirst();
            KingdomUiAssets.card(g,l+26,t+y+24,363,32,current);
            KingdomUiAssets.detailIcon(g,KingdomUiAssets.stall(current?placeSlot:index),l+29,t+y+24+5,21);
            VillageUiTheme.drawStringScaled(g,font,text("place_candidate",index+1,coordinates(p.position())),l+54,t+y+24+3,INK,.72f);
            VillageUiTheme.drawStringScaled(g,font,text("place_counter",coordinates(p.counter())),l+54,t+y+24+13,MUTED,.65f);
            VillageUiTheme.drawStringScaled(g,font,text("place_container",coordinates(p.container())),l+54,t+y+24+22,MUTED,.65f);
            button(g,l,t,240,y+7,65,text("place_mark"),true,false,mx,my,() -> {
                highlights=List.of(p.position(),p.counter(),p.container()); highlightLevel=minecraft.level; highlightTicks=200;
            });
            button(g,l,t,312,y+7,73,text(current?"place_assigned":"place_assign"),data.manageable()&&!current&&used.isEmpty(),current,mx,my,
                    () -> placeAction(BuildingPayloads.Kind.PLACE_ASSIGN,index));
            if(within(mx,my,l+26,t+y+24,363,32)) {
                var detail=new ArrayList<Component>(List.of(Component.translatable(TEXT+"place_candidate",index+1,coordinates(p.position())),
                        Component.translatable(TEXT+"place_counter",coordinates(p.counter())),Component.translatable(TEXT+"place_container",coordinates(p.container()))));
                used.ifPresent(a -> detail.add(Component.translatable(TEXT+"place_conflict",a.slot()+1)));
                hints.setTooltipForNextFrame(font,detail,mx,my);
            }
        }
        if(candidates.isEmpty()) VillageUiTheme.drawWrappedScaled(g,font,text("places_none"),l+30,t+193,352,MUTED,.8f,7);
        button(g,l,t,30,269,28,"<",placePage>0,false,mx,my,() -> placePage--);
        line(g,(placePage+1)+" / "+Math.max(1,(candidates.size()+2)/3),l+82,t+298,80,MUTED);
        button(g,l,t,180,269,28,">",(placePage+1)*3<candidates.size(),false,mx,my,() -> placePage++);
        button(g,l,t,218,269,166,text("travel_title"),true,false,mx,my,() -> travelEditing=true);
    }
    private static boolean samePlace(BuildingPayloads.Stand a,BuildingPayloads.Stand b) {
        return a.position().equals(b.position()) && a.counter().equals(b.counter()) && a.container().equals(b.container());
    }
    private void renderMarketTravel(GuiGraphics g,int l,int t,int mx,int my) {
        controls.clear(); subpage(g,l,t,text("travel_title"),KingdomUiAssets.DetailIcon.STALL_BLUE);
        button(g,l,t,376,10,24,text("close"),true,false,mx,my,() -> travelEditing=false);
        var trips=data.marketLayout().travel(); travelIndex=Math.min(travelIndex,Math.max(0,trips.size()-1));
        if(trips.isEmpty()) VillageUiTheme.drawWrappedScaled(g,font,text("travel_empty"),l+30,t+118,350,MUTED,.8f,7);
        else {
            var trip=trips.get(travelIndex);
            boolean returning=trip.phase().equals("returning");
            KingdomUiAssets.card(g,l+30,t+102,145,27,trip.phase().equals("home"));
            KingdomUiAssets.card(g,l+239,t+102,145,27,trip.phase().equals("stay")||trip.phase().equals("evening"));
            line(g,trip.home(),l+36,t+112,133,INK); line(g,trip.host(),l+245,t+112,133,INK);
            relationLine(g,l+178,t+115,l+236,t+115,trip.paused()?MUTED:0xFF56824B);
            line(g,returning?"<":" >",l+202,t+108,20,INK);
            VillageUiTheme.drawWrappedScaled(g,font,text("travel_phase."+trip.phase()),l+30,t+137,350,trip.paused()?0xFF9A3D32:INK,.8f,2);
            var lines=List.of(text("travel_person",trip.name()),text("travel_between",trip.home(),trip.host()),
                    trip.paused() && !trip.phase().equals("home")?text("travel_paused"):
                            trip.remaining()>0?text("travel_time",(trip.remaining()+19)/20):text("places_daytime"),
                    trip.late()?text("travel_late"):text("travel_routine"),text("travel_route"));
            var wrapped=travelLines(font,lines); int pages=Math.max(1,(wrapped.size()+6)/7); travelDetailPage=Math.min(travelDetailPage,pages-1);
            for(int i=0;i<7 && travelDetailPage*7+i<wrapped.size();i++)
                VillageUiTheme.drawStringScaled(g,font,wrapped.get(travelDetailPage*7+i),l+30,t+156+i*10,INK,.8f);
            button(g,l,t,286,211,24,"<",travelDetailPage>0,false,mx,my,() -> travelDetailPage--);
            line(g,text("travel_detail_pages"),l+230,t+241,50,MUTED);
            line(g,(travelDetailPage+1)+"/"+pages,l+316,t+241,38,MUTED);
            button(g,l,t,360,211,24,">",travelDetailPage+1<pages,false,mx,my,() -> travelDetailPage++);
            if(within(mx,my,l+28,t+104,356,126)) hints.setTooltipForNextFrame(font,Component.literal(text("travel_phase."+trip.phase())+"\n"+String.join("\n",lines)),mx,my);
            if(trips.size()>1) button(g,l,t,30,239,28,"<",true,false,mx,my,() -> {travelIndex=Math.floorMod(travelIndex-1,trips.size());travelDetailPage=0;});
            line(g,text("travel_trader_page",travelIndex+1,trips.size()),l+65,t+268,110,MUTED);
            if(trips.size()>1) button(g,l,t,180,239,28,">",true,false,mx,my,() -> {travelIndex=(travelIndex+1)%trips.size();travelDetailPage=0;});
        }
    }
    static List<String> travelLines(net.minecraft.client.gui.Font font,List<String> paragraphs) {
        var result=new ArrayList<String>();
        for(var paragraph:paragraphs) {
            for(var line:VillageUiTheme.wrapScaledLines(font,paragraph,350,.8f)) {
                var part=new StringBuilder();
                for(int cp:line.codePoints().toArray()) {
                    String glyph=new String(Character.toChars(cp));
                    if(!part.isEmpty() && font.width(part+glyph)*.8f>350) { result.add(part.toString()); part.setLength(0); }
                    part.append(glyph);
                }
                if(!part.isEmpty()) result.add(part.toString());
            }
            result.add("");
        }
        return List.copyOf(result);
    }
    private static String coordinates(BlockPos p) { return p.getX()+", "+p.getY()+", "+p.getZ(); }
    private void placeAction(BuildingPayloads.Kind kind,int candidate) {
        if(!closed && ClientPlayNetworking.canSend(BuildingPayloads.Action.ID))
            UiTestTour.send(new BuildingPayloads.Action(session(),data.revision(),kind,data.selectedVillage(),Integer.toString(placeSlot),candidate));
    }
    private void name(BuildingPayloads.Kind kind) {
        naming=kind; namingPending=false; nameField.setValue(data.name().isBlank()?text("default_name"):data.name()); nameField.setVisible(true); nameField.moveCursorToEnd(false); setInitialFocus(nameField);
    }
    private void cancelName() {
        naming=null; namingPending=false;
        if(nameField!=null) { nameField.setVisible(false); nameField.setFocused(false); }
        setFocused(null);
    }
    private void saveName() {
        if(naming==null || nameField==null || closed || namingPending || !de.quest.kingdom.LivingKingdomsState.validName(nameField.getValue())
                || !ClientPlayNetworking.canSend(BuildingPayloads.Action.ID)) return;
        action(naming,data.selectedVillage(),nameField.getValue());
        if(naming==BuildingPayloads.Kind.FOUND || naming==BuildingPayloads.Kind.CONFIRM) namingPending=true;
        else cancelName();
    }
    private void action(BuildingPayloads.Kind kind) { action(kind,data.selectedVillage(),""); }
    private void action(BuildingPayloads.Kind kind, UUID target, String name) {
        if (!closed && ClientPlayNetworking.canSend(BuildingPayloads.Action.ID))
            UiTestTour.send(new BuildingPayloads.Action(session(),data.revision(),kind,target,name,0));
    }
    @Override protected boolean handleMouseClicked(MouseButtonEvent click, boolean doubled) {
        if (click.button()!=InputConstants.MOUSE_BUTTON_LEFT) return super.handleMouseClicked(click,doubled);
        int x=responsiveMouseX(click.x(),W,H), y=responsiveMouseY(click.y(),W,H);
        for (Control control : List.copyOf(controls)) if (within(x,y,control.x,control.y,control.width,control.height)) {
            if (control.enabled) control.action.run(); return true;
        }
        if (naming!=null) return nameField.mouseClicked(new MouseButtonEvent(x,y,click.buttonInfo()),doubled);
        return false;
    }
    @Override public void tick() {
        super.tick(); if(offerPendingRevision>=0 && offerPendingTicks<200) offerPendingTicks++;
    }
    @Override public void onClose() {
        if(checkHelp>=0) { checkHelp=-1; return; }
        if(naming!=null || releasing) { cancelName(); releasing=false; return; }
        if(crisisEditing) { crisisEditing=false; return; }
        if(diplomacyEditing) { diplomacyEditing=false; relationEditing=true; return; }
        if(projectEditing) { projectEditing=false; return; }
        if(travelEditing) { travelEditing=false; return; }
        if(marketEditing) { marketEditing=false; return; }
        if(villageEditing) { villageEditing=false; return; }
        if(relationEditing) { relationEditing=false; return; }
        closeSession(); super.onClose();
    }
    @Override public void removed() { closeSession(); super.removed(); }
    private void closeSession() { if (!closed) { action(BuildingPayloads.Kind.CLOSE); closed=true; } }
    private void mark(int row) {
        if (minecraft.level==null) return; highlights=data.rows().get(row).targets(); highlightLevel=minecraft.level; highlightTicks=200;
    }
    /** Bounded local visual aid survives closing the checklist; no server evidence or block mutation. */
    public static void tickHighlights(Minecraft client) {
        if (client.level==null || client.level!=highlightLevel || highlightTicks<=0) { clearHighlights(); return; }
        if (highlightTicks--%10==0) for (BlockPos pos:highlights) if (client.level.hasChunkAt(pos))
            client.level.addParticle(ParticleTypes.HAPPY_VILLAGER,pos.getX()+.5,pos.getY()+.8,pos.getZ()+.5,0,.03,0);
    }
    public static void clearHighlights() { highlights=List.of(); highlightLevel=null; highlightTicks=0; }
    private static boolean within(int x,int y,int left,int top,int w,int h) { return x>=left&&x<left+w&&y>=top&&y<top+h; }
    private record Control(int x,int y,int width,boolean enabled,Runnable action,int height) {
        Control(int x,int y,int width,boolean enabled,Runnable action) { this(x,y,width,enabled,action,18); }
    }
}
