package com.dt.checkboard.dao;

import com.dt.checkboard.config.FormSpec;
import com.dt.checkboard.entity.CheckRecord;
import com.dt.checkboard.entity.UnitStat;
import com.dt.common.util.LogUtil;
import weaver.conn.RecordSet;
import weaver.general.Util;

import java.util.ArrayList;
import java.util.List;

/**
 * 点检数据访问层。
 *
 * <p>概览只查聚合结果（每天每单位一行），明细按需查询，两者互不影响。</p>
 */
public class CheckBoardDao {

    /**
     * 按应检单位聚合当天的记录：一行代表一个单位，行数等于设备数而不是记录数。
     */
    public List<UnitStat> stat(FormSpec f, String date) {
        List<UnitStat> list = new ArrayList<UnitStat>();
        List<Object> params = new ArrayList<Object>();
        StringBuilder sql = new StringBuilder(512);

        sql.append("SELECT ");
        if (f.hasUnitField()) {
            sql.append("r.").append(f.unitField).append(" AS unit, ");
        }
        sql.append("COUNT(*) AS cnt");
        sql.append(", MAX(r.").append(f.timeField).append(") AS last_time");

        if (f.hasResult()) {
            String result = "r." + f.resultField;
            sql.append(", SUM(CASE WHEN ").append(result).append(" IS NULL OR ").append(result)
                    .append(" = '' THEN 1 ELSE 0 END) AS empty_cnt");
            sql.append(", SUM(CASE WHEN ").append(result).append(" IS NOT NULL AND ").append(result)
                    .append(" <> '' AND ").append(result).append(" NOT IN (");
            for (int i = 0; i < f.normal.size(); i++) {
                if (i > 0) {
                    sql.append(", ");
                }
                sql.append("?");
                params.add(f.normal.get(i));
            }
            sql.append(") THEN 1 ELSE 0 END) AS bad_cnt");
        } else {
            sql.append(", 0 AS empty_cnt, 0 AS bad_cnt");
        }

        sql.append(", SUM(CASE WHEN r.modedatacreatedate > r.").append(f.dateField)
                .append(" THEN 1 ELSE 0 END) AS late_cnt");
        sql.append(" FROM ").append(f.table).append(" r");
        sql.append(" WHERE r.").append(f.dateField).append(" = ?");
        params.add(date);
        if (f.hasUnitField()) {
            sql.append(" GROUP BY r.").append(f.unitField);
        }

        String finalSql = sql.toString();
        try {
            RecordSet rs = new RecordSet();
            rs.executeQuery(finalSql, params.toArray());
            while (rs.next()) {
                UnitStat s = new UnitStat();
                if (f.hasUnitField()) {
                    s.unit = Util.null2String(rs.getString("unit")).trim();
                }
                s.count = rs.getInt("cnt");
                s.last = Util.null2String(rs.getString("last_time")).trim();
                s.empty = rs.getInt("empty_cnt");
                s.bad = rs.getInt("bad_cnt");
                s.late = rs.getInt("late_cnt");
                list.add(s);
            }
        } catch (Exception e) {
            LogUtil.error("查询 " + f.name + " 失败, sql=" + finalSql, e);
            throw new IllegalStateException("查询 " + f.name + " 失败: " + e.getMessage(), e);
        }
        return list;
    }

