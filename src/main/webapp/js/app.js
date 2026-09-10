/**
 * 教室空闲状态查询 - 前端逻辑（原生 JavaScript，无任何框架）
 *
 * 接口约定：
 *   GET {API_BASE}/api/classroom/free?building=A&date=2026-09-14&period=1
 *   成功：   HTTP 200 + {code:0, message, count, data:[...]}
 *   参数错： HTTP 400 + {code:400, message, data:null}
 *   无结果： HTTP 200 + {code:1001, message, data:null}
 *
 * 如前后端分开部署（例如前端开在其他端口 / 用 file:// 打开），
 * 修改下面的 API_BASE 即可，例如 'http://localhost:8080'。
 */
(function () {
    'use strict';

    var API_BASE = window.API_BASE || ''; // 默认同源
    var QUERY_URL = API_BASE + '/api/classroom/free';
    var TIMEOUT_MS = 10000; // 请求超时：10 秒

    // 教学楼（与后端保持一致）
    var BUILDINGS = [
        { code: 'A', name: '教一楼' },
        { code: 'B', name: '教二楼' },
        { code: 'C', name: '教三楼' },
        { code: 'D', name: '实验楼' }
    ];

    // ---------- DOM ----------
    var form = document.getElementById('queryForm');
    var buildingSelect = document.getElementById('building');
    var dateInput = document.getElementById('date');
    var periodSelect = document.getElementById('period');
    var queryBtn = document.getElementById('queryBtn');

    var loadingBox = document.getElementById('loadingBox');
    var emptyBox = document.getElementById('emptyBox');
    var messageBox = document.getElementById('messageBox');
    var resultBox = document.getElementById('resultBox');
    var resultTbody = document.getElementById('resultTbody');
    var resultTitle = document.getElementById('resultTitle');
    var resultMeta = document.getElementById('resultMeta');

    // ---------- 初始化 ----------
    function init() {
        BUILDINGS.forEach(function (b) {
            var opt = document.createElement('option');
            opt.value = b.code;
            opt.textContent = b.name + '（' + b.code + '座）';
            buildingSelect.appendChild(opt);
        });

        // 日期默认今天，限制在 前后 1 年范围内
        var today = formatDate(new Date());
        dateInput.value = today;
        dateInput.min = shiftDate(new Date(), -365);
        dateInput.max = shiftDate(new Date(), 365);

        form.addEventListener('submit', function (e) {
            e.preventDefault();
            queryFreeRooms();
        });
    }

    // ---------- 查询主流程 ----------
    function queryFreeRooms() {
        var building = buildingSelect.value;
        var date = dateInput.value;
        var period = periodSelect.value;

        // 1) 前端参数校验（不发无效请求）
        if (!building) {
            showMessage('warning', '&#9888;', '请选择教学楼');
            buildingSelect.focus();
            return;
        }
        if (!date) {
            showMessage('warning', '&#9888;', '请选择日期');
            dateInput.focus();
            return;
        }
        if (!period) {
            showMessage('warning', '&#9888;', '请选择节次');
            periodSelect.focus();
            return;
        }

        var periodLabel = periodSelect.options[periodSelect.selectedIndex].text;
        var buildingName = buildingNameOf(building);

        var url = QUERY_URL + '?building=' + encodeURIComponent(building)
            + '&date=' + encodeURIComponent(date)
            + '&period=' + encodeURIComponent(period);

        var controller = new AbortController();
        var timer = setTimeout(function () {
            controller.abort(); // 10 秒超时，主动中断请求
        }, TIMEOUT_MS);

        setLoading(true);

        fetch(url, {
            method: 'GET',
            headers: { 'Accept': 'application/json' },
            signal: controller.signal
        }).then(function (resp) {
            // 即使 HTTP 400，body 也是统一 JSON
            return resp.json().then(function (body) {
                return { httpStatus: resp.status, body: body };
            }).catch(function () {
                // 2xx 但响应体不是 JSON（一般是网关错误页）
                var e = new Error('服务器返回了无法解析的响应（HTTP ' + resp.status + '）');
                e.httpStatus = resp.status;
                throw e;
            });
        }).then(function (res) {
            clearTimeout(timer);
            setLoading(false);

            var body = res.body || {};

            // 2) 参数错误：HTTP 400 / 业务 code 400
            if (res.httpStatus === 400 || body.code === 400) {
                showMessage('error', '&#10060;',
                    body.message || '请求参数错误',
                    '请检查教学楼、日期、节次后重新查询');
                return;
            }

            // 3) 其他后端错误（500 等）
            if (res.httpStatus >= 500 || (body.code && body.code !== 0 && body.code !== 1001)) {
                showMessage('error', '&#9888;',
                    body.message || ('服务暂时不可用（HTTP ' + res.httpStatus + '）'),
                    '请稍后重试，若持续出现请联系管理员');
                return;
            }

            // 4) 无结果：code 1001
            if (body.code === 1001) {
                showMessage('warning', '&#128704;',
                    body.message || '该条件下暂无空闲教室',
                    '查询条件：' + buildingName + ' · ' + date + ' · ' + periodLabel
                        + '，请更换条件后重试');
                return;
            }

            // 5) 成功
            var rooms = Array.isArray(body.data) ? body.data : [];
            if (rooms.length === 0) {
                showMessage('warning', '&#128704;', '该条件下暂无空闲教室');
                return;
            }
            renderResult(rooms, buildingName, date, periodLabel);
        }).catch(function (err) {
            clearTimeout(timer);
            setLoading(false);

            // 6) 超时 / 网络中断
            if (err && err.name === 'AbortError') {
                showMessage('error', '&#9203;',
                    '查询超时：请求超过 ' + (TIMEOUT_MS / 1000) + ' 秒未响应',
                    '可能是网络较慢或后端服务繁忙，请稍后重试');
                return;
            }
            showMessage('error', '&#128246;',
                '网络异常，无法连接到查询服务',
                '请检查网络连接或确认后端服务是否已启动（' + QUERY_URL + '）');
        });
    }

    // ---------- 渲染 ----------
    function renderResult(rooms, buildingName, date, periodLabel) {
        hideAllStates();
        resultTbody.innerHTML = '';

        rooms.forEach(function (r) {
            var tr = document.createElement('tr');
            tr.appendChild(td(r.roomNo));
            tr.appendChild(td(r.buildingName || buildingName));
            tr.appendChild(td(String(r.capacity)));
            tr.appendChild(td(r.type || '-'));
            resultTbody.appendChild(tr);
        });

        resultTitle.textContent = '空闲教室列表（共 ' + rooms.length + ' 间）';
        resultMeta.textContent = '查询条件：' + buildingName + ' · ' + date + ' · ' + periodLabel;
        resultBox.hidden = false;
    }

    function td(text) {
        var cell = document.createElement('td');
        cell.textContent = text; // 防 XSS，一律按文本插入
        return cell;
    }

    function showMessage(type, iconHtml, title, hint) {
        hideAllStates();
        messageBox.className = 'state-box msg-' + (type === 'warning' ? 'warning' : 'error');
        var html = '<p class="state-icon">' + iconHtml + '</p>'
            + '<p>' + escapeHtml(title) + '</p>';
        if (hint) {
            html += '<p class="msg-hint">' + escapeHtml(hint) + '</p>';
        }
        messageBox.innerHTML = html;
        messageBox.hidden = false;
    }

    function setLoading(loading) {
        queryBtn.disabled = loading;
        queryBtn.textContent = loading ? '查询中…' : '查询空闲教室';
        if (loading) {
            hideAllStates();
            loadingBox.hidden = false;
        } else {
            loadingBox.hidden = true;
        }
    }

    function hideAllStates() {
        loadingBox.hidden = true;
        emptyBox.hidden = true;
        messageBox.hidden = true;
        resultBox.hidden = true;
    }

    // ---------- 工具 ----------
    function buildingNameOf(code) {
        for (var i = 0; i < BUILDINGS.length; i++) {
            if (BUILDINGS[i].code === code) {
                return BUILDINGS[i].name;
            }
        }
        return code;
    }

    function formatDate(d) {
        var y = d.getFullYear();
        var m = ('0' + (d.getMonth() + 1)).slice(-2);
        var day = ('0' + d.getDate()).slice(-2);
        return y + '-' + m + '-' + day;
    }

    function shiftDate(d, offsetDays) {
        var x = new Date(d.getTime());
        x.setDate(x.getDate() + offsetDays);
        return formatDate(x);
    }

    function escapeHtml(s) {
        if (s == null) {
            return '';
        }
        return String(s)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    // 暴露给控制台，方便联调：window.__query({building,date,period})
    window.__query = function (p) {
        if (p && p.building) { buildingSelect.value = p.building; }
        if (p && p.date) { dateInput.value = p.date; }
        if (p && p.period) { periodSelect.value = String(p.period); }
        queryFreeRooms();
    };

    init();
})();
