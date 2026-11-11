# check-board 点检看板

按「应检格」统计三张泛微点检表每天的完成情况。判据只有一条：

> **某个应检单位当天有至少一条记录 = 该格完成。**

点检人是谁、上的是白班还是夜班、当天填了几条，都不影响判定。

## 一、口径

| 表单 | 数据表 | 日期 | 时间 | 点检人 | 应检单位 | 结果 | 每天格数 |
|---|---|---|---|---|---|---|---|
| 叉车日常点检 | `edc_uf_table1112` | `field007` | `field071` | `field040` | 固定「叉车」 | `field009` | 1 |
| 焊机点检 | `edc_uf_table1109` | `field002` | `field003` | `field001` | `field023`（0~3） | `field013` | 4 |
| 产线自检（辊压） | `edc_uf_table1111` | `field004` | `field010` | `field011` | `field040`（0~3） | `field029` | 4 |

合计 **9 格/天**。一张表一天有 20 条记录，也只算它该算的那几格。

- 同一单位当天多条记录（不同点检人、不同班次）：仍只算 1 格，条数作为细节显示。例如 2026-11-09 焊机编号 1 有 3 条记录，仍然只是一格。
- 任意一条记录结果异常，该格标记为异常。
- 结果字段为空的记录单独标记「结果未填」，不混进异常。
- 点检日期晚于创建日期（`modedatacreatedate`）的记录标记为「补录」，用于识别事后补填。

## 二、目录

```
src/
  com/api/checkboard/BoardApi.java          REST 入口：summary / detail / config / page
  com/dt/checkboard/
    config/FormSpec.java                    一张表的契约
    config/FormConfig.java                  JSON 加载、校验、热加载
    dao/CheckBoardDao.java                  聚合查询、明细查询、值域抽样
    entity/UnitStat.java                    单位聚合结果
    entity/CheckRecord.java                 明细记录
    service/BoardService.java               格差集、状态组装
    checkboard.json                         唯一配置源
    index.html                              看板页面（随接口一起发布）
table_sql/
  edc_uf_table1109.sql / 1111 / 1112       三张表的原始结构，仅作参考
  checkboard_index.sql                     日期字段索引
```

## 三、配置

配置是一份 JSON，默认查找顺序：

1. `-Dcheckboard.conf=/绝对路径/checkboard.json`
2. `{weaver.root}/classbean/com/dt/checkboard/checkboard.json`（`weaver.root` 默认 `/data/weaver/ecology`）
3. classpath 下的 `/com/dt/checkboard/checkboard.json`

### 字段说明

| 键 | 必填 | 说明 |
|---|---|---|
| `key` | 是 | 表单标识，接口参数用它 |
| `name` | 是 | 页面显示名 |
| `table` | 是 | 物理表名 |
| `dateField` | 是 | 点检日期字段，varchar `yyyy-MM-dd` |
| `timeField` | 是 | 点检时间字段，用于显示最晚时间 |
| `inspectorField` | 是 | 点检人字段（人员 ID） |
| `resultField` | 否 | 结果字段；留空表示只判覆盖、不判异常 |
| `unitField` | 否 | 应检单位字段；留空表示单台设备 |
| `units` | 是 | 应检单位清单，`code` 必须等于字段实际存储值 |
| `normal` | 配了 `resultField` 时必填 | 正常值白名单，不在清单里即异常 |
| `valueLabels` | 否 | 仅用于展示的取值文案，不参与判定 |
| `items` | 否 | 点检项清单，只用于明细页定位异常项 |
| `images` | 否 | 整单级图片字段（如现场照片） |
| `imageUrl` | 配了图片字段时必填 | 图片地址模板，`{id}` 为文件 id，`{ctx}` 为应用上下文 |
| `detailUrl` | 否 | 原单链接模板，`{id}` 会替换成记录主键 |

`unitField` 为空时，`units` 只能有一个元素（叉车就是这种）。

### 点检项与图片（只影响明细页）

`items` 不是用来重画表单的，只在某条记录被判为异常时，告诉使用者“哪一项不合格”。正常记录不展示，所以多配几项也不会让页面变啰嗦。

