# 教室空闲状态查询

原生 **HTML + CSS + JavaScript** 前端 + **Java Servlet** 后端的教室空闲查询小应用（无前端框架、无第三方 JSON 依赖）。

## 1. 目录结构

```
.
├── pom.xml                                  # Maven 构建（war 包）
└── src/main/
    ├── java/com/example/classroom/
    │   ├── model/Classroom.java             # 教室模型
    │   ├── service/ClassroomService.java    # 空闲教室查询（确定性模拟占用，无数据库）
    │   ├── servlet/ClassroomQueryServlet.java # 查询接口 /api/classroom/free
    │   └── util/JsonUtil.java               # 极简 JSON 输出
    └── webapp/
        ├── index.html                       # 查询页面
        ├── css/style.css
        ├── js/app.js                        # 查询逻辑 + 10s 超时
        └── WEB-INF/web.xml
```

## 2. 本地启动

### 方式一：Jetty 插件（推荐，免装容器）

要求：JDK 8+、Maven 3.6+

```bash
mvn jetty:run
```

启动后浏览器打开：<http://localhost:8080/>

### 方式二：部署到 Tomcat 9

```bash
mvn clean package
# 把 target/classroom-query.war 丢进 Tomcat 9 的 webapps/
# 访问 http://localhost:8080/classroom-query/
```

> 注意：Servlet 基于 `javax.servlet`（4.0），请使用 **Tomcat 9 / Jetty 9**，不要用 Tomcat 10+（Jakarta 包名不兼容）。

## 3. 接口约定

### 请求

```
GET /api/classroom/free?building={教学楼}&date={日期}&period={节次}
```

| 参数 | 必填 | 说明 |
|---|---|---|
| `building` | 是 | 教学楼编码：`A` 教一楼、`B` 教二楼、`C` 教三楼、`D` 实验楼 |
| `date` | 是 | 日期，格式严格为 `yyyy-MM-dd`，如 `2026-09-14` |
| `period` | 是 | 节次，整数 `1`~`10` |
| `delay` | 否 | **仅联调用**，模拟后端延迟毫秒数（0~30000），用于验证前端超时 |

### 响应（HTTP 状态码与业务 code 对齐）

**① 成功（有结果）** `HTTP 200`

```json
{
  "code": 0,
  "message": "查询成功",
  "count": 24,
  "data": [
    { "roomNo": "A106", "buildingCode": "A", "buildingName": "教一楼", "capacity": 72, "type": "多媒体教室" }
  ]
}
```

**② 参数错误** `HTTP 400`

```json
{ "code": 400, "message": "参数 period 超出范围，应为 1-10", "data": null }
```

**③ 查询成功但无空闲教室** `HTTP 200`

```json
{ "code": 1001, "message": "该条件下暂无空闲教室，请更换日期或节次重试", "data": null }
```

## 4. 前端行为说明

- 三个条件任一未选：**前端直接拦截**，不发请求，黄色提示并聚焦对应控件。
- 请求发出后按钮置灰显示“查询中…”，结果区显示加载动画，禁止重复提交。
- **超时处理**：使用 `fetch + AbortController`，请求超过 **10 秒**未响应自动 `abort`，
  页面展示“查询超时：请求超过 10 秒未响应”，不出现页面卡死。
- 网络不通/服务未启动：展示“网络异常，无法连接到查询服务”。
- 表格数据全部用 `textContent` 插入，避免 XSS。

## 5. 联调测试说明

### 5.1 用 curl / 浏览器直接验证后端

```bash
# ① 成功（稳定返回 24 间空闲教室）
curl "http://localhost:8080/api/classroom/free?building=A&date=2026-09-14&period=1"

# ② 参数错误：节次非法 -> HTTP 400, code=400
curl -i "http://localhost:8080/api/classroom/free?building=A&date=2026-09-14&period=11"

# ② 参数错误：日期格式错 -> HTTP 400, code=400
curl -i "http://localhost:8080/api/classroom/free?building=A&date=2026/09/14&period=1"

# ② 参数错误：教学楼非法 / 缺参
curl -i "http://localhost:8080/api/classroom/free?building=X&date=2026-09-14&period=1"
curl -i "http://localhost:8080/api/classroom/free?date=2026-09-14&period=1"

# ③ 无结果（固定造数：周末第 5 节实验楼 D 全部占用）-> code=1001
curl -i "http://localhost:8080/api/classroom/free?building=D&date=2026-09-13&period=5"
# 2026-09-12（周六）同样为无结果
curl -i "http://localhost:8080/api/classroom/free?building=D&date=2026-09-12&period=5"

# ④ 超时联调：后端故意延迟 12 秒，前端 10 秒即超时中断
curl "http://localhost:8080/api/classroom/free?building=A&date=2026-09-14&period=1&delay=12000"
```

### 5.2 页面手工测试用例

| # | 操作 | 预期结果 |
|---|---|---|
| 1 | 不选教学楼直接点查询 | 黄色提示“请选择教学楼”，不发请求 |
| 2 | 不选日期 / 不选节次 | 对应提示并聚焦控件，不发请求 |
| 3 | 教一楼 + 2026-09-14 + 第1节 | 表格展示 **24 间**空闲教室，标题显示总数与查询条件 |
| 4 | 实验楼 + 2026-09-13（周日）+ 第5节 | 黄色“暂无空闲教室”（code 1001） |
| 5 | 实验楼 + 2026-09-14（周一）+ 第1节 | 正常返回空闲教室列表（15 间） |
| 6 | 重复点击查询 | 请求中按钮禁用，不会并发重复请求 |
| 7 | 用 5.3 方式制造慢响应 | 10 秒后显示红色“查询超时”，可重新查询 |
| 8 | 停掉后端再查询 | 红色“网络异常，无法连接到查询服务” |

> 占用数据由 `教学楼|教室|日期|节次` 做确定性哈希生成，同一条件结果恒定，方便回归测试。

### 5.3 超时联调的两种方法

1. **后端延迟参数（无需改代码）**：后端已支持 `delay`，可用浏览器控制台快速触发：
   ```js
   // 直接在页面 Console 执行（app.js 已暴露调试方法）
   __query({ building: 'A', date: '2026-09-14', period: 1 }); // 正常查询
   ```
   然后手动请求带延迟的地址确认超时：
   浏览器地址栏或 curl 访问带 `delay=12000` 的接口可观察后端确实 12 秒才返回；
   页面侧可用 DevTools 的 Network → 选择该请求 → 右键 "Block request URL" 或
   Throttling 设为 Offline 模拟网络异常。

2. **Chrome DevTools 弱网模拟**：F12 → Network → Throttling 选择慢速预设，
   或在 Application/Network 条件下制造超时，前端 10 秒后必然中断并提示。

### 5.4 前后端分离联调

- Servlet 已对所有响应设置 `Access-Control-Allow-Origin: *`，并处理了 `OPTIONS` 预检。
- 前端若部署在其他端口/静态服务器（或直接双击 `index.html` 用 `file://` 打开），
  只需修改 `src/main/webapp/js/app.js` 顶部：
  ```js
  var API_BASE = 'http://localhost:8080'; // 指向后端
  ```
- 也可用任何静态服务器启动前端目录：
  ```bash
  cd src/main/webapp && python3 -m http.server 5500
  # 访问 http://localhost:5500 ，记得在 app.js 配好 API_BASE
  ```

## 6. 后续接真实数据

`ClassroomService.findFreeRooms` 目前为确定性模拟实现。接入教务系统/数据库时，
只需替换该方法的数据源查询逻辑，Servlet 的参数校验与响应协议无需改动。
