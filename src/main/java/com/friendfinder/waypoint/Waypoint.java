package com.friendfinder.waypoint;

public class Waypoint {
    private String name;
    private String dimension;
    private int x;
    private int y;
    private int z;

    public Waypoint() {
    }

    public Waypoint(String name, String dimension, int x, int y, int z) {
        this.name = name;
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public String getName() { return name; }
    public String getDimension() { return dimension; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }

    public double distanceTo(double px, double py, double pz) {
        double dx = px - x;
        double dy = py - y;
        double dz = pz - z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