- `field` 是点检项所在地列，`label` 是展示文案。
- 判定复用 `normal` 白名单：值非空且不在白名单 → 该项异常。
- 可选 `imageField`，该项自己的图片列，照片跟在该项下面。
- 可选 `remarkField`，该项自己的备注列，会显示在异常项下面。
- `images` 用于整单照片，展示在记录底部。

```json
"items": [
  { "field": "field014", "label": "① 外壳PE线接线正确、连接可靠" },
  { "field": "field015", "label": "② 易燃易爆管道", "imageField": "field030", "remarkField": "field031" }
],
"images": [{ "field": "field022", "label": "现场照片" }],
"imageUrl": "{ctx}/weaver/weaver.file.ImageFileView?imagefileid={id}"
```

`field` 留空表示**占位**：该项不参与查询，只在 `/api/checkboard/config` 的 `pendingItems` 里列出来。可以先把标题写好，字段号后面再补，配置不会因此报错。

`imageUrl` 兼容三种存法：纯文件 id（`123`）、逗号分隔 id（`123,456`）、泛微的 JSON 串；已经是 `http(s)://` 或 `/` 开头的地址原样使用。`{ctx}` 会替换成应用上下文，部署在 `/ec` 下不用改配置。

图片地址和泛微版本有关，先拿一张真实照片确认。常见两种：

- 图片：`{ctx}/weaver/weaver.file.ImageFileView?imagefileid={id}`
- 附件：`{ctx}/weaver/weaver.file.FileDownload?fileid={id}&download=1`

### 热加载

配置文件每 5 秒检查一次 `lastModified`，改动后自动生效，**不需要重启泛微**。

新配置校验不通过时**不会生效**，继续使用上一版，同时：

- 服务端日志写一条 `配置改动未生效`
- 页面顶部出现黄色横幅
- `/api/checkboard/config` 的 `reloadError` 返回原因

这样改坏配置不会让看板突然变成「全部未检」。

### 校验规则

启动或热加载时逐条检查，任何一条不通过就拒绝该表单并在页面上报出来：

- `table` / 各字段名只能是字母、数字、下划线
- `units` 不能为空，`code` 不能重复
- `unitField` 非空时 `units` 可以有多个；为空时只能有一个
- 配了 `resultField` 就必须配 `normal`

## 四、接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/checkboard/summary?date=2026-11-11` | 9 格状态汇总，页面主数据；加 `&force=1` 绕过缓存 |
| GET | `/api/checkboard/detail?form=welder&date=2026-11-11&unit=0` | 某一格的原始记录，最多 200 条 |
| GET | `/api/checkboard/config` | 配置自检：字段是否存在、实际值域与配置是否一致 |
| GET | `/api/checkboard/page` | 看板页面 |

页面支持 `?date=2026-11-09` 直接打开指定日期，方便对照测试数据；页面里的接口前缀由当前地址自动推导，部署在 `/ec` 等上下文下同样可用（地址必须以 `/page` 结尾）。

### 缓存与刷新

`summary` 默认走 5 秒缓存：同一日期 5 秒内重复请求只查一次库，多个人同时打开页面也只查一次。页面上的「刷新」按钮带 `force=1` 绕过缓存，所以主动刷新永远是实时数据；返回里的 `generatedAt` 是数据生成时间，页面显示成「数据 15:34:05」，缓存命中时一眼能看出来。

没有在按钮上加固定冷却，原因很简单：刚在泛微填完点检单、想立刻看有没有算进去，被「5 秒后才能刷新」挡住比多查三次库更难受。真正防重复的是两件事：

- 服务端 5 秒缓存，挡住并发打开和重复请求
- 前端在请求未返回时禁用按钮，挡住连点

`detail` 和 `config` 不走缓存，点开明细永远是实时数据。`config` 会逐表做列校验和值域抽样，一次大约 9 条小查询，属于诊断接口，不要拿去轮询。

`summary` 关键结构：

