package com.dt.checkboard.config;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.dt.common.util.LogUtil;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 看板配置加载器。
 *
 * <p>查找顺序：-Dcheckboard.conf → {weaver.root}/classbean/com/dt/checkboard/checkboard.json
 * → classpath:/com/dt/checkboard/checkboard.json。文件来源支持热加载：每 5 秒检查一次 mtime，
 * 新配置校验不通过时保留上一版，并把错误通过 /config 和页面横幅暴露出来。</p>
 */
public final class FormConfig {

    public static final String FILE_NAME = "checkboard.json";

    private static final long CHECK_INTERVAL_MS = 5000L;

    private static volatile Snapshot current;
    private static volatile long lastCheckAt;
    private static volatile long failedMtime;
    private static volatile String reloadError = "";
    private static final AtomicLong VERSION = new AtomicLong();

    static {
        current = load();
        LogUtil.info("看板配置: " + current.source + "，表单 " + current.forms.size() + " 张"
                + (current.errors.isEmpty() ? "" : "，配置错误 " + current.errors.size() + " 条"));
    }

    private FormConfig() {
    }

    public static Snapshot get() {
        long now = System.currentTimeMillis();
        if (now - lastCheckAt >= CHECK_INTERVAL_MS) {
            synchronized (FormConfig.class) {
                if (now - lastCheckAt >= CHECK_INTERVAL_MS) {
                    lastCheckAt = now;
                    reloadIfChanged();
                }
            }
        }
        return current;
    }

    public static List<FormSpec> forms() {
        return get().forms;
    }

    public static FormSpec form(String key) {
        if (key == null) {
            return null;
        }
        for (FormSpec f : forms()) {
            if (f.key.equals(key)) {
                return f;
            }
        }
        return null;
    }

    public static String reloadError() {
        return reloadError;
    }

    private static void reloadIfChanged() {
        File file = resolveFile();
        if (file == null) {
            return;
        }
        long mtime = file.lastModified();
        if (mtime == current.mtime || mtime == failedMtime) {
            return;
        }
        Snapshot next = load();
        if (next.errors.isEmpty() && !next.forms.isEmpty()) {
            current = next;
            failedMtime = 0L;
            reloadError = "";
            LogUtil.info("看板配置已热加载: " + next.source + "，表单 " + next.forms.size() + " 张");
        } else {
            failedMtime = mtime;
            reloadError = "配置改动未生效，仍使用上一版：" + join(next.errors);
            LogUtil.error(reloadError, null);
        }
    }

    private static File resolveFile() {
        String custom = System.getProperty("checkboard.conf");
        if (custom != null && !custom.trim().isEmpty()) {
            File f = new File(custom.trim());
            if (f.isFile()) {
                return f;
            }
        }
        String root = System.getProperty("weaver.root", "/data/weaver/ecology");
        File f = new File(root, "classbean/com/dt/checkboard/" + FILE_NAME);
        return f.isFile() ? f : null;
    }

    private static Snapshot load() {
        Snapshot snap = new Snapshot();
        snap.version = VERSION.incrementAndGet();
        File file = resolveFile();
        String text;
        if (file != null) {
            snap.source = "文件 " + file.getAbsolutePath();
            snap.mtime = file.lastModified();
            try {
                text = readFile(file);
            } catch (Exception e) {
                snap.errors.add("读取配置失败: " + e.getMessage());
                return snap;
            }
        } else {
            text = readClasspath();
            snap.source = text == null ? "未找到 " + FILE_NAME : "classpath:" + FILE_NAME;
        }
        if (text == null) {
            snap.errors.add("没有找到 " + FILE_NAME
                    + "，请放在 classbean/com/dt/checkboard/ 下，或用 -Dcheckboard.conf 指定绝对路径");
            return snap;
        }
        try {
            parse(text, snap);
        } catch (Exception e) {
            snap.errors.add("解析配置失败: " + e.getMessage());
        }
        snap.loadedAt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        return snap;
    }

    private static void parse(String text, Snapshot snap) {
        JSONObject root;
        try {
            root = JSON.parseObject(text);
        } catch (Exception e) {
            snap.errors.add("JSON 语法错误: " + e.getMessage());
            return;
        }
        if (root == null) {
            snap.errors.add("配置内容为空");
            return;
        }
        String title = trim(root.getString("title"));
        if (!title.isEmpty()) {
            snap.title = title;
        }
        JSONArray arr = root.getJSONArray("forms");
        if (arr == null || arr.isEmpty()) {
            snap.errors.add("forms 为空，看板不会有任何表单");
            return;
        }
        List<FormSpec> list = new ArrayList<FormSpec>();
        for (int i = 0; i < arr.size(); i++) {
            FormSpec f;
            try {
                f = parseForm(arr.getJSONObject(i), i, snap);
            } catch (Exception e) {
                snap.errors.add("forms[" + i + "] 结构不对: " + e.getMessage());
                continue;
            }
            if (f == null) {
                continue;
            }
            if (find(list, f.key) != null) {
                snap.errors.add("表单 key 重复: " + f.key);
                continue;
            }
            list.add(f);
        }
        snap.forms = list;
    }

