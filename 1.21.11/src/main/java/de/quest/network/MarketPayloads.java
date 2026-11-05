package de.quest.network;

import de.quest.kingdom.MarketPricePolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Private bounded market presentation. Actions contain no item, stock, price or inventory evidence. */
public final class MarketPayloads {
    public static final UUID NONE=new UUID(0,0);
    private MarketPayloads() {}
    public enum Kind { TRADE,LOTS,REFRESH,CLOSE }
    public enum Feedback { UPDATED,COMMITTED,CANCELLED,HELD,REJECTED,EXPIRED,INVENTORY,CLOSED,BUYBACKS_UNAVAILABLE,NEED_EXHAUSTED,BUDGET_EXHAUSTED,CHECKING }
    public record Row(String sku,Identifier item,int lot,int stock,int demand,MarketPricePolicy.Adjustment adjustment,
                      UUID buyQuote,long buyPrice,UUID sellQuote,long sellPrice) {
        public Row {
            Objects.requireNonNull(adjustment);
            if(sku==null || !sku.matches("[a-z0-9_]{1,32}") || item==null || !item.getNamespace().equals("minecraft")
                    || item.getPath().equals("air") || lot<1 || lot>64) throw new IllegalArgumentException("Invalid market goods row");
            bounded(stock,128/lot); bounded(demand,128/lot); price(buyQuote,buyPrice); price(sellQuote,sellPrice);
        }
    }
    public record View(UUID session,long revision,boolean initial,boolean active,String trader,String home,String market,
                       int lots,long balance,int budget,int factor,int discount,List<Row> rows,Feedback feedback,long departureTicks) implements CustomPacketPayload {
        public View(UUID session,long revision,boolean initial,boolean active,String trader,String home,String market,
                int lots,long balance,int budget,int factor,int discount,List<Row> rows,Feedback feedback) {
            this(session,revision,initial,active,trader,home,market,lots,balance,budget,factor,discount,rows,feedback,0);
        }
        public View {
            Objects.requireNonNull(session); Objects.requireNonNull(feedback); text(trader); text(home); text(market);
            if(departureTicks<0 || departureTicks>24000 || session.equals(NONE) || revision<0 || balance<0 || lots<1 || lots>2 || factor< -15 || factor>15
                    || discount<0 || discount>15 || initial && !active || rows==null || !active && !rows.isEmpty())
                throw new IllegalArgumentException("Invalid market view");
            bounded(budget,16); bounded(rows.size(),8); rows=List.copyOf(rows);
            if(rows.stream().map(Row::sku).distinct().count()!=rows.size()) throw new IllegalArgumentException("Duplicate market goods");
        }
        public static final Type<View> ID=new Type<>(Identifier.fromNamespaceAndPath("village-quest","market_view"));
        public static final StreamCodec<RegistryFriendlyByteBuf,View> CODEC=StreamCodec.of((buf,view) -> {
            buf.writeUUID(view.session); buf.writeVarLong(view.revision); buf.writeBoolean(view.initial); buf.writeBoolean(view.active);
            buf.writeUtf(view.trader,96); buf.writeUtf(view.home,96); buf.writeUtf(view.market,96); buf.writeVarInt(view.lots);
            buf.writeVarLong(view.balance); buf.writeVarInt(view.budget); buf.writeVarInt(view.factor); buf.writeVarInt(view.discount);
            buf.writeVarInt(view.rows.size());
            for(var row:view.rows) {
                buf.writeUtf(row.sku,32); buf.writeIdentifier(row.item); buf.writeVarInt(row.lot); buf.writeVarInt(row.stock); buf.writeVarInt(row.demand);
                buf.writeVarInt(row.adjustment.availability()); buf.writeVarInt(row.adjustment.need()); buf.writeVarInt(row.adjustment.trust());
                buf.writeVarInt(row.adjustment.buying()); buf.writeVarInt(row.adjustment.selling()); buf.writeVarInt(row.adjustment.reputation());
                buf.writeUUID(row.buyQuote); buf.writeVarLong(row.buyPrice); buf.writeUUID(row.sellQuote); buf.writeVarLong(row.sellPrice);
            }
            buf.writeEnum(view.feedback); buf.writeVarLong(view.departureTicks);
        },buf -> {
            var session=buf.readUUID(); long revision=buf.readVarLong(); boolean initial=buf.readBoolean(),active=buf.readBoolean();
            String trader=buf.readUtf(96),home=buf.readUtf(96),market=buf.readUtf(96); int lots=buf.readVarInt();
            long balance=buf.readVarLong(); int budget=buf.readVarInt(),factor=buf.readVarInt(),discount=buf.readVarInt();
            int count=bounded(buf.readVarInt(),8); var rows=new ArrayList<Row>(count);
            for(int index=0;index<count;index++) rows.add(new Row(buf.readUtf(32),buf.readIdentifier(),buf.readVarInt(),buf.readVarInt(),
                    buf.readVarInt(),new MarketPricePolicy.Adjustment(buf.readVarInt(),buf.readVarInt(),buf.readVarInt(),buf.readVarInt(),buf.readVarInt(),buf.readVarInt()),
                    buf.readUUID(),buf.readVarLong(),buf.readUUID(),buf.readVarLong()));
            return new View(session,revision,initial,active,trader,home,market,lots,balance,budget,factor,discount,rows,buf.readEnum(Feedback.class),buf.readVarLong());
        });
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }
    public record Action(UUID session,long revision,Kind kind,UUID quote,int lots) implements CustomPacketPayload {
        public Action {
            Objects.requireNonNull(session); Objects.requireNonNull(kind); Objects.requireNonNull(quote);
            if(session.equals(NONE) || revision<0 || lots<1 || lots>2 || (kind==Kind.TRADE)==quote.equals(NONE))
                throw new IllegalArgumentException("Invalid market action");
        }
        public static final Type<Action> ID=new Type<>(Identifier.fromNamespaceAndPath("village-quest","market_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Action> CODEC=StreamCodec.of((buf,action) -> {
            buf.writeUUID(action.session); buf.writeVarLong(action.revision); buf.writeEnum(action.kind); buf.writeUUID(action.quote); buf.writeVarInt(action.lots);
        },buf -> new Action(buf.readUUID(),buf.readVarLong(),buf.readEnum(Kind.class),buf.readUUID(),buf.readVarInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }
    private static int bounded(int value,int max) {
        if(value<0 || value>max) throw new IllegalArgumentException("Unbounded market packet"); return value;
    }
    private static void price(UUID quote,long value) {
        if(quote==null || value<0 || value>3_000_000_000L || quote.equals(NONE)!=(value==0))
            throw new IllegalArgumentException("Invalid displayed market quote");
    }
    private static void text(String value) {
        if(value==null || value.length()>96 || value.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Invalid market display text");
    }
}
