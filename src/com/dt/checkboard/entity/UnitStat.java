package com.dt.checkboard.entity;

/**
 * 一个应检单位在指定日期内的聚合结果，对应一条 group by 记录。
 */
public class UnitStat {

    public String unit = "";

    /** 当天记录条数，只作为细节展示，不影响完成判定。 */
    public int count;

    /** 结果异常的记录条数。 */
    public int bad;

    /** 结果字段为空的记录条数。 */
    public int empty;

    /** 补录（创建日期晚于点检日期）的记录条数。 */
    public int late;

    /** 最晚一条记录的时间。 */
    public String last = "";

    public boolean done() {
        return count > 0;
    }
}
