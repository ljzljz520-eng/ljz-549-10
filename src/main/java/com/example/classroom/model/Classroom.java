package com.example.classroom.model;

/**
 * 教室信息（演示用内存模型）。
 */
public class Classroom {

    private final String buildingCode;
    private final String buildingName;
    private final String roomNo;
    private final int capacity;
    private final String type;

    public Classroom(String buildingCode, String buildingName,
                     String roomNo, int capacity, String type) {
        this.buildingCode = buildingCode;
        this.buildingName = buildingName;
        this.roomNo = roomNo;
        this.capacity = capacity;
        this.type = type;
    }

    public String getBuildingCode() {
        return buildingCode;
    }

    public String getBuildingName() {
        return buildingName;
    }

    public String getRoomNo() {
        return roomNo;
    }

    public int getCapacity() {
        return capacity;
    }

    public String getType() {
        return type;
    }
}
