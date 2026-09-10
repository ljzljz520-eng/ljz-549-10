package com.example.classroom.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.example.classroom.model.Classroom;

/**
 * 教室与占用情况服务（演示环境，无数据库）。
 *
 * 占用情况由 教学楼+教室+日期+节次 做确定性哈希得到：
 * 同一组查询条件结果稳定，便于联调回归；约 30% 教室被判定为占用。
 *
 * 特殊规则：周末第 5 节（13:30-14:15）实验楼 D 全部被占用，
 * 用于稳定复现“无空闲教室（code=1001）”场景。
 */
public class ClassroomService {

    /** 教学楼编码 -> 配置（保持下拉顺序） */
    private static final Map<String, BuildingInfo> BUILDINGS = new LinkedHashMap<String, BuildingInfo>();

    static {
        BUILDINGS.put("A", new BuildingInfo("教一楼", 5, 8, 60));
        BUILDINGS.put("B", new BuildingInfo("教二楼", 6, 6, 80));
        BUILDINGS.put("C", new BuildingInfo("教三楼", 4, 5, 45));
        BUILDINGS.put("D", new BuildingInfo("实验楼", 5, 4, 30));
    }

    /**
     * 查询指定条件下的空闲教室。
     *
     * @param buildingCode 教学楼编码
     * @param date         日期 yyyy-MM-dd
     * @param period       节次 1-10
     * @return 空闲教室列表（可能为空）
     */
    public List<Classroom> findFreeRooms(String buildingCode, String date, int period) {
        BuildingInfo info = BUILDINGS.get(buildingCode);
        List<Classroom> free = new ArrayList<Classroom>();

        boolean weekendD5 = "D".equals(buildingCode) && period == 5 && isWeekend(date);

        for (int floor = 1; floor <= info.floors; floor++) {
            for (int n = 1; n <= info.roomsPerFloor; n++) {
                String roomNo = buildingCode + floor + String.format("%02d", n);
                String key = buildingCode + "|" + roomNo + "|" + date + "|" + period;

                boolean occupied = weekendD5 || (hash(key) % 100 < 30);
                if (occupied) {
                    continue;
                }

                int capacity = info.baseCapacity + (hash(roomNo) % 21); // 容量做点差异
                free.add(new Classroom(buildingCode, info.name, roomNo, capacity,
                        resolveType(buildingCode, floor, n)));
            }
        }
        return free;
    }

    public boolean supportsBuilding(String code) {
        return code != null && BUILDINGS.containsKey(code);
    }

    /** 日期是否为周六/周日 */
    private boolean isWeekend(String date) {
        // 使用 java.time 解析，按 ISO 周历判断，不依赖默认 Locale/时区
        java.time.LocalDate d = java.time.LocalDate.parse(date);
        java.time.DayOfWeek dow = d.getDayOfWeek();
        return dow == java.time.DayOfWeek.SATURDAY || dow == java.time.DayOfWeek.SUNDAY;
    }

    private String resolveType(String code, int floor, int n) {
        if ("A".equals(code)) {
            return n % 4 == 1 ? "多媒体教室" : "普通教室";
        }
        if ("B".equals(code)) {
            return n == 1 ? "阶梯教室" : "多媒体教室";
        }
        if ("C".equals(code)) {
            return "多媒体教室";
        }
        if ("D".equals(code)) {
            return n % 2 == 0 ? "机房" : "实验室";
        }
        return "普通教室";
    }

    /** 稳定字符串哈希（FNV-1a 变种，只依赖输入内容） */
    private static int hash(String s) {
        int h = 0x811c9dc5;
        for (int i = 0; i < s.length(); i++) {
            h ^= s.charAt(i);
            h *= 0x01000193;
        }
        return h & 0x7fffffff;
    }

    private static class BuildingInfo {
        final String name;
        final int floors;
        final int roomsPerFloor;
        final int baseCapacity;

        BuildingInfo(String name, int floors, int roomsPerFloor, int baseCapacity) {
            this.name = name;
            this.floors = floors;
            this.roomsPerFloor = roomsPerFloor;
            this.baseCapacity = baseCapacity;
        }
    }
}
