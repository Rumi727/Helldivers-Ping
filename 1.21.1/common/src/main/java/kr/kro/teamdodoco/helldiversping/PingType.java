package kr.kro.teamdodoco.helldiversping;

public enum PingType {
    /** A block / location in the world. */
    BLOCK,
    /** A living entity or other pickable entity. The ping follows it and disappears when it dies. */
    ENTITY,
    /** A dropped item entity. Shows the item icon and follows the item. */
    ITEM;

    private static final PingType[] VALUES = values();

    public static PingType byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : BLOCK;
    }
}
