package de.quest.commands;
import java.util.*;
final class PersonalResetConfirmations {
    private record Ticket(Object server,long expires) {}
    private final Map<UUID,Ticket> tickets=new HashMap<>();
    void request(Object server,UUID owner,long now) {
        tickets.entrySet().removeIf(e -> e.getValue().expires()<=now);
        tickets.put(owner,new Ticket(server,Math.addExact(now,120_000L)));
    }
    boolean consume(Object server,UUID owner,long now) {
        Ticket ticket=tickets.remove(owner);
        return ticket!=null && ticket.server()==server && now<ticket.expires();
    }
}