```json
{
  "success": true,
  "date": "2026-11-11",
  "total": { "required": 9, "done": 8, "missing": 1, "bad": 2, "late": 0 },
  "forms": [{
    "key": "welder", "name": "焊机点检", "required": 4, "done": 3,
    "cells": [{ "code": "0", "label": "1号焊机", "done": true, "count": 1,
                "bad": false, "empty": false, "late": false, "last": "10:31" }],
    "missing": ["4号焊机"], "orphan": 0
  }],
  "configErrors": [], "reloadError": ""
}
```

`orphan` 是「记录里的单位值不在配置清单里」的条数。比如焊机表单出现了编号 `4`，而配置只有 0~3，就会在这里加一，不会被静默忽略。

## 五、部署

1. 用 IntelliJ 编译本模块，或：

   ```bash
   javac -encoding UTF-8 -cp "<ecology 的 lib 目录>/*" -d out/production/check-board $(find src -name "*.java")
   ```

2. 把编译输出里的 `com` 目录整体覆盖到 `{weaver.root}/classbean/`，确认三个位置存在：

   ```
   classbean/com/api/checkboard/BoardApi.class
   classbean/com/dt/checkboard/config/FormConfig.class
   classbean/com/dt/checkboard/checkboard.json
   classbean/com/dt/checkboard/index.html
   ```

   IDE 有时不会把 `src` 下的非 java 文件复制到输出目录，那就手工把 `checkboard.json` 和 `index.html` 从 `src/com/dt/checkboard/` 拷过去。

3. 执行 `table_sql/checkboard_index.sql` 补索引。

4. 重启泛微（Java 类变更需要重启；之后只改 JSON 就不用重启了）。

5. 验证：

   - 先看 `http://<host>:9083/api/checkboard/config`，应是 JSON 且 `"errors": []`
   - 再看 `http://<host>:9083/api/checkboard/page?date=2026-11-09`
   - 若整套应用部署在 `/ec` 上下文下，两个地址前面都要加 `/ec`
   - `/api/checkboard/config` 应返回 `"errors": []`，每张表 `columnsOk: true`
   - `/api/checkboard/page` 打开页面，切到 `2026-11-09`：焊机应显示 1号、2号两格已检（3号、4号未检），其中编号 1 有三条记录但仍只算一格；叉车和产线当天无记录

## 六、上线前要补的字段映射

产线自检表的字段是从样例数据反推的，其中 `unitField` 和 `resultField` 需要用属性面板确认：

```sql
SELECT id, field001, field004, field010, field011, field029, field034, field040
FROM edc_uf_table1111 ORDER BY id DESC LIMIT 20;
```

判断方法：

- `unitField`：应为「机台」，取值 0~3（对应 1~4 线）。若实际是 1~4，把 `units` 的 `code` 改成 `1`~`4`。
- `resultField`：应为「点检结果判定」，取值 0/1。
- `field034`（班次）**不参与配置**，不用管。

改完打开 `/api/checkboard/config`，看 `resultValues` 是不是只有 0/1 两个值、`unexpectedUnits` 是否为空。如果 `resultValues` 里出现了大量零件号之类的值，说明 `resultField` 配错了。

### 叉车的点检项待填（配置里已放好占位）

`checkboard.json` 里 forklift 的 `items` 已经有 10 条占位，`field`、`imageField`、`remarkField` 都是空的。字段号没填之前这些项不参与查询，`/api/checkboard/config` 的 `pendingItems` 会一直列着它们，填一个少一个。

先跑一条 SQL 找字段：

```sql
SELECT * FROM edc_uf_table1112 WHERE id = (SELECT MAX(id) FROM edc_uf_table1112);
```

对照表单设计器右侧的「字段名称」，按行填三样：

- `field`：√/× 结果列
- `imageField`：该行的图片列（值是文件 id 或 JSON 串）
- `remarkField`：该行的备注列

叉车的 `images`（整单照片）留了空位，表里有就填，没有就保持空。

字段没填之前，明细页会显示「点检项字段待配置（N 项）」，不会只显示一个「异常」让人猜。

### 焊机的备注

焊机没有图片控件，配置里已经去掉了 `imageUrl`。

