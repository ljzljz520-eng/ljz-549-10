package com.example.classroom.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.regex.Pattern;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.example.classroom.model.Classroom;
import com.example.classroom.service.ClassroomService;
import com.example.classroom.util.JsonUtil;

/**
 * 空闲教室查询接口
 *
 * GET /api/classroom/free?building=A&date=2026-09-14&period=1
 *
 * 响应约定（HTTP 状态码与业务 code 对齐）：
 *   成功有结果：HTTP 200  {"code":0,"message":"查询成功","count":N,"data":[...]}
 *   参数错误：  HTTP 400  {"code":400,"message":"错误原因","data":null}
 *   无结果：    HTTP 200  {"code":1001,"message":"该条件下暂无空闲教室","data":null}
 *
 * 联调用：delay 毫秒（0-30000）可人为增加后端耗时，用于验证前端 10s 超时。
 */
@WebServlet("/api/classroom/free")
public class ClassroomQueryServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private static final Pattern DATE_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

    private final ClassroomService classroomService = new ClassroomService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        applyCommonHeaders(resp);

        String building = trim(req.getParameter("building"));
        String date = trim(req.getParameter("date"));
        String periodStr = trim(req.getParameter("period"));

        // ---------- 参数校验 ----------
        if (building == null || building.isEmpty()) {
            writeJson(resp, HttpServletResponse.SC_BAD_REQUEST,
                    JsonUtil.error(400, "缺少必填参数：building（教学楼）"));
            return;
        }
        if (!classroomService.supportsBuilding(building)) {
            writeJson(resp, HttpServletResponse.SC_BAD_REQUEST,
                    JsonUtil.error(400, "参数 building 非法，仅支持 A/B/C/D"));
            return;
        }
        if (date == null || date.isEmpty()) {
            writeJson(resp, HttpServletResponse.SC_BAD_REQUEST,
                    JsonUtil.error(400, "缺少必填参数：date（日期，格式 yyyy-MM-dd）"));
            return;
        }
        if (!DATE_PATTERN.matcher(date).matches()) {
            writeJson(resp, HttpServletResponse.SC_BAD_REQUEST,
                    JsonUtil.error(400, "参数 date 格式错误，应为 yyyy-MM-dd"));
            return;
        }
        try {
            LocalDate parsed = LocalDate.parse(date);
            if (parsed.getYear() < 2000 || parsed.getYear() > 2099) {
                writeJson(resp, HttpServletResponse.SC_BAD_REQUEST,
                        JsonUtil.error(400, "参数 date 超出允许范围（2000-2099 年）"));
                return;
            }
        } catch (DateTimeParseException e) {
            writeJson(resp, HttpServletResponse.SC_BAD_REQUEST,
                    JsonUtil.error(400, "参数 date 不是有效日期"));
            return;
        }

        int period;
        try {
            period = Integer.parseInt(periodStr);
        } catch (NumberFormatException e) {
            writeJson(resp, HttpServletResponse.SC_BAD_REQUEST,
                    JsonUtil.error(400, "缺少或非法参数：period（节次，1-10 的整数）"));
            return;
        }
        if (period < 1 || period > 10) {
            writeJson(resp, HttpServletResponse.SC_BAD_REQUEST,
                    JsonUtil.error(400, "参数 period 超出范围，应为 1-10"));
            return;
        }

        // ---------- 联调用：模拟后端延迟 ----------
        long delay = parseDelay(req.getParameter("delay"));
        if (delay > 0) {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }

        // ---------- 业务查询 ----------
        List<Classroom> freeRooms = classroomService.findFreeRooms(building, date, period);
        if (freeRooms.isEmpty()) {
            writeJson(resp, HttpServletResponse.SC_OK,
                    JsonUtil.error(1001, "该条件下暂无空闲教室，请更换日期或节次重试"));
            return;
        }
        writeJson(resp, HttpServletResponse.SC_OK,
                JsonUtil.success("查询成功", freeRooms));
    }

    /** 简单 CORS 支持，方便把 index.html 单独放到其他静态服务器 / 直接 file:// 打开联调 */
    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        applyCommonHeaders(resp);
        resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }

    private void applyCommonHeaders(HttpServletResponse resp) {
        resp.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Access-Control-Allow-Origin", "*");
        resp.setHeader("Access-Control-Allow-Methods", "GET, OPTIONS");
        resp.setHeader("Access-Control-Allow-Headers", "Content-Type");
        resp.setHeader("Cache-Control", "no-store");
    }

    private long parseDelay(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0L;
        }
        try {
            long d = Long.parseLong(value.trim());
            if (d < 0) {
                return 0L;
            }
            return Math.min(d, 30000L); // 最多 30 秒，防止误操作
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private void writeJson(HttpServletResponse resp, int status, String body) throws IOException {
        resp.setStatus(status);
        PrintWriter writer = resp.getWriter();
        writer.write(body);
        writer.flush();
    }

    private static String trim(String s) {
        return s == null ? null : s.trim();
    }
}
