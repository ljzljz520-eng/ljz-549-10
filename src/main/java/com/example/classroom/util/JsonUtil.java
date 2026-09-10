package com.example.classroom.util;

import java.util.List;

import com.example.classroom.model.Classroom;

/**
 * 极简 JSON 输出工具（不引入第三方依赖，避免中文被 unicode 转义）。
 */
public final class JsonUtil {

    private JsonUtil() {
    }

    /** 统一成功响应 */
    public static String success(String message, List<Classroom> rooms) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"code\":0,\"message\":\"").append(escape(message)).append("\",");
        sb.append("\"count\":").append(rooms.size()).append(",\"data\":[");
        for (int i = 0; i < rooms.size(); i++) {
            Classroom r = rooms.get(i);
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"roomNo\":\"").append(escape(r.getRoomNo())).append('"');
            sb.append(",\"buildingCode\":\"").append(escape(r.getBuildingCode())).append('"');
            sb.append(",\"buildingName\":\"").append(escape(r.getBuildingName())).append('"');
            sb.append(",\"capacity\":").append(r.getCapacity());
            sb.append(",\"type\":\"").append(escape(r.getType())).append("\"}");
        }
        sb.append("]}");
        return sb.toString();
    }

    /** 统一失败响应（参数错误 code=400；无结果 code=1001） */
    public static String error(int code, String message) {
        return "{\"code\":" + code + ",\"message\":\"" + escape(message) + "\",\"data\":null}";
    }

    /** JSON 字符串转义 */
    public static String escape(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
}