备注按列排布推断是**每项一个**：8 个点检项对应 `field024`~`field031`（正好是 8 个 `varchar(200)`，测试数据里的“测试异常”落在第一项的 `field024`）。已经按顺序写进每项的 `remarkField`，显示在异常项下面。如果实际错位，对照属性面板改这 8 个 `remarkField` 即可。

焊机的 8 个点检项已经按 `field014`~`field021` 配好，可以拿它当样例。产线自检的 `items` 仍是空的，需要时按同样方式补。

`detailUrl` 目前留空。要跳转泛微原单时填模板，例如：

```json
"detailUrl": "/workflow/request/ViewRequest.jsp?requestid={id}"
```

## 七、排障

| 现象 | 先看哪 |
|---|---|
| 页面顶部黄色横幅 | 配置校验没过，按文字改 JSON |
| 某张表显示「查询失败」 | 表名或字段名写错，或该表没权限。它的应检格仍计入分母，会显示为未检 |
| 某张表一直 0 格 | `/config` 的 `columnsOk`，再看当天表里到底有没有数据 |
| 应检 9 格但只显示 8 格 | 某张表单配置被校验拒绝，看 `configErrors` |
| 格子显示 ×2 / ×3 | 当天有多条记录，正常，不影响判定 |
| 「未登记编号」不为 0 | 表单里出现了配置外的单位值，对照 `unexpectedUnits` 补 `units` |
| 异常记录没列出具体项 | 该表 `items` 没配，或项的值不在 `normal` 白名单里 |
| 异常项下面没有备注 | 该项没配 `remarkField` |
| 照片不显示 / 显示裂图 | `imageUrl` 模板与泛微版本不匹配，用真实照片地址核对一次 |
| 照片返回 401/403 | 文件地址需要登录态或加密参数，换用 `isencrypt=1` 形式的地址 |

**数据源健康**：如果某张表连续两天 0 条记录，先怀疑表单链接、字段改名、权限变化，而不是「大家都不点检」。看板本身不应该把「数据源断了」显示成「完成率 0%」。

## 八、与旧版的差异

| 项 | 旧版 | 现在 |
|---|---|---|
| 配置 | `checkboard.properties`，扁平 key + 自定义分隔符 | `checkboard.json`，嵌套结构 |
| 表单形态 | `mode=simple/device` 两种分支 + 空值兜底 | 只有「单位清单」一种，单台设备就是一个元素 |
| 异常判定 | 黑名单 + `contains`（「无异常」会误判） | 白名单，不在清单里才算异常 |
| 明细项 | 每张表强制映射，全部展示 | 可选映射，只展示异常项，正常记录不占空间 |
| 查询 | 每表拉全量记录，Java 里 HashSet 聚合 | 每表一条 `group by`，行数 = 格数 |
| 配置错误 | 静默跳过，页面显示「无记录」 | 启动校验 + 页面横幅 + `/config` 可见 |
| 配置生效 | 类加载时读一次，改动要重启 | 文件方式 5 秒热加载，改错不生效 |
| 页面 | `front/index.html` 与 classbean 两份副本 | 只有 `src/com/dt/checkboard/index.html` 一份 |

## 九、已知限制

- 只按「点检日期」字段归日，不猜夜班跨零点。填单人把生产日期填到哪一天，就算哪一天。
- 焊机表单截图里设备编号有 0~4 五个选项，配置按 4 台（0~3）。若真有人选 4，会以「未登记编号」提示，不会算进应检格。
- 1111 的合格/不良数量没有接入指标。这张表同时承载质量数据，完成率只看「有没有自检」，产量与不良率另行统计。
- 叉车的点检项是 10 条占位，字段号待填；产线自检的 `items` 仍为空；焊机已配好 8 项且没有图片。
- 焊机的备注列（`field024`~`field031`）是按列排布推断的，上线时核对一次，错位只改配置。
- 图片地址模板与泛微版本相关，`{ctx}/weaver/weaver.file.ImageFileView?imagefileid={id}` 是常见形式，实际以测试环境为准。
- 明细最多返回 200 条，超过只显示最近 200 条。
- 项目目前没有版本管理，建议 `git init` 后再上线，方便回滚配置。
# CheckBoard
