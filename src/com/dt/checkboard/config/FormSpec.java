package com.dt.checkboard.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一张点检表的契约。
 *
 * <p>监控平台只关心四件事：哪天、哪个应检单位、有没有记录、结果是否正常。
 * 点检人、班次、明细项都不参与完成判定。</p>
 */
public final class FormSpec {

    public String key = "";
    public String name = "";
    public String table = "";

    public String dateField = "";
    public String timeField = "";
    public String inspectorField = "";
    public String resultField = "";
    public String unitField = "";

    /** 应检单位清单。叉车一个元素，焊机四个，产线四条线。 */
    public List<Unit> units = new ArrayList<Unit>();

    /** 正常值白名单，不在清单里的值视为异常。 */
    public List<String> normal = new ArrayList<String>();

    /** 仅用于展示的值文案，不参与判定。 */
    public Map<String, String> valueLabels = new LinkedHashMap<String, String>();

    /** 原单链接模板，{id} 会被替换成记录主键，留空则不显示链接。 */
    public String detailUrl = "";

    /** 点检项清单，只用于明细页定位异常项，不参与完成判定。 */
    public List<Item> items = new ArrayList<Item>();

    /** 整单级的图片字段（如现场照片）。 */
    public List<ImageField> images = new ArrayList<ImageField>();

    /** 图片地址模板，{id} 替换成文件 id；留空则不展示图片。 */
    public String imageUrl = "";

    /** 还没填字段的点检项，仅用于 /config 提示，不参与查询。 */
    public List<String> pendingItems = new ArrayList<String>();

    /** 还没填字段的图片位，仅用于 /config 提示。 */
    public List<String> pendingImages = new ArrayList<String>();

    public boolean hasUnitField() {
        return unitField != null && !unitField.isEmpty();
    }

    public boolean hasResult() {
        return resultField != null && !resultField.isEmpty();
    }

    /** 每天应检格数。 */
    public int required() {
        return units.size();
    }

    public Unit unit(String code) {
        if (code == null) {
            return null;
        }
        for (Unit u : units) {
            if (u.code.equals(code)) {
                return u;
            }
        }
        return null;
    }

    public String labelOf(String code) {
        Unit u = unit(code);
        return u == null ? (code == null ? "" : code) : u.label;
    }

    public String valueLabel(String raw) {
        if (raw == null) {
            return "";
        }
        String v = valueLabels.get(raw);
        return v == null ? raw : v;
    }

    public String url(int id) {
        if (detailUrl == null || detailUrl.isEmpty()) {
            return "";
        }
        return detailUrl.replace("{id}", String.valueOf(id));
    }

    @Override
    public String toString() {
        return name + "(" + key + ")";
    }

    public static final class Unit {
        public String code = "";
        public String label = "";

        public Unit() {
        }

        public Unit(String code, String label) {
            this.code = code;
            this.label = label;
        }

        @Override
        public String toString() {
            return label + "[" + code + "]";
        }
    }

    /**
     * 一个点检项。值不在 normal 白名单里即视为该项异常。
     */
    public static final class Item {
        public String field = "";
        public String label = "";
        /** 该项自己的图片字段，可为空。 */
        public String imageField = "";
        /** 该项自己的备注字段，可为空。 */
        public String remarkField = "";
    }

    public static final class ImageField {
        public String field = "";
        public String label = "";
    }
}
