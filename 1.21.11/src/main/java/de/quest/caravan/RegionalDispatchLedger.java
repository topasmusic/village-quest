package de.quest.caravan;

import de.quest.data.PlayerQuestData;
import java.util.UUID;

/** One saved regional freight assignment per owner, independent of compacting route slots. */
public final class RegionalDispatchLedger {
    private static final String KEY = "regional_dispatch_v1";

    private RegionalDispatchLedger() {}

    public enum Stage {
        WAITING_AT_SOURCE, LEG_ONE, WAITING_AT_HUB, LEG_TWO,
        HELD_AT_DESTINATION, DELIVERED, CANCELLED_SAFE
    }

    public enum Endpoint { SOURCE, HUB, TARGET }

    public record Dispatch(UUID id, UUID sourceConnection, UUID targetConnection,
                           String dimension, int sourceX, int sourceZ, int targetX, int targetZ,
                           int sourceType, int targetType, String itemId, int amount, int reward,
                           Stage stage) {
        public Dispatch {
            if (id == null || sourceConnection == null || targetConnection == null
                    || sourceConnection.equals(targetConnection) || stage == null
                    || dimension == null || dimension.isBlank() || dimension.contains("|")
                    || itemId == null || itemId.isBlank() || itemId.contains("|")
                    || sourceType <= 0 || targetType <= 0 || amount <= 0 || amount > 64
                    || reward <= 0 || reward > 1_000) {
                throw new IllegalArgumentException("invalid regional dispatch");
            }
        }

        Dispatch at(Stage next) {
            return new Dispatch(id, sourceConnection, targetConnection, dimension,
                    sourceX, sourceZ, targetX, targetZ, sourceType, targetType,
                    itemId, amount, reward, next);
        }
    }

    public static Dispatch read(PlayerQuestData data) {
        if (data == null) return null;
        String saved = data.getTradeRouteString(KEY);
        if (saved.isBlank()) return null;
        String[] fields = saved.split("\\|", -1);
        if (fields.length != 15 || !"v1".equals(fields[0])) return null;
        try {
            return new Dispatch(UUID.fromString(fields[1]), UUID.fromString(fields[2]),
                    UUID.fromString(fields[3]), fields[4], Integer.parseInt(fields[5]),
                    Integer.parseInt(fields[6]), Integer.parseInt(fields[7]), Integer.parseInt(fields[8]),
                    Integer.parseInt(fields[9]), Integer.parseInt(fields[10]), fields[11],
                    Integer.parseInt(fields[12]), Integer.parseInt(fields[13]),
                    Stage.valueOf(fields[14]));
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    public static boolean start(PlayerQuestData data, Dispatch dispatch) {
        if (data == null || dispatch == null || dispatch.stage() != Stage.WAITING_AT_SOURCE) return false;
        String existing = data.getTradeRouteString(KEY);
        if (!existing.isBlank() && (read(data) == null || read(data).stage() != Stage.DELIVERED)) return false;
        write(data, dispatch);
        return true;
    }

    /** Returns true only on the first successful delivery, so the caller may grant one reward. */
    public static boolean arrive(PlayerQuestData data, UUID connection, Endpoint endpoint,
                                 boolean sourceAvailable, boolean targetAvailable, boolean hubAvailable) {
        Dispatch current = read(data);
        if (current == null || connection == null || endpoint == null) return false;
        Stage next = switch (current.stage()) {
            case WAITING_AT_SOURCE -> connection.equals(current.sourceConnection())
                    && endpoint == Endpoint.SOURCE && sourceAvailable && hubAvailable
                    ? Stage.LEG_ONE : null;
            case LEG_ONE -> connection.equals(current.sourceConnection()) && endpoint == Endpoint.HUB
                    ? Stage.WAITING_AT_HUB : null;
            case WAITING_AT_HUB -> connection.equals(current.targetConnection())
                    && endpoint == Endpoint.HUB && hubAvailable && targetAvailable
                    ? Stage.LEG_TWO : null;
            case LEG_TWO -> connection.equals(current.targetConnection()) && endpoint == Endpoint.TARGET
                    ? targetAvailable ? Stage.DELIVERED : Stage.HELD_AT_DESTINATION : null;
            default -> null;
        };
        if (next == null) return false;
        write(data, current.at(next));
        return next == Stage.DELIVERED;
    }

    public static boolean restoreDestination(PlayerQuestData data, boolean targetAvailable) {
        Dispatch current = read(data);
        if (current == null || current.stage() != Stage.HELD_AT_DESTINATION || !targetAvailable) return false;
        write(data, current.at(Stage.DELIVERED));
        return true;
    }

    /** Removing the already completed first leg does not strand cargo waiting at the hub. */
    public static boolean cancelForRemovedRoute(PlayerQuestData data, UUID connection) {
        Dispatch current = read(data);
        if (current == null || connection == null) return false;
        boolean sourceNeeded = current.stage() == Stage.WAITING_AT_SOURCE || current.stage() == Stage.LEG_ONE;
        boolean targetNeeded = current.stage() != Stage.DELIVERED
                && current.stage() != Stage.CANCELLED_SAFE;
        if (!(sourceNeeded && connection.equals(current.sourceConnection())
                || targetNeeded && connection.equals(current.targetConnection()))) return false;
        write(data, current.at(Stage.CANCELLED_SAFE));
        return true;
    }

    public static Dispatch claimCancelled(PlayerQuestData data) {
        Dispatch current = read(data);
        if (current == null || current.stage() != Stage.CANCELLED_SAFE) return null;
        claimCancelled(data, current.amount());
        return current;
    }

    /** Consume only freight actually inserted into ordinary inventory, retaining the rest. */
    public static boolean claimCancelled(PlayerQuestData data, int returnedAmount) {
        Dispatch current = read(data);
        if (current == null || current.stage() != Stage.CANCELLED_SAFE
                || returnedAmount <= 0 || returnedAmount > current.amount()) return false;
        if (returnedAmount == current.amount()) data.setTradeRouteString(KEY, "");
        else write(data, new Dispatch(current.id(), current.sourceConnection(), current.targetConnection(),
                current.dimension(), current.sourceX(), current.sourceZ(), current.targetX(), current.targetZ(),
                current.sourceType(), current.targetType(), current.itemId(), current.amount() - returnedAmount,
                current.reward(), current.stage()));
        return true;
    }

    private static void write(PlayerQuestData data, Dispatch dispatch) {
        data.setTradeRouteString(KEY, String.join("|", "v1", dispatch.id().toString(),
                dispatch.sourceConnection().toString(), dispatch.targetConnection().toString(),
                dispatch.dimension(), Integer.toString(dispatch.sourceX()),
                Integer.toString(dispatch.sourceZ()), Integer.toString(dispatch.targetX()),
                Integer.toString(dispatch.targetZ()), Integer.toString(dispatch.sourceType()),
                Integer.toString(dispatch.targetType()), dispatch.itemId(),
                Integer.toString(dispatch.amount()), Integer.toString(dispatch.reward()),
                dispatch.stage().name()));
    }
}