    private static FormSpec parseForm(JSONObject o, int idx, Snapshot snap) {
        if (o == null) {
            snap.errors.add("forms[" + idx + "] 不是对象");
            return null;
        }
        FormSpec f = new FormSpec();
        f.key = trim(o.getString("key"));
        f.name = trim(o.getString("name"));
        f.table = trim(o.getString("table"));
        f.dateField = trim(o.getString("dateField"));
        f.timeField = trim(o.getString("timeField"));
        f.inspectorField = trim(o.getString("inspectorField"));
        f.resultField = trim(o.getString("resultField"));
        f.unitField = trim(o.getString("unitField"));
        f.detailUrl = trim(o.getString("detailUrl"));

        String at = "forms[" + idx + "]" + (f.key.isEmpty() ? "" : "(" + f.key + ")");
        List<String> errs = new ArrayList<String>();

        if (f.key.isEmpty()) {
            errs.add("缺 key");
        }
        if (f.name.isEmpty()) {
            errs.add("缺 name");
        }
        if (!ident(f.table)) {
            errs.add("table 必填，且只能包含字母、数字、下划线");
        }
        if (!ident(f.dateField)) {
            errs.add("dateField 必填，且只能包含字母、数字、下划线");
        }
        if (!ident(f.timeField)) {
            errs.add("timeField 必填，且只能包含字母、数字、下划线");
        }
        if (!ident(f.inspectorField)) {
            errs.add("inspectorField 必填，且只能包含字母、数字、下划线");
        }
        if (!f.resultField.isEmpty() && !ident(f.resultField)) {
            errs.add("resultField 只能包含字母、数字、下划线");
        }
        if (!f.unitField.isEmpty() && !ident(f.unitField)) {
            errs.add("unitField 只能包含字母、数字、下划线");
        }

        JSONArray ua = o.getJSONArray("units");
        if (ua == null || ua.isEmpty()) {
            errs.add("units 不能为空，单台设备也写一个元素");
        } else {
            for (int i = 0; i < ua.size(); i++) {
                JSONObject uo = ua.getJSONObject(i);
                if (uo == null) {
                    errs.add("units[" + i + "] 不是对象");
                    continue;
                }
                FormSpec.Unit u = new FormSpec.Unit();
                u.code = trim(uo.getString("code"));
                u.label = trim(uo.getString("label"));
                if (u.code.isEmpty()) {
                    errs.add("units[" + i + "] 缺 code");
                    continue;
                }
                if (u.label.isEmpty()) {
                    u.label = u.code;
                }
                if (f.unit(u.code) != null) {
                    errs.add("units 有重复 code: " + u.code);
                } else {
                    f.units.add(u);
                }
            }
        }
        if (f.unitField.isEmpty() && f.units.size() > 1) {
            errs.add("unitField 为空时 units 只能有一个元素");
        }

        JSONArray na = o.getJSONArray("normal");
        if (na != null) {
            for (int i = 0; i < na.size(); i++) {
                String v = trim(na.getString(i));
                if (!v.isEmpty() && !f.normal.contains(v)) {
                    f.normal.add(v);
                }
            }
        }
        if (!f.resultField.isEmpty() && f.normal.isEmpty()) {
            errs.add("配了 resultField 就必须配 normal（正常值白名单，如 [\"0\",\"正常\"]）");
        }
        if (f.resultField.isEmpty()) {
            f.normal.clear();
        }

        JSONObject labels = o.getJSONObject("valueLabels");
        if (labels != null) {
            for (Map.Entry<String, Object> e : labels.entrySet()) {
                if (e.getValue() != null) {
                    f.valueLabels.put(e.getKey(), String.valueOf(e.getValue()));
                }
            }
        }

        f.imageUrl = trim(o.getString("imageUrl"));

        JSONArray ia = o.getJSONArray("items");
        if (ia != null) {
            List<String> seen = new ArrayList<String>();
            for (int i = 0; i < ia.size(); i++) {
                JSONObject io = ia.getJSONObject(i);
                if (io == null) {
                    errs.add("items[" + i + "] 不是对象");
                    continue;
                }
                FormSpec.Item it = new FormSpec.Item();
                it.field = trim(io.getString("field"));
                it.label = trim(io.getString("label"));
                it.imageField = trim(io.getString("imageField"));
                it.remarkField = trim(io.getString("remarkField"));
                if (it.field.isEmpty()) {
                    // 占位项：字段还没填，跳过查询，只在 /config 里提示
                    if (!it.label.isEmpty()) {
                        f.pendingItems.add(it.label);
                    }
                    continue;
                }
                if (!ident(it.field)) {
                    errs.add("items[" + i + "] field 只能包含字母、数字、下划线");
                    continue;
                }
                if (it.label.isEmpty()) {
                    it.label = it.field;
                }
                if (!it.imageField.isEmpty() && !ident(it.imageField)) {
                    errs.add("items[" + i + "] 的 imageField 只能包含字母、数字、下划线");
                    continue;
                }
                if (!it.remarkField.isEmpty() && !ident(it.remarkField)) {
                    errs.add("items[" + i + "] 的 remarkField 只能包含字母、数字、下划线");
                    continue;
                }
                if (seen.contains(it.field)) {
                    errs.add("items 有重复 field: " + it.field);
                    continue;
                }
                seen.add(it.field);
                f.items.add(it);
            }
        }

        JSONArray ga = o.getJSONArray("images");
        if (ga != null) {
            List<String> seen = new ArrayList<String>();
            for (int i = 0; i < ga.size(); i++) {
                JSONObject go = ga.getJSONObject(i);
                if (go == null) {
                    errs.add("images[" + i + "] 不是对象");
                    continue;
                }
                FormSpec.ImageField img = new FormSpec.ImageField();
                img.field = trim(go.getString("field"));
                img.label = trim(go.getString("label"));
                if (img.field.isEmpty()) {
                    f.pendingImages.add(img.label.isEmpty() ? "未命名图片位" : img.label);
                    continue;
                }
                if (!ident(img.field)) {
                    errs.add("images[" + i + "] field 只能包含字母、数字、下划线");
                    continue;
                }
                if (img.label.isEmpty()) {
                    img.label = img.field;
                }
                if (seen.contains(img.field)) {
                    errs.add("images 有重复 field: " + img.field);
                    continue;
                }
                seen.add(img.field);
                f.images.add(img);
            }
        }

        boolean needImageUrl = !f.images.isEmpty();
        for (FormSpec.Item it : f.items) {
            if (!it.imageField.isEmpty()) {
                needImageUrl = true;
            }
        }
        if (needImageUrl && f.imageUrl.isEmpty()) {
            errs.add("配了图片字段就必须配 imageUrl，推荐 {ctx}/api/checkboard/image?id={id}");
        }

        if (!errs.isEmpty()) {
            snap.errors.add(at + ": " + join(errs));
            return null;
        }
        return f;
    }

