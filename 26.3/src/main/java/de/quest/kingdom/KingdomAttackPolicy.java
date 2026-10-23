package de.quest.kingdom;

/** Every proof is required; uncertainty never becomes a refused-help incident. */
public final class KingdomAttackPolicy {
    private KingdomAttackPolicy() {}
    public record Evidence(boolean loaded,boolean alive,boolean playable,boolean peacefulExcluded,
                           boolean visible,boolean access,boolean rescuable,int remainingTicks) {}
    public static boolean equipped(float health,int armor,double attackDamage) {
        return Float.isFinite(health) && health>=16 && armor>=12 && Double.isFinite(attackDamage) && attackDamage>=6;
    }
    public static boolean eligible(Evidence e) {
        return e!=null && e.loaded && e.alive && e.playable && e.peacefulExcluded && e.visible && e.access && e.rescuable && e.remainingTicks>=600;
    }
}
