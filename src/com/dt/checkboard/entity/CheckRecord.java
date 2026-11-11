package com.dt.checkboard.entity;

/**
 * 一条点检记录，只用于明细展示。
 */
public class CheckRecord {

    public int id;
    public String time = "";
    public String inspectorId = "";
    public String inspector = "";
    public String unit = "";
    public String result = "";
    public String createdDate = "";

    public boolean abnormal;
    public boolean emptyResult;
    public boolean late;

    /** 与 FormSpec.items 一一对应的原始值。 */
    public String[] itemValues = new String[0];

    /** 与 FormSpec.items 一一对应的图片原始值。 */
    public String[] itemImages = new String[0];

    /** 与 FormSpec.items 一一对应的备注原文。 */
    public String[] itemRemarks = new String[0];

    /** 与 FormSpec.images 一一对应的原始值。 */
    public String[] imageValues = new String[0];
}