    private static FormSpec find(List<FormSpec> list, String key) {
        for (FormSpec f : list) {
            if (f.key.equals(key)) {
                return f;
            }
        }
        return null;
    }

    private static String readFile(File file) throws Exception {
        FileInputStream in = new FileInputStream(file);
        try {
            return read(in);
        } finally {
            close(in);
        }
    }

    private static String readClasspath() {
        String[] paths = {"/com/dt/checkboard/" + FILE_NAME, "/" + FILE_NAME};
        for (String path : paths) {
            InputStream in = FormConfig.class.getResourceAsStream(path);
            if (in == null) {
                continue;
            }
            try {
                return read(in);
            } catch (Exception e) {
                LogUtil.error("读取 classpath:" + path + " 失败", e);
            } finally {
                close(in);
            }
        }
        return null;
    }

    private static String read(InputStream in) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            bos.write(buf, 0, n);
        }
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }

    private static void close(InputStream in) {
        try {
            in.close();
        } catch (Exception ignore) {
            // ignore
        }
    }

    private static boolean ident(String s) {
        return s != null && !s.isEmpty() && s.matches("[A-Za-z0-9_]+");
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    public static String join(List<String> list) {
        StringBuilder sb = new StringBuilder();
        for (String s : list) {
            if (sb.length() > 0) {
                sb.append("；");
            }
            sb.append(s);
        }
        return sb.toString();
    }

    public static final class Snapshot {
        public String title = "点检看板";
        public String source = "未加载";
        public String loadedAt = "";
        /** 每次加载递增，用于让缓存自然失效。 */
        public long version;
        public long mtime;
        public List<FormSpec> forms = new ArrayList<FormSpec>();
        public List<String> errors = new ArrayList<String>();
    }
}
