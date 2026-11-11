package com.dt.checkboard.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.dt.checkboard.config.FormConfig;
import com.dt.checkboard.config.FormSpec;
import com.dt.checkboard.dao.CheckBoardDao;
import com.dt.checkboard.entity.CheckRecord;
import com.dt.checkboard.entity.UnitStat;
import com.dt.common.util.LogUtil;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 看板业务层。
 *
 * <p>所有表单共用一条判定：应检单位当天有记录即完成。异常只由结果字段的白名单判定，
 * 与点检人、班次、记录条数无关。</p>
 */
public class BoardService {

    private static final int DETAIL_LIMIT = 200;

    /** 概览缓存时长：挡住重复打开页面和连点，手动刷新会绕过。 */
    private static final long CACHE_TTL_MS = 5000L;
    private static final int CACHE_MAX = 64;
    private static final Map<String, CacheEntry> CACHE = new ConcurrentHashMap<String, CacheEntry>();
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final CheckBoardDao dao = new CheckBoardDao();

    /**
     * @param force true 表示绕过缓存，用于用户主动点刷新
     */
    public JSONObject summary(String date, boolean force) {
        FormConfig.Snapshot snap = FormConfig.get();
        if (!force) {
            CacheEntry entry = CACHE.get(date);
            if (entry != null && entry.expireAt > System.currentTimeMillis()
                    && entry.version == snap.version) {
                return entry.data;
            }
        }
        JSONObject res = buildSummary(date, snap);
        if (CACHE.size() >= CACHE_MAX) {
            CACHE.clear();
        }
        CACHE.put(date, new CacheEntry(res, System.currentTimeMillis() + CACHE_TTL_MS, snap.version));
        return res;
    }

    private JSONObject buildSummary(String date, FormConfig.Snapshot snap) {
        JSONArray forms = new JSONArray();
        int required = 0;
        int done = 0;
        int bad = 0;
        int late = 0;

        for (FormSpec f : snap.forms) {
            JSONObject item = new JSONObject();
            item.put("key", f.key);
            item.put("name", f.name);
            item.put("table", f.table);
            item.put("required", f.required());
            // 查询失败也要计入应检，否则分母变小会让完成率虚高
            required += f.required();

            int fDone = 0;
            int fBad = 0;
            int fEmpty = 0;
            int fLate = 0;
            int fRecords = 0;
            int orphan = 0;
            JSONArray missing = new JSONArray();
            JSONArray cells = new JSONArray();

            try {
                List<UnitStat> stats = dao.stat(f, date);
                Map<String, UnitStat> byUnit = new LinkedHashMap<String, UnitStat>();
                if (f.hasUnitField()) {
                    for (UnitStat s : stats) {
                        if (s.unit.isEmpty() || f.unit(s.unit) == null) {
                            orphan += s.count;
                        } else {
                            byUnit.put(s.unit, s);
                        }
                    }
                } else if (!stats.isEmpty() && stats.get(0).count > 0) {
                    // 没有 GROUP BY 时 COUNT(*) 总会返回一行，必须用 count 判断是否真的有记录
                    byUnit.put(f.units.get(0).code, stats.get(0));
                }

                for (FormSpec.Unit u : f.units) {
                    UnitStat s = byUnit.get(u.code);
                    boolean isDone = s != null;
                    JSONObject c = new JSONObject();
                    c.put("code", u.code);
                    c.put("label", u.label);
                    c.put("done", isDone);
                    c.put("count", isDone ? s.count : 0);
                    c.put("bad", isDone && s.bad > 0);
                    c.put("empty", isDone && s.empty > 0);
                    c.put("late", isDone && s.late > 0);
                    c.put("last", isDone ? shortTime(s.last) : "");
                    cells.add(c);

                    if (isDone) {
                        fDone++;
                        fRecords += s.count;
                        if (s.bad > 0) {
                            fBad++;
                        }
                        if (s.empty > 0) {
                            fEmpty++;
                        }
                        if (s.late > 0) {
                            fLate++;
                        }
                    } else {
                        missing.add(u.label);
                    }
                }

                done += fDone;
                bad += fBad;
                late += fLate;

                item.put("done", fDone);
                item.put("records", fRecords);
                item.put("bad", fBad);
                item.put("empty", fEmpty);
                item.put("late", fLate);
                item.put("orphan", orphan);
                item.put("missing", missing);
                item.put("cells", cells);
            } catch (Exception e) {
                item.put("error", message(e));
                item.put("done", 0);
                item.put("records", 0);
                item.put("bad", 0);
                item.put("late", 0);
                item.put("orphan", 0);
                item.put("missing", new JSONArray());
                item.put("cells", cells);
                LogUtil.error("表单 " + f.key + " 统计失败", e);
            }
            forms.add(item);
        }

        JSONObject total = new JSONObject();
        total.put("required", required);
        total.put("done", done);
        total.put("missing", Math.max(0, required - done));
        total.put("bad", bad);
        total.put("late", late);

        JSONObject res = new JSONObject();
        res.put("success", true);
        res.put("title", snap.title);
        res.put("date", date);
        res.put("source", snap.source);
        res.put("loadedAt", snap.loadedAt);
        res.put("generatedAt", LocalTime.now().format(TIME));
        res.put("total", total);
        res.put("forms", forms);
        res.put("configErrors", strings(snap.errors));
        res.put("reloadError", FormConfig.reloadError());
        return res;
    }

