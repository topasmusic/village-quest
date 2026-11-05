package de.quest.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import de.quest.client.compat.GuiGraphics;
import de.quest.client.ui.KingdomUiAssets;
import de.quest.client.ui.KingdomUiHints;
import de.quest.client.ui.VillageUiTheme;
import de.quest.client.ui.JournalV3Visuals;
import de.quest.economy.CurrencyService;
import de.quest.network.MarketPayloads;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Displays the server's exact offers; no local prices, stock mutations or inventory dragging. */
public final class MarketScreen extends CompatScreen {
    private final KingdomUiHints hints=new KingdomUiHints();
    private static final String TEXT="screen.village-quest.market.";
    private static final int W=560,INK=0xFF3E2918,MUTED=0xFF70543B;
    private static final int H=315, PAGE_SIZE=4;
    private static final Set<UUID> CLOSED=new LinkedHashSet<>();
    private final List<Control> controls=new ArrayList<>();
    private MarketPayloads.View data;
    private boolean waiting,closed;
    private int detail=-1,detailPage,goodsPage;
    public MarketScreen(MarketPayloads.View data) { super(Component.translatable(TEXT+"title")); this.data=data; }
    public UUID session() { return data.session(); }
    void selectTourDetails() { detail=0;detailPage=1; }
    public static boolean wasClosed(UUID session) { return CLOSED.contains(session); }
    public static void disconnected() { CLOSED.clear(); }
    public boolean updateData(MarketPayloads.View view) {
        if(!view.session().equals(session()) || view.revision()<data.revision()) return false;
        data=view; goodsPage=Math.clamp(goodsPage,0,Math.max(0,(view.rows().size()-1)/PAGE_SIZE)); waiting=false;
        if(!view.active()) { closed=true; remember(); }
        return true;
    }
    @Override public boolean isPauseScreen() { return false; }
    private static String text(String key,Object... args) { return Component.translatable(TEXT+key,args).getString(); }
    private void line(GuiGraphics g,String value,int x,int y,int width,int color) {
        VillageUiTheme.drawStringScaled(g,font,VillageUiTheme.ellipsize(font,value,(int)(width/.95f)),x,y,color,.95f);
    }
    private void centered(GuiGraphics g,String value,int x,int y,int width,int color) {
        JournalV3Visuals.centeredHeading(g,font,value,x,y,width,.95f,color);
    }
    private void button(GuiGraphics g,int x,int y,int w,int h,String label,boolean enabled,int mx,int my,Runnable action) {
        button(g,x,y,w,h,label,enabled,enabled,mx,my,action);
    }
    private void button(GuiGraphics g,int x,int y,int w,int h,String label,boolean enabled,boolean appearanceEnabled,int mx,int my,Runnable action) {
        boolean hover=within(mx,my,x,y,w,h);
        if("×".equals(label))JournalV3Visuals.close(g,x,y,w,h,hover);
        else if(appearanceEnabled)JournalV3Visuals.action(g,font,label,x,y,w,h,hover);
        else VillageUiTheme.drawButton(g,font,x,y,w,h,label,false,false,false);
        controls.add(new Control(x,y,w,enabled,action,h));
        if(hover)hints.setTooltipForNextFrame(font,Component.literal(label),mx,my);
    }
    private void tradeButton(GuiGraphics g,int x,int y,boolean available,int mx,int my,Runnable action) {
        button(g,x,y,56,24,"",available&&!waiting,available,mx,my,action);
    }
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float delta) {
        VillageUiTheme.drawScreenShade(g,width,height);controls.clear();hints.clear();
        int mx=responsiveMouseX(mouseX,W,H),my=responsiveMouseY(mouseY,W,H);
        float scale=beginResponsivePanel(g,W,H);
        try {
            int l=(width-W)/2,t=(height-H)/2;
            g.fill(l+12,t+22,l+W-12,t+H-14,VillageUiTheme.WOOD);
            JournalV3Visuals.paper(g,l+27,t+37,506,49);
            VillageUiTheme.drawFrame(g,l,t,W,H);
            JournalV3Visuals.title(g,font,text("title"),l+188,t+3,184,"market");
            button(g,l+W-25,t+3,20,20,"×",true,mx,my,()->{detail=-1;onClose();});
            JournalV3Visuals.heading(g,font,text("trader",data.trader()),l+36,t+44,278,1.05f,INK);
            line(g,text("origin",data.home(),data.market()),l+36,t+60,278,INK);
            line(g,text("departure",String.format(Locale.ROOT,"%d:%02d",data.departureTicks()/1200,(data.departureTicks()/20)%60)),l+36,t+73,278,MUTED);
            if(within(mx,my,l+32,t+40,285,42))hints.setTooltipForNextFrame(font,List.of(Component.literal(text("origin",data.home(),data.market())),Component.literal(text("prices",data.factor(),data.discount()))),mx,my);
            wallet(g,text("wallet_you"),data.balance(),l+324,t+43,100,mx,my);
            wallet(g,text("wallet_trader"),data.budget(),l+430,t+43,100,mx,my);
            JournalV3Visuals.paper(g,l+28,t+92,338,184);
            JournalV3Visuals.section(g,font,text("goods"),l+31,t+95,332,1.05f);
            centered(g,text("goods_column"),l+36,t+119,108,INK);
            centered(g,text("stock_column"),l+148,t+119,43,INK);
            centered(g,text("owned_column"),l+195,t+119,41,INK);
            centered(g,text("buy"),l+240,t+119,56,INK);
            centered(g,text("sell"),l+300,t+119,56,INK);
            for(int index=goodsPage*PAGE_SIZE;index<Math.min(data.rows().size(),(goodsPage+1)*PAGE_SIZE);index++) {
                var row=data.rows().get(index);int y=t+133+(index%PAGE_SIZE)*31;
                var stack=new ItemStack(BuiltInRegistries.ITEM.getValue(row.item()));int owned=owned(stack);
                if(index==(detail>=0?detail:goodsPage*PAGE_SIZE)) {
                    g.fill(l+32,y-1,l+362,y+27,0x33365E59);
                    g.fill(l+32,y-1,l+35,y+27,VillageUiTheme.TEAL);
                }
                g.fill(l+34,y+28,l+360,y+29,0x559A6620);g.renderItem(stack,l+36,y+5);
                VillageUiTheme.drawWrappedScaled(g,font,text("quantity",row.lot(),stack.getHoverName().getString()),l+57,y+4,86,INK,1f,2);
                boolean need=row.sku().startsWith("need_");
                String counts=need?text("home_need",row.demand()*row.lot()):row.stock()==0?text("sold_out"):text("stock",row.stock()*row.lot());
                if(need) {VillageUiTheme.drawWrappedScaled(g,font,text("need_short"),l+148,y+1,46,MUTED,.54f,1);centered(g,Integer.toString(row.demand()*row.lot()),l+148,y+10,43,INK);}
                else centered(g,row.stock()==0?"—":Integer.toString(row.stock()*row.lot()),l+148,y+10,43,INK);
                centered(g,Integer.toString(owned),l+195,y+10,41,INK);
                if(within(mx,my,l+33,y-1,201,29))hints.setTooltipForNextFrame(font,List.of(stack.getHoverName(),Component.literal(counts),Component.translatable(TEXT+"owned",owned),Component.translatable(TEXT+"details_hint")),mx,my);
                boolean buyEnabled=!row.buyQuote().equals(MarketPayloads.NONE) && data.balance()>=row.buyPrice();
                boolean sellEnabled=owned>=row.lot() && !row.sellQuote().equals(MarketPayloads.NONE) && data.balance()<=Long.MAX_VALUE-row.sellPrice();
                tradeButton(g,l+240,y,buyEnabled,mx,my,()->action(MarketPayloads.Kind.TRADE,row.buyQuote(),1));
                tradeButton(g,l+300,y,sellEnabled,mx,my,()->action(MarketPayloads.Kind.TRADE,row.sellQuote(),1));
                if(row.buyPrice()>0)KingdomUiAssets.moneyCentered(g,font,row.buyPrice(),l+240,y+7,56,buyEnabled);else line(g,text("unavailable"),l+259,y+8,24,MUTED);
                if(row.sellPrice()>0)KingdomUiAssets.moneyCentered(g,font,row.sellPrice(),l+300,y+7,56,sellEnabled);else line(g,text("unavailable"),l+321,y+8,24,MUTED);
                if(within(mx,my,l+240,y,116,24)) {
                    boolean buy=mx<l+298;
                    String why=waiting?"waiting":(buy?row.buyQuote():row.sellQuote()).equals(MarketPayloads.NONE)?"trade_unavailable":buy && data.balance()<row.buyPrice()?"money_missing":!buy && owned<row.lot()?"goods_missing":"one_click";
                    hints.setTooltipForNextFrame(font,List.of(Component.translatable(TEXT+why),Component.translatable(TEXT+"quantity",row.lot(),stack.getHoverName()),CurrencyService.formatBalance(buy?row.buyPrice():row.sellPrice())),mx,my);
                }
            }
            if(data.rows().size()>PAGE_SIZE) {
                button(g,l+150,t+260,24,13,"‹",goodsPage>0,mx,my,()->goodsPage=Math.max(0,goodsPage-1));
                line(g,(goodsPage+1)+" / "+((data.rows().size()+PAGE_SIZE-1)/PAGE_SIZE),l+185,t+263,40,INK);
                button(g,l+224,t+260,24,13,"›",(goodsPage+1)*PAGE_SIZE<data.rows().size(),mx,my,()->goodsPage=Math.min(goodsPage+1,(data.rows().size()-1)/PAGE_SIZE));
            }
            drawPriceDetails(g,l,t,mx,my);
            JournalV3Visuals.paper(g,l+90,t+280,442,19);
            String feedback=text(waiting?"waiting":"feedback."+data.feedback().name().toLowerCase(Locale.ROOT));
            if(!waiting && data.feedback()==MarketPayloads.Feedback.UPDATED && detail>=0 && detail<data.rows().size()) {
                var selected=data.rows().get(detail);
                if(!selected.buyQuote().equals(MarketPayloads.NONE) && data.balance()<selected.buyPrice()) feedback=text("money_missing");
                else if(!selected.sellQuote().equals(MarketPayloads.NONE) && owned(new ItemStack(BuiltInRegistries.ITEM.getValue(selected.item())))<selected.lot()) feedback=text("goods_missing");
                else if(selected.buyQuote().equals(MarketPayloads.NONE) && selected.sellQuote().equals(MarketPayloads.NONE)) feedback=text("trade_unavailable");
            }
            VillageUiTheme.drawStringScaled(g,font,VillageUiTheme.ellipsize(font,feedback,400),l+98,t+286,MUTED,.8f);
            if(within(mx,my,l+93,t+281,330,17))hints.setTooltipForNextFrame(font,Component.literal(feedback),mx,my);
            button(g,l+428,t+281,100,17,text("refresh"),!waiting,true,mx,my,()->action(MarketPayloads.Kind.REFRESH,MarketPayloads.NONE,1));
            JournalV3Visuals.ornaments(g,l,t,W,H);super.render(g,mx,my,delta);hints.draw(g,font,l,t,W,H);
        }finally{endResponsivePanel(g,scale);}
    }
    private void wallet(GuiGraphics g,String label,long amount,int x,int y,int w,int mx,int my) {
        JournalV3Visuals.paper(g,x,y,w,36);VillageUiTheme.drawStringScaled(g,font,VillageUiTheme.ellipsize(font,label,(int)((w-12)/.8f)),x+6,y+5,INK,.8f);
        KingdomUiAssets.moneyCentered(g,font,amount,x+4,y+20,w-8,false);
        if(within(mx,my,x,y,w,36))hints.setTooltipForNextFrame(font,CurrencyService.formatBalance(amount),mx,my);
    }
    private void drawPriceDetails(GuiGraphics g,int l,int t,int mx,int my) {
        JournalV3Visuals.paper(g,l+374,t+92,158,184);
        JournalV3Visuals.section(g,font,text("details_title"),l+377,t+95,152,1.05f);
        JournalV3Visuals.illustration(g,net.minecraft.resources.Identifier.fromNamespaceAndPath("village-quest","textures/gui/ui/v3/market_stall.png"),l+382,t+116,142,36);
        if(data.rows().isEmpty())return;
        if(detail>=data.rows().size())detail=-1;
        int selected=detail>=0?detail:Math.min(goodsPage*PAGE_SIZE,data.rows().size()-1);
        var row=data.rows().get(selected);var stack=new ItemStack(BuiltInRegistries.ITEM.getValue(row.item()));
        JournalV3Visuals.heading(g,font,stack.getHoverName().getString(),l+384,t+157,138,1.05f,INK);
        var info=priceDetails(row,stack.getHoverName(),text("stock",row.stock()*row.lot()));
        // Pack whole factors, never split a factor across page boundaries.
        var pagesOfLines=new ArrayList<List<String>>();var page=new ArrayList<String>();
        for(var entry:info.subList(1,info.size())) {
            var group=VillageUiTheme.wrapScaledLines(font,entry.getString(),138,.82f);
            if(group.size()>9) {
                if(!page.isEmpty()){pagesOfLines.add(List.copyOf(page));page.clear();}
                for(int start=0;start<group.size();start+=9)pagesOfLines.add(List.copyOf(group.subList(start,Math.min(group.size(),start+9))));
                continue;
            }
            if(!page.isEmpty() && page.size()+group.size()>9) {pagesOfLines.add(List.copyOf(page));page.clear();}
            page.addAll(group);
        }
        if(!page.isEmpty())pagesOfLines.add(List.copyOf(page));
        int pages=Math.max(1,pagesOfLines.size());detailPage=Math.clamp(detailPage,0,pages-1);
        var lines=pagesOfLines.isEmpty()?List.<String>of():pagesOfLines.get(detailPage);
        for(int i=0;i<lines.size();i++) {
            VillageUiTheme.drawStringScaled(g,font,lines.get(i),l+384,t+171+i*9,INK,.82f);
        }
        button(g,l+382,t+260,22,13,"‹",detailPage>0,mx,my,()->detailPage=Math.max(0,detailPage-1));
        line(g,(detailPage+1)+" / "+pages,l+438,t+263,42,INK);
        button(g,l+502,t+260,22,13,"›",detailPage+1<pages,mx,my,()->detailPage=Math.min(pages-1,detailPage+1));
    }
    private List<Component> priceDetails(MarketPayloads.Row row,Component name,String counts) {
        var adjustment=row.adjustment();
        return List.of(name,Component.literal(counts),
                Component.translatable(TEXT+"price_buy",row.adjustment().buying()),
                Component.translatable(TEXT+"price_sell",row.adjustment().selling()),
                Component.translatable(TEXT+"regional_supply"),
                Component.translatable(TEXT+"price_personality",data.factor()),
                Component.translatable(TEXT+"price_reputation",String.format(Locale.ROOT,"%+d",adjustment.reputation())),
                Component.translatable(TEXT+"price_stock",adjustment.availability()),
                Component.translatable(TEXT+"price_need",adjustment.need()),
                Component.translatable(TEXT+"price_trust",adjustment.trust()),
                Component.translatable(TEXT+"price_discount",data.discount()),
                Component.translatable(TEXT+"price_limit"));
    }
    private void action(MarketPayloads.Kind kind,UUID quote,int lots) {
        if(UiTestTour.active()) return;
        if(closed || waiting && kind!=MarketPayloads.Kind.CLOSE || !ClientPlayNetworking.canSend(MarketPayloads.Action.ID)) return;
        ClientPlayNetworking.send(new MarketPayloads.Action(session(),data.revision(),kind,quote,lots));
        if(kind!=MarketPayloads.Kind.CLOSE) waiting=true;
    }
    @Override protected boolean handleMouseClicked(MouseButtonEvent click,boolean doubled) {
        if(click.button()!=InputConstants.MOUSE_BUTTON_LEFT) return super.handleMouseClicked(click,doubled);
        int x=responsiveMouseX(click.x(),W,H),y=responsiveMouseY(click.y(),W,H);
        for(var control:List.copyOf(controls)) if(within(x,y,control.x,control.y,control.width,control.height)) {
            if(control.enabled) control.action.run(); return true;
        }
        {
            int l=(width-W)/2,t=(height-H)/2;
            for(int i=goodsPage*PAGE_SIZE;i<Math.min(data.rows().size(),(goodsPage+1)*PAGE_SIZE);i++)
                if(within(x,y,l+33,t+132+(i%PAGE_SIZE)*31,201,29)) {detail=i;detailPage=0;return true;}
        }
        return false;
    }
    @Override public void onClose() { detail=-1; closeSession(); super.onClose(); }
    @Override public void removed() { closeSession(); super.removed(); }
    private void closeSession() { if(!closed) { action(MarketPayloads.Kind.CLOSE,MarketPayloads.NONE,data.lots()); closed=true; remember(); } }
    private void remember() { CLOSED.add(session()); while(CLOSED.size()>32) CLOSED.remove(CLOSED.iterator().next()); }
    private static boolean within(int x,int y,int left,int top,int width,int height) { return x>=left && x<left+width && y>=top && y<top+height; }
    private int owned(ItemStack commodity) {
        if(UiTestTour.active()) return 64;
        if(minecraft==null || minecraft.player==null) return 0;
        int count=0; for(int slot=0;slot<36;slot++) {var stack=minecraft.player.getInventory().getItem(slot);
            if(ItemStack.isSameItemSameComponents(stack,commodity)) count+=stack.getCount();}
        return count;
    }
    private record Control(int x,int y,int width,boolean enabled,Runnable action,int height) {
        Control(int x,int y,int width,boolean enabled,Runnable action) { this(x,y,width,enabled,action,22); }
    }
}
