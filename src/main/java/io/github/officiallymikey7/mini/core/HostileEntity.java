package io.github.officiallymikey7.mini.core;

/** A hostile mob detected near the agent. */
public final class HostileEntity {
    public final String type;
    public final double distance;

    public HostileEntity(String type, double distance) {
        this.type = type;
        this.distance = distance;
    }

    @Override
    public String toString() {
        return type + "@" + String.format("%.1f", distance) + "m";
    }
}