    public JSONObject detail(String key, String date, String unit) {
        JSONObject res = new JSONObject();
        FormSpec f = FormConfig.form(key);
        if (f == null) {
            res.put("success", false);
            res.put("error", "没有这张表单: " + key);
            return res;
        }
        try {
            List<CheckRecord> rows = dao.detail(f, date, unit, DETAIL_LIMIT);
            JSONArray records = new JSONArray();
            for (CheckRecord r : rows) {
                JSONObject o = new JSONObject();
                o.put("id", r.id);
                o.put("time", shortTime(r.time));
                o.put("inspector", r.inspector.isEmpty() ? r.inspectorId : r.inspector);
                o.put("unit", r.unit);
                o.put("unitLabel", r.unit.isEmpty() || !f.hasUnitField() ? "" : f.labelOf(r.unit));
                o.put("result", r.result);
                o.put("resultLabel", f.valueLabel(r.result));
                o.put("abnormal", r.abnormal);
                o.put("empty", r.emptyResult);
                o.put("late", r.late);
                o.put("createdDate", r.createdDate);

                JSONArray abnormalItems = new JSONArray();
                for (int i = 0; i < f.items.size(); i++) {
                    String v = i < r.itemValues.length ? r.itemValues[i] : "";
                    if (v.isEmpty() || f.normal.contains(v)) {
                        continue;
                    }
                    JSONObject it = new JSONObject();
                    it.put("label", f.items.get(i).label);
                    it.put("value", v);
                    String rawImage = i < r.itemImages.length ? r.itemImages[i] : "";
                    JSONArray itemPics = images(rawImage, f.imageUrl);
                    if (!itemPics.isEmpty()) {
                        it.put("images", itemPics);
                    }
                    String itemRemark = i < r.itemRemarks.length ? r.itemRemarks[i] : "";
                    if (!itemRemark.isEmpty()) {
                        it.put("remark", itemRemark);
                    }
                    abnormalItems.add(it);
                }
                o.put("abnormalItems", abnormalItems);
                o.put("itemTotal", f.items.size());

                JSONArray pics = new JSONArray();
                for (String rawImage : r.imageValues) {
                    JSONArray one = images(rawImage, f.imageUrl);
                    for (int k = 0; k < one.size(); k++) {
                        pics.add(one.getString(k));
                    }
                }
                if (!pics.isEmpty()) {
                    o.put("images", pics);
                }

                String url = f.url(r.id);
                if (!url.isEmpty()) {
                    o.put("url", url);
                }
                records.add(o);
            }
            res.put("success", true);
            res.put("key", f.key);
            res.put("name", f.name);
            res.put("date", date);
            res.put("unit", unit == null ? "" : unit);
            res.put("unitLabel", unit == null || unit.isEmpty() ? "" : f.labelOf(unit));
            res.put("limit", DETAIL_LIMIT);
            res.put("pendingItems", strings(f.pendingItems));
            res.put("records", records);
            return res;
        } catch (Exception e) {
            res.put("success", false);
            res.put("error", message(e));
            return res;
        }
    }

