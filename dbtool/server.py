#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
物料拉动系统 - 轻量数据库可视化工具（零安装，仅依赖 python3 标准库 + mysql 客户端）。
功能：登录口令保护、查看所有表、分页浏览数据、勾选删除、SQL 控制台。
安全：口令认证 + 可绑定本机/局域网；删除需二次确认。仅供内网管理使用。
"""
import base64
import html
import http.cookies
import json
import os
import secrets
import subprocess
import tempfile
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

import labels

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
ENV_FILE = os.path.join(BASE_DIR, "dbtool.env")


def load_env(path):
    cfg = {}
    if os.path.exists(path):
        with open(path, "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line or line.startswith("#") or "=" not in line:
                    continue
                k, v = line.split("=", 1)
                cfg[k.strip()] = v.strip()
    return cfg


CFG = load_env(ENV_FILE)
PASSWORD = CFG.get("DBTOOL_PASSWORD", "changeme")
BIND = CFG.get("DBTOOL_BIND", "127.0.0.1")
PORT = int(CFG.get("DBTOOL_PORT", "8899"))
DB_HOST = CFG.get("DB_HOST", "127.0.0.1")
DB_PORT = CFG.get("DB_PORT", "3306")
DB_NAME = CFG.get("DB_NAME", "material_pull")
DB_USER = CFG.get("DB_USER", "root")
DB_PASSWORD = CFG.get("DB_PASSWORD", "")

# 内存会话表：token -> 过期时间
SESSIONS = {}
SESSION_TTL = 8 * 3600


def _defaults_file():
    """把 DB 口令写入临时 defaults-extra-file，避免密码出现在进程命令行参数里。"""
    fd, path = tempfile.mkstemp(prefix="dbtool-my-", suffix=".cnf")
    with os.fdopen(fd, "w") as f:
        f.write("[client]\n")
        f.write(f"host={DB_HOST}\n")
        f.write(f"port={DB_PORT}\n")
        f.write(f"user={DB_USER}\n")
        f.write(f"password={DB_PASSWORD}\n")
    os.chmod(path, 0o600)
    return path


class DBError(Exception):
    pass


def run_sql(sql, xml=True, timeout=30):
    """执行 SQL。xml=True 时用 --xml 输出并解析为 (columns, rows)；否则返回原始文本。"""
    cnf = _defaults_file()
    try:
        # 用 --batch(TSV) 模式，比 --xml 健壮：特殊字符被转义为 \t \n \\，不会导致解析崩溃。
        args = ["mysql", f"--defaults-extra-file={cnf}", "--batch", DB_NAME, "-e", sql]
        proc = subprocess.run(args, capture_output=True, text=True, timeout=timeout)
        if proc.returncode != 0:
            raise DBError(proc.stderr.strip() or "SQL 执行失败")
        if not xml:
            return proc.stdout
        return _parse_tsv(proc.stdout)
    except subprocess.TimeoutExpired:
        raise DBError("SQL 执行超时")
    finally:
        try:
            os.remove(cnf)
        except OSError:
            pass


def _unescape(cell):
    """还原 mysql batch 模式的转义：\\t \\n \\r \\\\ ；\\N 表示 NULL。"""
    if cell == "\\N":
        return None
    out = []
    i = 0
    while i < len(cell):
        c = cell[i]
        if c == "\\" and i + 1 < len(cell):
            nxt = cell[i + 1]
            out.append({"t": "\t", "n": "\n", "r": "\r", "\\": "\\", "0": "\0"}.get(nxt, nxt))
            i += 2
        else:
            out.append(c)
            i += 1
    return "".join(out)


def _parse_tsv(text):
    """解析 mysql --batch(TSV) 输出为 (columns, rows[dict])。首行是列名。"""
    if not text:
        return [], []
    lines = text.split("\n")
    while lines and lines[-1] == "":
        lines.pop()
    if not lines:
        return [], []
    columns = [_unescape(c) for c in lines[0].split("\t")]
    rows = []
    for line in lines[1:]:
        parts = line.split("\t")
        if len(parts) < len(columns):
            parts += [""] * (len(columns) - len(parts))
        row = {columns[i]: _unescape(parts[i]) for i in range(len(columns))}
        rows.append(row)
    return columns, rows


def esc(v):
    """转义字符串值用于 SQL 字面量。"""
    if v is None:
        return "NULL"
    s = str(v)
    s = s.replace("\\", "\\\\").replace("'", "\\'")
    return "'" + s + "'"


LOGIN_HTML = """<!doctype html><html lang="zh"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>数据库工具 · 登录</title>
<style>
*{box-sizing:border-box}
body{margin:0;font-family:'Microsoft YaHei','PingFang SC',Arial,sans-serif;
background:linear-gradient(135deg,#1e293b 0%,#0f172a 50%,#1e3a5f 100%);
display:flex;align-items:center;justify-content:center;min-height:100vh}
.box{background:rgba(255,255,255,.98);padding:40px 40px 32px;border-radius:18px;
box-shadow:0 20px 60px rgba(0,0,0,.4);width:360px;backdrop-filter:blur(10px)}
.logo{width:52px;height:52px;border-radius:14px;background:linear-gradient(135deg,#3b82f6,#2563eb);
display:flex;align-items:center;justify-content:center;font-size:26px;margin-bottom:18px}
h1{font-size:20px;margin:0 0 4px;color:#0f172a;font-weight:700}
.sub{color:#94a3b8;font-size:13px;margin-bottom:24px}
label{display:block;font-size:13px;color:#475569;margin-bottom:6px;font-weight:600}
input{width:100%;padding:12px 14px;border:1.5px solid #e2e8f0;border-radius:10px;font-size:14px;
margin-bottom:18px;transition:.2s;outline:none}
input:focus{border-color:#3b82f6;box-shadow:0 0 0 3px rgba(59,130,246,.12)}
button{width:100%;padding:12px;background:linear-gradient(135deg,#3b82f6,#2563eb);color:#fff;
border:0;border-radius:10px;font-size:15px;font-weight:600;cursor:pointer;transition:.2s}
button:hover{transform:translateY(-1px);box-shadow:0 8px 20px rgba(37,99,235,.35)}
.err{color:#dc2626;font-size:13px;margin-bottom:14px;min-height:18px;font-weight:600}
</style></head><body>
<form class="box" method="POST" action="/login">
<div class="logo">🗄️</div>
<h1>数据库管理工具</h1>
<div class="sub">物料拉动系统 · Material Pull</div>
<div class="err">__ERR__</div>
<label>访问口令</label>
<input type="password" name="password" placeholder="请输入访问口令" autofocus>
<button type="submit">登 录</button>
</form></body></html>"""


def page_shell(body, active_nav=""):
    def nav(href, label, key):
        cls = "navlink active" if active_nav == key else "navlink"
        return f'<a class="{cls}" href="{href}">{label}</a>'
    return """<!doctype html><html lang="zh"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>数据库工具</title>
<style>
*{box-sizing:border-box}
body{margin:0;font-family:'Microsoft YaHei','PingFang SC',Arial,sans-serif;background:#f8fafc;color:#1e293b}
.top{background:#fff;border-bottom:1px solid #e2e8f0;padding:0 22px;height:56px;display:flex;
align-items:center;gap:6px;box-shadow:0 1px 3px rgba(0,0,0,.04);position:sticky;top:0;z-index:10}
.brand{display:flex;align-items:center;gap:10px;font-weight:700;font-size:15px;color:#0f172a;margin-right:20px}
.brand .ico{width:32px;height:32px;border-radius:9px;background:linear-gradient(135deg,#3b82f6,#2563eb);
display:flex;align-items:center;justify-content:center;font-size:17px}
.navlink{color:#64748b;text-decoration:none;font-size:14px;padding:8px 14px;border-radius:8px;transition:.15s}
.navlink:hover{background:#f1f5f9;color:#0f172a}
.navlink.active{background:#eff6ff;color:#2563eb;font-weight:600}
.logout{margin-left:auto;color:#94a3b8;text-decoration:none;font-size:13px;padding:6px 12px;border-radius:8px}
.logout:hover{background:#fef2f2;color:#dc2626}
.wrap{display:flex;height:calc(100vh - 56px)}
.side{width:250px;background:#fff;border-right:1px solid #e2e8f0;overflow:auto;padding:10px 0}
.side .sh{padding:8px 18px;font-size:11px;color:#94a3b8;font-weight:700;letter-spacing:.5px;text-transform:uppercase}
.side a{display:flex;align-items:center;gap:8px;padding:9px 18px;color:#475569;text-decoration:none;
font-size:13px;border-left:3px solid transparent;transition:.12s}
.side a:hover{background:#f8fafc;color:#0f172a}
.side a.active{background:#eff6ff;color:#2563eb;font-weight:600;border-left-color:#2563eb}
.side a .dot{width:6px;height:6px;border-radius:50%;background:#cbd5e1;flex-shrink:0;margin-top:5px}
.side a.active .dot{background:#2563eb}
.side a .nm{display:flex;flex-direction:column;line-height:1.35;overflow:hidden}
.side a .cn{font-size:12px;color:#94a3b8}
.side a.active .cn{color:#60a5fa}
th .cn{display:block;font-weight:400;color:#94a3b8;font-size:11px;text-transform:none;letter-spacing:0;margin-top:2px}
.main{flex:1;padding:24px 28px;overflow:auto}
.card{background:#fff;border:1px solid #e2e8f0;border-radius:14px;padding:20px 22px;box-shadow:0 1px 3px rgba(0,0,0,.03)}
h2{margin:0;font-size:19px;font-weight:700;color:#0f172a}
.tablewrap{overflow:auto;border:1px solid #e2e8f0;border-radius:10px;margin-top:6px}
table{border-collapse:collapse;width:100%;font-size:13px}
th,td{padding:9px 12px;text-align:left;max-width:300px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;border-bottom:1px solid #f1f5f9}
th{background:#f8fafc;position:sticky;top:0;font-weight:700;color:#475569;font-size:12px;text-transform:uppercase;letter-spacing:.3px}
tbody tr{transition:.1s}
tbody tr:hover td{background:#f8fbff}
.btn{padding:9px 16px;border:0;border-radius:9px;font-size:13px;font-weight:600;cursor:pointer;transition:.15s}
.btn:hover{transform:translateY(-1px)}
.btn-d{background:#dc2626;color:#fff}.btn-d:hover{background:#b91c1c}
.btn-p{background:linear-gradient(135deg,#3b82f6,#2563eb);color:#fff}
.btn-g{background:#f1f5f9;color:#475569}
.bar{margin-bottom:16px;display:flex;gap:12px;align-items:center;flex-wrap:wrap}
.tag{background:#eff6ff;color:#2563eb;padding:3px 10px;border-radius:20px;font-size:12px;font-weight:600}
.tag.gray{background:#f1f5f9;color:#64748b}
textarea{width:100%;height:140px;font-family:'Cascadia Code',Consolas,monospace;font-size:13px;
padding:14px;border:1.5px solid #e2e8f0;border-radius:10px;outline:none;transition:.2s;resize:vertical}
textarea:focus{border-color:#3b82f6;box-shadow:0 0 0 3px rgba(59,130,246,.1)}
.muted{color:#94a3b8;font-size:12px}
.pg{margin-top:16px;display:flex;flex-wrap:wrap;gap:5px}
.pg a{padding:5px 11px;border:1px solid #e2e8f0;border-radius:8px;text-decoration:none;color:#475569;font-size:13px;transition:.12s}
.pg a:hover{border-color:#3b82f6;color:#2563eb}
.pg a.cur{background:#2563eb;color:#fff;border-color:#2563eb}
.msg{padding:12px 16px;border-radius:10px;margin-bottom:16px;font-size:13px;font-weight:500}
.msg.ok{background:#f0fdf4;color:#166534;border:1px solid #bbf7d0}
.msg.err{background:#fef2f2;color:#991b1b;border:1px solid #fecaca}
input[type=checkbox]{width:16px;height:16px;cursor:pointer;accent-color:#2563eb;vertical-align:middle}
.editlink{text-decoration:none;color:#2563eb;font-size:15px;padding:0 3px}
.editlink:hover{color:#1d4ed8}
.form-row{display:flex;gap:14px;align-items:flex-start;padding:10px 0;border-bottom:1px solid #f1f5f9}
.form-row .lab{width:220px;flex-shrink:0;padding-top:8px}
.form-row .lab .en{font-family:monospace;font-size:13px;color:#0f172a;font-weight:600}
.form-row .lab .cn{display:block;font-size:12px;color:#94a3b8;margin-top:2px}
.form-row .fld{flex:1}
.form-row input[type=text],.form-row textarea{width:100%;padding:9px 12px;border:1.5px solid #e2e8f0;border-radius:8px;font-size:13px;outline:none;transition:.15s;font-family:inherit}
.form-row input[type=text]:focus,.form-row textarea:focus{border-color:#3b82f6;box-shadow:0 0 0 3px rgba(59,130,246,.1)}
.form-row .hint{font-size:11px;color:#cbd5e1;margin-top:3px}
.nullbox{font-size:12px;color:#64748b;margin-top:5px;display:flex;align-items:center;gap:5px;cursor:pointer}
</style></head><body>
<div class="top">
<div class="brand"><span class="ico">🗄️</span>数据库工具</div>
""" + nav("/tables", "📋 表浏览", "tables") + nav("/query", "⌨️ SQL 控制台", "query") + """
<a class="logout" href="/logout">退出登录</a></div>
""" + body + "</body></html>"


def list_tables():
    _, rows = run_sql("SHOW TABLES")
    key = f"Tables_in_{DB_NAME}"
    out = []
    for r in rows:
        out.append(r.get(key) or list(r.values())[0])
    return out


def table_pk(table):
    """取表主键列名；无主键返回 None。"""
    _, rows = run_sql(f"SHOW KEYS FROM `{table}` WHERE Key_name='PRIMARY'")
    if rows:
        return rows[0].get("Column_name")
    return None


def side_nav(active):
    tables = list_tables()
    links = [f'<div class="sh">数据表 ({len(tables)})</div>']
    for t in tables:
        cls = "active" if t == active else ""
        cn = labels.table_cn(t)
        cn_html = f'<span class="cn">{html.escape(cn)}</span>' if cn else ""
        links.append(f'<a class="{cls}" href="/table?name={html.escape(t)}" title="{html.escape(t)}"><span class="dot"></span><span class="nm">{html.escape(t)}{cn_html}</span></a>')
    return '<div class="side">' + "".join(links) + "</div>"


def render_tables_home():
    tables = list_tables()
    body = side_nav("")
    inner = f"""<div class="main"><div class="card">
<h2>数据库总览</h2>
<p class="muted" style="margin-top:8px">当前库 <b>{html.escape(DB_NAME)}</b> 共有 <b>{len(tables)}</b> 张表。从左侧选择一张表查看数据，或前往顶部「SQL 控制台」执行查询。</p>
</div></div>"""
    return page_shell('<div class="wrap">' + body + inner + "</div>", "tables")


def render_table(table, page, msg=""):
    if table not in list_tables():
        return page_shell('<div class="main"><div class="card"><div class="msg err">表不存在</div></div></div>', "tables")
    page = max(1, int(page or 1))
    size = 50
    offset = (page - 1) * size
    _, cnt = run_sql(f"SELECT COUNT(*) AS c FROM `{table}`")
    total = int(cnt[0]["c"]) if cnt else 0
    pk = table_pk(table)
    cols, rows = run_sql(f"SELECT * FROM `{table}` ORDER BY {('`'+pk+'` DESC') if pk else '1'} LIMIT {size} OFFSET {offset}")
    pages = max(1, (total + size - 1) // size)

    msg_html = f'<div class="msg ok">{html.escape(msg)}</div>' if msg else ""
    def th(c):
        cn = labels.column_cn(c)
        cn_html = f'<span class="cn">{html.escape(cn)}</span>' if cn else ""
        return f"<th>{html.escape(c)}{cn_html}</th>"
    head = "".join(th(c) for c in cols)
    check_head = "<th style='width:60px'>操作</th>" if pk else ""
    body_rows = []
    for r in rows:
        cells = ""
        if pk:
            pv = html.escape(str(r.get(pk, "")))
            edit_url = f"/edit?name={html.escape(table)}&pk={html.escape(pk)}&id={pv}&page={page}"
            cells += f'<td style="white-space:nowrap"><input type="checkbox" name="ids" value="{pv}"> <a class="editlink" href="{edit_url}" title="编辑此行">✎</a></td>'
        for c in cols:
            v = r.get(c)
            v = "" if v is None else html.escape(str(v))
            cells += f"<td title=\"{v}\">{v}</td>"
        body_rows.append(f"<tr>{cells}</tr>")

    # 分页条
    pg = ""
    if pages > 1:
        for p in range(max(1, page - 3), min(pages, page + 3) + 1):
            cur = "cur" if p == page else ""
            pg += f'<a class="{cur}" href="/table?name={html.escape(table)}&page={p}">{p}</a>'

    del_form = ""
    if pk:
        del_form = f"""
<form method="POST" action="/delete" onsubmit="return confirmDel(this)">
<input type="hidden" name="table" value="{html.escape(table)}">
<input type="hidden" name="pk" value="{html.escape(pk)}">
<input type="hidden" name="page" value="{page}">
<div class="bar">
<a class="btn btn-p" href="/new?name={html.escape(table)}&page={page}">＋ 新增行</a>
<button type="submit" class="btn btn-d">🗑 删除选中行</button>
<label class="muted" style="display:flex;align-items:center;gap:6px;cursor:pointer"><input type="checkbox" onclick="toggleAll(this)"> 全选本页</label>
<span class="muted">点 ✎ 编辑单行；勾选后点删除；主键：{html.escape(pk)}</span>
</div>
<div class="tablewrap"><table><thead><tr>{check_head}{head}</tr></thead><tbody>{''.join(body_rows)}</tbody></table></div>
</form>"""
    else:
        del_form = f'<div class="bar"><span class="muted">该表无主键，仅支持查看，不能在此删除</span></div><div class="tablewrap"><table><thead><tr>{head}</tr></thead><tbody>{"".join(body_rows)}</tbody></table></div>'

    inner = f"""<div class="main"><div class="card">
<div class="bar"><h2>{html.escape(table)}</h2>{f'<span class="tag">{html.escape(labels.table_cn(table))}</span>' if labels.table_cn(table) else ''}<span class="tag gray">共 {total} 行</span><span class="tag gray">第 {page}/{pages} 页</span></div>
{msg_html}
{del_form}
<div class="pg">{pg}</div>
</div></div>
<script>
function confirmDel(f){{
  var n=f.querySelectorAll('input[name=ids]:checked').length;
  if(n===0){{alert('请先勾选要删除的行');return false;}}
  return confirm('确认删除选中的 '+n+' 行？此操作不可撤销！');
}}
function toggleAll(cb){{
  document.querySelectorAll('input[name=ids]').forEach(function(x){{x.checked=cb.checked;}});
}}
</script>"""
    return page_shell('<div class="wrap">' + side_nav(table) + inner + "</div>", "tables")


def do_delete(table, pk, ids):
    if not ids:
        return "未选择任何行"
    if table not in list_tables():
        raise DBError("表不存在")
    real_pk = table_pk(table)
    if not real_pk or real_pk != pk:
        raise DBError("主键校验失败，拒绝删除")
    values = ",".join(esc(i) for i in ids)
    run_sql(f"DELETE FROM `{table}` WHERE `{real_pk}` IN ({values})", xml=False)
    return f"已删除 {len(ids)} 行"


def column_meta(table):
    """返回列元信息 [{name, type, nullable, key, default, extra, comment}]。"""
    _, rows = run_sql(f"SHOW FULL COLUMNS FROM `{table}`")
    metas = []
    for r in rows:
        metas.append({
            "name": r.get("Field"),
            "type": r.get("Type") or "",
            "nullable": (r.get("Null") == "YES"),
            "key": r.get("Key") or "",
            "default": r.get("Default"),
            "extra": r.get("Extra") or "",
        })
    return metas


def render_form(table, mode, row=None, page=1, msg="", is_err=False):
    """mode: 'new' 新增 / 'edit' 编辑。row 为编辑时的现有数据。"""
    if table not in list_tables():
        return page_shell('<div class="main"><div class="card"><div class="msg err">表不存在</div></div></div>', "tables")
    metas = column_meta(table)
    pk = table_pk(table)
    row = row or {}
    is_edit = (mode == "edit")
    title = ("编辑行" if is_edit else "新增行")
    rows_html = []
    for m in metas:
        name = m["name"]
        cn = labels.column_cn(name)
        is_auto = "auto_increment" in m["extra"]
        # 自增主键：新增时不填；编辑时只读展示
        cur = row.get(name)
        cur_val = "" if cur is None else html.escape(str(cur))
        readonly = ""
        hint = html.escape(m["type"])
        if is_auto:
            hint += " · 自增，留空自动生成"
            if not is_edit:
                readonly = "disabled"
        if is_edit and name == pk:
            readonly = "readonly"
            hint += " · 主键，不可改"
        # 长文本用 textarea
        big = any(k in m["type"].lower() for k in ("text", "json", "blob"))
        if big:
            field = f'<textarea name="f_{html.escape(name)}" rows="3" {readonly}>{cur_val}</textarea>'
        else:
            field = f'<input type="text" name="f_{html.escape(name)}" value="{cur_val}" {readonly}>'
        # NULL 复选（可空且非主键）
        nullbox = ""
        if m["nullable"] and name != pk:
            checked = "checked" if (is_edit and cur is None) else ""
            nullbox = f'<label class="nullbox"><input type="checkbox" name="null_{html.escape(name)}" {checked}> 设为 NULL（空值）</label>'
        cn_html = f'<span class="cn">{html.escape(cn)}</span>' if cn else ""
        rows_html.append(f"""<div class="form-row">
<div class="lab"><span class="en">{html.escape(name)}</span>{cn_html}</div>
<div class="fld">{field}<div class="hint">{hint}</div>{nullbox}</div>
</div>""")
    msg_html = ""
    if msg:
        msg_html = f'<div class="msg {"err" if is_err else "ok"}">{html.escape(msg)}</div>'
    hidden_id = ""
    if is_edit:
        hidden_id = f'<input type="hidden" name="orig_id" value="{html.escape(str(row.get(pk, "")))}">'
    action = "/edit" if is_edit else "/new"
    cn_tag = f'<span class="tag">{html.escape(labels.table_cn(table))}</span>' if labels.table_cn(table) else ""
    inner = f"""<div class="main"><div class="card">
<div class="bar"><h2>{title} · {html.escape(table)}</h2>{cn_tag}</div>
{msg_html}
<form method="POST" action="{action}">
<input type="hidden" name="table" value="{html.escape(table)}">
<input type="hidden" name="pk" value="{html.escape(pk or '')}">
<input type="hidden" name="page" value="{page}">
{hidden_id}
{''.join(rows_html)}
<div class="bar" style="margin-top:18px">
<button type="submit" class="btn btn-p">{'保存修改' if is_edit else '确认新增'}</button>
<a class="btn btn-g" href="/table?name={html.escape(table)}&page={page}">取消返回</a>
</div>
</form>
</div></div>"""
    return page_shell('<div class="wrap">' + side_nav(table) + inner + "</div>", "tables")


def _collect_fields(table, form):
    """从表单解析各列的值：返回 {列名: SQL字面量}。NULL 优先，其余按文本处理。"""
    metas = column_meta(table)
    result = {}
    for m in metas:
        name = m["name"]
        if form.get(f"null_{name}", [""])[0]:
            result[name] = "NULL"
            continue
        key = f"f_{name}"
        if key not in form:
            continue  # disabled 字段（如自增主键新增时）不出现在表单里，跳过
        val = form.get(key, [""])[0]
        if "auto_increment" in m["extra"] and val == "":
            continue
        result[name] = esc(val)
    return result


def do_insert(table, form):
    if table not in list_tables():
        raise DBError("表不存在")
    fields = _collect_fields(table, form)
    if not fields:
        raise DBError("没有可插入的字段")
    cols = ",".join(f"`{k}`" for k in fields)
    vals = ",".join(fields.values())
    run_sql(f"INSERT INTO `{table}` ({cols}) VALUES ({vals})", xml=False)
    return "已新增 1 行"


def do_update(table, pk, orig_id, form):
    if table not in list_tables():
        raise DBError("表不存在")
    real_pk = table_pk(table)
    if not real_pk or real_pk != pk:
        raise DBError("主键校验失败，拒绝更新")
    fields = _collect_fields(table, form)
    fields.pop(real_pk, None)  # 不更新主键本身
    if not fields:
        raise DBError("没有可更新的字段")
    sets = ",".join(f"`{k}`={v}" for k, v in fields.items())
    run_sql(f"UPDATE `{table}` SET {sets} WHERE `{real_pk}`={esc(orig_id)}", xml=False)
    return "已保存修改"


def load_one_row(table, pk, pk_value):
    _, rows = run_sql(f"SELECT * FROM `{table}` WHERE `{pk}`={esc(pk_value)} LIMIT 1")
    return rows[0] if rows else None


def render_query(sql="", msg="", is_err=False, result_html=""):
    msg_html = ""
    if msg:
        cls = "err" if is_err else "ok"
        msg_html = f'<div class="msg {cls}">{html.escape(msg)}</div>'
    inner = f"""<div class="main"><div class="card">
<h2>SQL 控制台</h2>
<p class="muted" style="margin:8px 0 14px">支持任意 SQL。SELECT/SHOW 显示结果表；其他语句(UPDATE/DELETE 等)显示执行结果。危险操作请谨慎。</p>
<form method="POST" action="/query">
<textarea name="sql" placeholder="SELECT * FROM t_replenishment_task WHERE status='COMPLETED' LIMIT 20">{html.escape(sql)}</textarea>
<div class="bar" style="margin-top:12px"><button class="btn btn-p" type="submit">▶ 执行</button></div>
</form>
{msg_html}
{result_html}
</div></div>"""
    return page_shell('<div class="wrap">' + side_nav("") + inner + "</div>", "query")


def result_table_html(cols, rows):
    if not cols:
        return '<p class="muted">无结果集</p>'
    head = "".join(f"<th>{html.escape(c)}</th>" for c in cols)
    body = ""
    for r in rows:
        body += "<tr>" + "".join(f"<td>{'' if r.get(c) is None else html.escape(str(r.get(c)))}</td>" for c in cols) + "</tr>"
    return f'<div class="tablewrap"><table><thead><tr>{head}</tr></thead><tbody>{body}</tbody></table></div><p class="muted" style="margin-top:10px">共 {len(rows)} 行</p>'


def new_session():
    token = secrets.token_urlsafe(32)
    SESSIONS[token] = time.time() + SESSION_TTL
    return token


def valid_session(token):
    exp = SESSIONS.get(token)
    if not exp:
        return False
    if time.time() > exp:
        SESSIONS.pop(token, None)
        return False
    return True


class Handler(BaseHTTPRequestHandler):
    server_version = "dbtool/1.0"

    def log_message(self, fmt, *args):
        pass  # 静音默认日志

    def _cookie_token(self):
        c = self.headers.get("Cookie")
        if not c:
            return None
        ck = http.cookies.SimpleCookie(c)
        m = ck.get("dbtool")
        return m.value if m else None

    def _authed(self):
        return valid_session(self._cookie_token())

    def _send(self, code, body, ctype="text/html; charset=utf-8", cookie=None):
        data = body.encode("utf-8") if isinstance(body, str) else body
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(data)))
        if cookie:
            self.send_header("Set-Cookie", cookie)
        self.send_header("X-Frame-Options", "DENY")
        self.end_headers()
        self.wfile.write(data)

    def _redirect(self, loc, cookie=None):
        self.send_response(302)
        self.send_header("Location", loc)
        if cookie:
            self.send_header("Set-Cookie", cookie)
        self.end_headers()

    def _read_form(self):
        length = int(self.headers.get("Content-Length", 0))
        raw = self.rfile.read(length).decode("utf-8") if length else ""
        return parse_qs(raw, keep_blank_values=True)

    def do_GET(self):
        u = urlparse(self.path)
        path = u.path
        if path == "/login":
            self._send(200, LOGIN_HTML.replace("__ERR__", ""))
            return
        if path == "/logout":
            tok = self._cookie_token()
            SESSIONS.pop(tok, None)
            self._redirect("/login", cookie="dbtool=; Path=/; Max-Age=0")
            return
        if not self._authed():
            self._redirect("/login")
            return
        q = parse_qs(u.query)
        try:
            if path in ("/", "/tables"):
                self._send(200, render_tables_home())
            elif path == "/table":
                name = q.get("name", [""])[0]
                page = q.get("page", ["1"])[0]
                self._send(200, render_table(name, page))
            elif path == "/query":
                self._send(200, render_query())
            elif path == "/new":
                name = q.get("name", [""])[0]
                page = q.get("page", ["1"])[0]
                self._send(200, render_form(name, "new", page=page))
            elif path == "/edit":
                name = q.get("name", [""])[0]
                pk = q.get("pk", [""])[0]
                rid = q.get("id", [""])[0]
                page = q.get("page", ["1"])[0]
                row = load_one_row(name, pk, rid)
                if row is None:
                    self._send(200, render_table(name, page, msg="要编辑的行不存在"))
                else:
                    self._send(200, render_form(name, "edit", row=row, page=page))
            else:
                self._send(404, page_shell('<div class="main">404</div>'))
        except Exception as e:
            try:
                self._send(200, page_shell(f'<div class="main"><div class="msg err">出错了：{html.escape(str(e))}</div></div>'))
            except Exception:
                pass

    def do_POST(self):
        path = urlparse(self.path).path
        form = self._read_form()
        if path == "/login":
            pw = form.get("password", [""])[0]
            if secrets.compare_digest(pw, PASSWORD):
                token = new_session()
                self._redirect("/tables", cookie=f"dbtool={token}; Path=/; HttpOnly; SameSite=Strict; Max-Age={SESSION_TTL}")
            else:
                self._send(200, LOGIN_HTML.replace("__ERR__", "口令错误"))
            return
        if not self._authed():
            self._redirect("/login")
            return
        try:
            if path == "/delete":
                table = form.get("table", [""])[0]
                pk = form.get("pk", [""])[0]
                ids = form.get("ids", [])
                page = form.get("page", ["1"])[0]
                msg = do_delete(table, pk, ids)
                self._send(200, render_table(table, page, msg=msg))
            elif path == "/new":
                table = form.get("table", [""])[0]
                page = form.get("page", ["1"])[0]
                try:
                    msg = do_insert(table, form)
                    self._send(200, render_table(table, page, msg=msg))
                except DBError as e:
                    self._send(200, render_form(table, "new", row={k[2:]: form[k][0] for k in form if k.startswith("f_")}, page=page, msg=str(e), is_err=True))
            elif path == "/edit":
                table = form.get("table", [""])[0]
                pk = form.get("pk", [""])[0]
                orig_id = form.get("orig_id", [""])[0]
                page = form.get("page", ["1"])[0]
                try:
                    msg = do_update(table, pk, orig_id, form)
                    self._send(200, render_table(table, page, msg=msg))
                except DBError as e:
                    row = load_one_row(table, pk, orig_id) or {}
                    self._send(200, render_form(table, "edit", row=row, page=page, msg=str(e), is_err=True))
            elif path == "/query":
                sql = form.get("sql", [""])[0].strip()
                if not sql:
                    self._send(200, render_query(sql, "请输入 SQL", True))
                    return
                low = sql.lstrip().lower()
                if low.startswith("select") or low.startswith("show") or low.startswith("desc") or low.startswith("explain"):
                    cols, rows = run_sql(sql)
                    self._send(200, render_query(sql, "查询成功", False, result_table_html(cols, rows)))
                else:
                    run_sql(sql, xml=False)
                    self._send(200, render_query(sql, "语句已执行", False))
            else:
                self._send(404, page_shell('<div class="main">404</div>'))
        except Exception as e:
            try:
                self._send(200, render_query(form.get("sql", [""])[0], str(e), True) if path == "/query"
                           else page_shell(f'<div class="main"><div class="msg err">出错了：{html.escape(str(e))}</div></div>'))
            except Exception:
                pass


def main():
    if PASSWORD in ("changeme", "", "Dbtool@2026Change"):
        print("[警告] 你还在用默认口令，请尽快修改 dbtool.env 里的 DBTOOL_PASSWORD")
    print(f"[dbtool] 启动：http://{BIND}:{PORT}  库={DB_NAME}  绑定={BIND}")
    if BIND == "0.0.0.0":
        print("[dbtool] 注意：已监听局域网，同网段可访问，务必设强口令。")
    server = ThreadingHTTPServer((BIND, PORT), Handler)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\n[dbtool] 已停止")


if __name__ == "__main__":
    main()
