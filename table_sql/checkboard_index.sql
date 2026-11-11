-- 点检看板：为三张点检表的日期字段补索引
-- 背景：三张表当前只有主键，看板按日期过滤会全表扫描，数据量上来后每天三次慢查询。
-- 说明：索引名重复（1061 Duplicate key name）说明已经建过，忽略即可。
-- 注意：泛微建模重建表可能丢索引，重建后需要重跑本脚本。

ALTER TABLE `edc_uf_table1112` ADD INDEX `idx_cb_date` (`field007`);
ALTER TABLE `edc_uf_table1109` ADD INDEX `idx_cb_date` (`field002`);
ALTER TABLE `edc_uf_table1111` ADD INDEX `idx_cb_date` (`field004`);