    /**
     * 配置自检：字段是否存在、实际值域与配置是否一致。让"配置错"在页面上可见。
     */
    public JSONObject config() {
        FormConfig.Snapshot snap = FormConfig.get();
        String from = LocalDate.now().minusDays(30).toString();
        JSONArray forms = new JSONArray();

        for (FormSpec f : snap.forms) {
            JSONObject item = new JSONObject();
            item.put("key", f.key);
            item.put("name", f.name);
            item.put("table", f.table);
            item.put("required", f.required());
            item.put("units", f.units.size());
            item.put("resultField", f.resultField);
            item.put("normal", strings(f.normal));
            item.put("pendingItems", strings(f.pendingItems));
            item.put("pendingImages", strings(f.pendingImages));

            String columnError = dao.checkColumns(f);
            item.put("columnsOk", columnError.isEmpty());
            if (!columnError.isEmpty()) {
                item.put("columnError", columnError);
            } else {
                List<String[]> unitValues = dao.distinct(f, f.hasUnitField() ? f.unitField : "", from, 30);
                item.put("unitValues", values(unitValues));
                item.put("resultValues", values(dao.distinct(f, f.hasResult() ? f.resultField : "", from, 30)));
                JSONArray unexpected = new JSONArray();
                for (String[] v : unitValues) {
                    if (f.unit(v[0]) == null) {
                        unexpected.add(v[0] + "（" + v[1] + " 条）");
                    }
                }
                item.put("unexpectedUnits", unexpected);
            }
            forms.add(item);
        }

        JSONObject res = new JSONObject();
        res.put("success", true);
        res.put("title", snap.title);
        res.put("source", snap.source);
        res.put("loadedAt", snap.loadedAt);
        res.put("errors", strings(snap.errors));
        res.put("reloadError", FormConfig.reloadError());
        res.put("forms", forms);
        return res;
    }

    private static JSONArray values(List<String[]> list) {
        JSONArray arr = new JSONArray();
        for (String[] v : list) {
            JSONObject o = new JSONObject();
            o.put("value", v[0]);
            o.put("count", v[1]);
            arr.add(o);
        }
        return arr;
    }

    static JSONArray strings(List<String> list) {
        JSONArray arr = new JSONArray();
        if (list == null) {
            return arr;
        }
        for (String s : list) {
            arr.add(s);
        }
        return arr;
    }

    static String shortTime(String t) {
        if (t == null) {
            return "";
        }
        String s = t.trim();
        if (s.length() >= 16 && s.charAt(10) == ' ') {
            return s.substring(11, 16);
        }
        if (s.length() >= 5 && s.charAt(2) == ':') {
            return s.substring(0, 5);
        }
        return s;
    }

    private static String message(Exception e) {
        return e.getMessage() == null ? e.toString() : e.getMessage();
    }

    private static final Pattern FILE_ID = Pattern.compile("\"(?:fileid|fileId|id)\"\\s*:\\s*\"?(\\d+)");
    private static final Pattern SPLIT = Pattern.compile("[,;|\\s]+");

    /**
     * 把图片字段的原始值转成可访问地址。
     *
     * <p>兼容三种存法：纯文件 id（123）、逗号分隔的 id 列表、泛微的 JSON 串；
     * 已经是 http(s) 或 / 开头的地址则原样使用。最多取 9 张。</p>
     */
    static JSONArray images(String raw, String template) {
        JSONArray arr = new JSONArray();
        if (raw == null || raw.isEmpty() || template == null || template.isEmpty()) {
            return arr;
        }
        List<String> tokens = new ArrayList<String>();
        String s = raw.trim();
        if (s.indexOf('{') >= 0) {
            Matcher m = FILE_ID.matcher(s);
            while (m.find()) {
                tokens.add(m.group(1));
            }
        } else {
            for (String t : SPLIT.split(s)) {
                if (!t.isEmpty()) {
                    tokens.add(t);
                }
            }
        }
        for (String t : tokens) {
            String url;
            if (t.startsWith("http://") || t.startsWith("https://") || t.startsWith("/")) {
                url = t;
            } else if (t.matches("\\d+")) {
                url = template.replace("{id}", t);
            } else {
                continue;
            }
            arr.add(url);
            if (arr.size() >= 9) {
                break;
            }
        }
        return arr;
    }

    private static final class CacheEntry {
        final JSONObject data;
        final long expireAt;
        final long version;

        CacheEntry(JSONObject data, long expireAt, long version) {
            this.data = data;
            this.expireAt = expireAt;
            this.version = version;
        }
    }
}