    /**
     * 某个应检单位当天的明细，按主键倒序，最多 limit 条。
     */
    public List<CheckRecord> detail(FormSpec f, String date, String unit, int limit) {
        List<CheckRecord> list = new ArrayList<CheckRecord>();
        List<Object> params = new ArrayList<Object>();
        StringBuilder cols = new StringBuilder(640);
        cols.append("r.id AS rid, r.").append(f.timeField).append(" AS rtime")
                .append(", r.").append(f.inspectorField).append(" AS rins")
                .append(", h.lastname AS rname")
                .append(", ").append(f.hasUnitField() ? "r." + f.unitField : "''").append(" AS runit")
                .append(", ").append(f.hasResult() ? "r." + f.resultField : "''").append(" AS rresult")
                .append(", r.modedatacreatedate AS rcreate");
        for (int i = 0; i < f.items.size(); i++) {
            FormSpec.Item item = f.items.get(i);
            cols.append(", r.").append(item.field).append(" AS it").append(i);
            if (!item.imageField.isEmpty()) {
                cols.append(", r.").append(item.imageField).append(" AS iti").append(i);
            }
            if (!item.remarkField.isEmpty()) {
                cols.append(", r.").append(item.remarkField).append(" AS itr").append(i);
            }
        }
        for (int i = 0; i < f.images.size(); i++) {
            cols.append(", r.").append(f.images.get(i).field).append(" AS im").append(i);
        }

        StringBuilder sql = new StringBuilder(768);
        sql.append("SELECT ").append(cols)
                .append(" FROM ").append(f.table).append(" r")
                .append(" LEFT JOIN hrmresource h ON h.id = r.").append(f.inspectorField)
                .append(" WHERE r.").append(f.dateField).append(" = ?");
        params.add(date);
        if (f.hasUnitField() && unit != null && !unit.isEmpty()) {
            sql.append(" AND r.").append(f.unitField).append(" = ?");
            params.add(unit);
        }
        sql.append(" ORDER BY r.id DESC");
        if (limit > 0) {
            sql.append(" LIMIT ").append(limit);
        }

        String finalSql = sql.toString();
        try {
            RecordSet rs = new RecordSet();
            rs.executeQuery(finalSql, params.toArray());
            while (rs.next()) {
                CheckRecord r = new CheckRecord();
                r.id = rs.getInt("rid");
                r.time = Util.null2String(rs.getString("rtime")).trim();
                r.inspectorId = Util.null2String(rs.getString("rins")).trim();
                r.inspector = Util.null2String(rs.getString("rname")).trim();
                r.unit = Util.null2String(rs.getString("runit")).trim();
                r.result = Util.null2String(rs.getString("rresult")).trim();
                r.createdDate = Util.null2String(rs.getString("rcreate")).trim();
                r.late = !r.createdDate.isEmpty() && r.createdDate.compareTo(date) > 0;
                r.emptyResult = f.hasResult() && r.result.isEmpty();
                r.abnormal = f.hasResult() && !r.result.isEmpty() && !f.normal.contains(r.result);

                r.itemValues = new String[f.items.size()];
                r.itemImages = new String[f.items.size()];
                r.itemRemarks = new String[f.items.size()];
                for (int i = 0; i < f.items.size(); i++) {
                    r.itemValues[i] = Util.null2String(rs.getString("it" + i)).trim();
                    r.itemImages[i] = f.items.get(i).imageField.isEmpty()
                            ? "" : Util.null2String(rs.getString("iti" + i)).trim();
                    r.itemRemarks[i] = f.items.get(i).remarkField.isEmpty()
                            ? "" : Util.null2String(rs.getString("itr" + i)).trim();
                }
                r.imageValues = new String[f.images.size()];
                for (int i = 0; i < f.images.size(); i++) {
                    r.imageValues[i] = Util.null2String(rs.getString("im" + i)).trim();
                }
                list.add(r);
            }
        } catch (Exception e) {
            LogUtil.error("查询 " + f.name + " 明细失败, sql=" + finalSql, e);
            throw new IllegalStateException("查询 " + f.name + " 明细失败: " + e.getMessage(), e);
        }
        return list;
    }

    /**
     * 检查配置里用到的列是否都存在，返回空串表示通过。
     */
    public String checkColumns(FormSpec f) {
        StringBuilder sql = new StringBuilder(200);
        sql.append("SELECT r.").append(f.dateField)
                .append(", r.").append(f.timeField)
                .append(", r.").append(f.inspectorField);
        if (f.hasResult()) {
            sql.append(", r.").append(f.resultField);
        }
        if (f.hasUnitField()) {
            sql.append(", r.").append(f.unitField);
        }
        sql.append(" FROM ").append(f.table).append(" r WHERE 1 = 0");
        try {
            RecordSet rs = new RecordSet();
            rs.executeQuery(sql.toString());
            rs.next();
            return "";
        } catch (Exception e) {
            return e.getMessage() == null ? e.toString() : e.getMessage();
        }
    }

    /**
     * 抽样某个字段最近一段时间的值域，用于发现配置与实际表单不一致。
     *
     * @return 每个元素为 {值, 出现次数}
     */
    public List<String[]> distinct(FormSpec f, String field, String fromDate, int limit) {
        List<String[]> list = new ArrayList<String[]>();
        if (field == null || field.isEmpty()) {
            return list;
        }
        String sql = "SELECT " + field + " AS v, COUNT(*) AS c FROM " + f.table
                + " WHERE " + f.dateField + " >= ? GROUP BY " + field + " ORDER BY c DESC";
        try {
            RecordSet rs = new RecordSet();
            rs.executeQuery(sql, fromDate);
            while (rs.next() && list.size() < limit) {
                list.add(new String[]{
                        Util.null2String(rs.getString("v")).trim(),
                        String.valueOf(rs.getInt("c"))
                });
            }
        } catch (Exception e) {
            LogUtil.error("统计 " + f.key + "." + field + " 值域失败, sql=" + sql, e);
        }
        return list;
    }
}
