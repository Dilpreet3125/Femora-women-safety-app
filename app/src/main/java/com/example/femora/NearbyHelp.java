package com.example.femora;

public class NearbyHelp {
    private String name;
    private String address;
    private String distance;
    private String phone;
    private int iconRes;

    public NearbyHelp(String name, String address, String distance, String phone, int iconRes) {
        this.name = name;
        this.address = address;
        this.distance = distance;
        this.phone = phone;
        this.iconRes = iconRes;
    }

    public String getName() { return name; }
    public String getAddress() { return address; }
    public String getDistance() { return distance; }
    public String getPhone() { return phone; }
    public int getIconRes() { return iconRes; }
}