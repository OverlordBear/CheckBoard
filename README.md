# check-board 点检看板

跑在泛微 E9（weaver-ecology-3.28）上的二开模块。把三张建模点检表折成每天 **9 个应检格**，回答「今天哪台设备没人点检」。

判据只有一条：

> **某个应检单位当天有至少一条记录 = 该格完成。**

点检人是谁、白班还是夜班、当天填了几条，都不影响判定。

技术栈：Java 8、E9 JAX-RS（Jersey 1.19）、fastjson、原生 HTML/JS，无前端构建。

## 一、口径

| 表单 | 数据表 | 日期 | 时间 | 点检人 | 应检单位 | 结果 | 每天格数 |
|---|---|---|---|---|---|---|---|
| 叉车日常点检 | `edc_uf_table1112` | `field007` | `field071` | `field040` | 固定「叉车」 | `field009` | 1 |
| 焊机点检 | `edc_uf_table1109` | `field002` | `field003` | `field001` | `field023`（0~3） | `field013` | 4 |
| 产线自检（辊压） | `edc_uf_table1111` | `field004` | `field010` | `field011` | `field008`（0~3） | `field029` | 4 |

合计 **9 格/天**。一张表一天有 20 条记录，也只算它该算的那几格。

- 同一单位当天多条记录（不同点检人、不同班次）：仍只算 1 格，条数作为细节显示为 `×2` / `×3`。
- 任意一条记录结果异常，该格标记为异常。
- 结果字段为空的记录单独标记「结果未填」，不混进异常。
- 创建日期晚于点检日期（`modedatacreatedate > 点检日期`）标记「补录」，用于识别事后补填；**补录仍然算完成**，只做提示。
- 记录里的单位值不在 `units` 清单里，计入「未登记编号」，不新增格，也不静默丢弃。

> 产线 `unitField` 当前按 `field008` 配置。若属性面板显示机台实际是 `field040`，改 JSON 即可，不用改代码；`/api/checkboard/config` 的 `unitValues` 能看到实际值域。

## 二、目录

```
src/
  com/api/checkboard/BoardApi.java          REST 入口：summary / detail / image / config / page
  com/dt/checkboard/
    config/FormSpec.java                    一张表的契约
    config/FormConfig.java                  JSON 加载、校验、热加载
    dao/CheckBoardDao.java                  聚合查询、明细查询、值域抽样
    entity/UnitStat.java                    单位聚合结果
    entity/CheckRecord.java                 明细记录
    service/BoardService.java               格差集、状态组装、5 秒缓存
    checkboard.json                         唯一配置源
    index.html                              看板页面（随接口一起发布）
table_sql/
  edc_uf_table1109.sql / 1111 / 1112        三张表的原始结构，仅作参考
  checkboard_index.sql                      日期字段索引
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
| `imageUrl` | 配了图片字段时必填 | 图片地址模板，推荐 `{ctx}/api/checkboard/image?id={id}` |
| `detailUrl` | 否 | 原单链接模板，`{id}` 会替换成记录主键 |

`unitField` 为空时，`units` 只能有一个元素（叉车就是这种）。

### 当前三张表的映射

**叉车（forklift）**

- 14 个点检项：`field010`、`field013`、`field015`…`field037`。
- 每项的图片：`field042`、`field044`、`field047`…`field069`；每项的备注：`field045`、`field046`、`field048`…`field070`。
- 整单照片位 `images` 留空，表里有再加。
- `imageUrl` 指向本服务的 `/api/checkboard/image?id={id}`，不走 E9 的 `FileDownload`。

> **待核对**：1112 的列类型是 `010(varchar100)/011(varchar200)`、`013/014`…`037/038` 的「结果 + 备注」成对结构，`011/014/…/038` 更像备注列；当前配置用的是 `045`–`070`。上线前在测试环境填一条叉车点检，第 1 项备注写 `REMARK1`、传一张图，然后 `SELECT field011, field042, field045 FROM edc_uf_table1112 ORDER BY id DESC LIMIT 1`，一次就能确认。

**焊机（welder）**

- 8 个点检项：`field014`~`field021`，每项备注：`field024`~`field031`。
- 焊机没有图片控件，配置里没有 `imageUrl`。

**产线（line）**

- 10 个点检项：`field013`~`field022`，label 暂用「点检项 1」~「点检项 10」占位。
- 产线点检表没有图片控件，所以不配 `imageUrl`，也不配 `imageField`。
- 拿到表单原文后只改 `label`，不要动 `field`。
- `field027`、`field035`、`field038` 还没接入（可能是复检/首件确认，待确认）。
- 1111 样例里 `field013` 出现过 `00`。如果表单确实会存两位，明细会把它判成异常项；要么规范数据，要么把 `00` 加进 `normal`。

### 点检项与图片

`items` 不是用来重画表单的，只在某条记录被判为异常时，告诉使用者「哪一项不合格」。正常记录不展示，所以多配几项不会让页面变啰嗦。

- `field` 是点检项所在地列，`label` 是展示文案。
- 判定复用 `normal` 白名单：值非空且不在白名单 → 该项异常。
- 可选 `imageField`，该项自己的图片列，照片跟在该项下面。
- 可选 `remarkField`，该项自己的备注列，会显示在异常项下面。
- `images` 用于整单照片，展示在记录底部。
- `field` 留空表示**占位**：该项不参与查询，只在 `/api/checkboard/config` 的 `pendingItems` 里列出来。可以先把标题写好，字段号后面再补，配置不会因此报错。

```json
"items": [
  { "field": "field014", "label": "① 外壳PE线接线正确、连接可靠" },
  { "field": "field015", "label": "② 易燃易爆管道", "imageField": "field030", "remarkField": "field031" }
],
"images": [{ "field": "field022", "label": "现场照片" }],
"imageUrl": "{ctx}/api/checkboard/image?id={id}"
```

`imageUrl` 兼容三种存法：纯文件 id（`123`）、逗号分隔 id（`123,456`）、泛微的 JSON 串；已经是 `http(s)://` 或 `/` 开头的地址原样使用。`{ctx}` 会替换成应用上下文，部署在 `/ec` 下不用改配置。

### 图片的存储链路（docid 与 imagefileid）

点检表的图片字段和自建上传的照片不是一种 id，这是图片 404 的根源：

```
① 自定义上传（如 fanweiTest 的维修照片）
   业务表.photo = 327527 ─────────────► imagefile.imagefileid = 327527
                                        （getInputStreamById 一步读到）

② 建模图片控件（叉车的图片字段）
   业务表.field042 = 255709 ──docid──► DocImageFile.docid = 255709
                                        │ imagefileid = 327527
                                        ▼
                                      imagefile.imagefileid = 327527
```

`/api/checkboard/image` 的解析顺序：

1. 直接 `ImageFileManager.getInputStreamById(id)`，命中就返回（自定义上传的值走这条）
2. 未命中，`SELECT imagefileid FROM DocImageFile WHERE docid = ?`
3. 仍未命中，按 `DocImageFile.id = ?` 再查一次
4. 都取不到才 404

排查用：

```sql
SELECT imagefileid, imagefilename, imagefiletype, filerealpath
FROM imagefile WHERE imagefileid = 327527;

SELECT id, docid, imagefileid, docfiletype
FROM DocImageFile WHERE docid = 255709;
```

### 热加载

配置文件每 5 秒检查一次 `lastModified`（请求触发 + 节流，不是后台线程），改动后自动生效，**不需要重启泛微**。

新配置校验不通过时**不会生效**，继续使用上一版，同时：

- 服务端日志写一条 `配置改动未生效`
- 页面顶部出现黄色横幅
- `/api/checkboard/config` 的 `reloadError` 返回原因

这样改坏配置不会让看板突然变成「全部未检」。classpath 里的配置只读一次，不走热加载；要热加载就用文件方式。

### 校验规则

启动或热加载时逐条检查，任何一条不通过就拒绝该表单并在页面上报出来：

- `table` / 各字段名只能是字母、数字、下划线
- `units` 不能为空，`code` 不能重复
- `unitField` 非空时 `units` 可以有多个；为空时只能有一个
- 配了 `resultField` 就必须配 `normal`
- 配了图片字段（`images` 或任一项的 `imageField`）就必须配 `imageUrl`
- `items` 的 `field` 不能重复；`images` 的 `field` 不能重复

## 四、接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/checkboard/summary?date=2026-11-11` | 9 格状态汇总，页面主数据；加 `&force=1` 绕过缓存 |
| GET | `/api/checkboard/detail?form=welder&date=2026-11-11&unit=0` | 某一格的原始记录，最多 200 条 |
| GET | `/api/checkboard/image?id=255709` | 点检图片，按 docid 直接返回字节流 |
| GET | `/api/checkboard/config` | 配置自检：字段是否存在、实际值域与配置是否一致 |
| GET | `/api/checkboard/page` | 看板页面 |

### date 参数

- 留空或 `date=`：取服务器当天。
- 必须是严格的 `yyyy-MM-dd`（两位月、两位日）。
- 格式不对（`2026-2-3`），或日期本身不存在（`2026-99-99`、`2026-02-30`），一律返回 **HTTP 400**，不再静默退回今天：

```json
{ "success": false, "error": "date 参数不合法：2026-99-99，应为 yyyy-MM-dd（例如 2026-11-09）" }
```

`detail` 缺少 `form` 参数同样返回 400。

> 旧版用正则只校验「4-2-2 位数字」，`2026-99-99` 会一路传到 SQL，格式不对还会静默回退到今天，看起来像「今天的数据」。现在改成 `LocalDate.parse` 严格解析，错就是错。

### summary 关键结构

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

`total.bad` 统计的是**异常格数**，不是异常记录条数。

### 缓存与刷新

`summary` 默认走 5 秒缓存：同一日期 5 秒内重复请求只查一次库，多个人同时打开页面也只查一次。页面上的「刷新」按钮带 `force=1` 绕过缓存，主动刷新永远是实时数据；返回里的 `generatedAt` 是数据生成时间，页面显示成「数据 15:34:05」，缓存命中时一眼能看出来。

`detail` 和 `config` 不走缓存，点开明细永远是实时数据。`config` 会逐表做列校验和值域抽样，一次大约 9 条小查询，属于诊断接口，不要拿去轮询。

## 五、页面

- 支持 `?date=2026-11-09` 直接打开指定日期，方便对照测试数据。
- 页面里的接口前缀由当前地址自动推导，部署在 `/ec` 等上下文下同样可用（地址必须以 `/page` 结尾）。
- 刷新按钮在请求未返回时禁用，挡住连点；日期切换不受这个限制。
- 汇总和明细都带请求序号，只有最新一次请求的结果会落到页面。加载中切日期、连点两个格子，都不会出现「旧数据覆盖新数据」。
- 接口返回非 JSON（登录失效、被网关拦截）时，页面会提示「可能登录已失效或被网关拦截」，不会只显示一个空白。

## 六、部署

1. 编译本模块：

   ```bash
   javac -encoding UTF-8 -source 1.8 -target 1.8 \
     -cp "F:/fwLib/*" -d out/production/check-board $(find src -name "*.java")
   ```

   依赖目录里必须有 `weaver-ecology-3.28-*.jar`、`jersey-bundle-1.19.1.jar`、`fastjson-*.jar` 和 **`dt-common.jar`**。`dt-common.jar` 缺了会在编译期报 `com.dt.common.util.LogUtil` 找不到，运行期报 `NoClassDefFoundError`。

2. 把编译输出里的 `com` 目录整体覆盖到 `{weaver.root}/classbean/`，确认四个位置存在：

   ```
   classbean/com/api/checkboard/BoardApi.class
   classbean/com/dt/checkboard/config/FormConfig.class
   classbean/com/dt/checkboard/checkboard.json
   classbean/com/dt/checkboard/index.html
   ```

   IDE 有时不会把 `src` 下的非 java 文件复制到输出目录，那就手工把 `checkboard.json` 和 `index.html` 从 `src/com/dt/checkboard/` 拷过去。

3. 把 `dt-common.jar` 放到 E9 的 lib 目录（和 `weaver-ecology-*.jar` 同级）。它提供 `LogUtil`，本项目只用到这一个类。

4. 执行 `table_sql/checkboard_index.sql` 补索引。索引名重复（1061）说明已经建过，忽略即可；泛微建模重建表可能丢索引，重建后要重跑。

5. 重启泛微（Java 类变更需要重启；之后只改 JSON 就不用重启了）。

6. 验证：

   - `http://<host>:9083/api/checkboard/config` 应是 JSON 且 `"errors": []`，每张表 `columnsOk: true`
   - `http://<host>:9083/api/checkboard/page?date=2026-11-09` 应打开页面
   - 若整套应用部署在 `/ec` 上下文下，两个地址前面都要加 `/ec`
   - 切到 `2026-11-09`：焊机应显示 1 号、2 号两格已检（3 号、4 号未检），其中 2 号有三条记录但仍只算一格；叉车和产线当天无记录

## 七、排障

| 现象 | 先看哪 |
|---|---|
| 页面顶部黄色横幅 | 配置校验没过，按文字改 JSON |
| 某张表显示「查询失败」 | 表名或字段名写错，或该表没权限。它的应检格仍计入分母，会显示为未检 |
| 某张表一直 0 格 | `/config` 的 `columnsOk`，再看当天表里到底有没有数据 |
| 应检 9 格但只显示 8 格 | 某张表单配置被校验拒绝，看 `configErrors`；分母会跟着变小，页面可能显示很高的完成率 |
| 接口返回 400 | `date` 不是合法的 `yyyy-MM-dd`，看返回里的 `error` |
| 格子显示 ×2 / ×3 | 当天有多条记录，正常，不影响判定 |
| 「未登记编号」不为 0 | 表单里出现了配置外的单位值，对照 `unexpectedUnits` 补 `units` |
| 异常记录没列出具体项 | 该表 `items` 没配，或项的值不在 `normal` 白名单里；产线的 label 目前是序号占位 |
| 异常项下面没有备注 | 该项没配 `remarkField` |
| 照片不显示 / 显示裂图 | 直接打开 `/api/checkboard/image?id=<docid>`，404 说明 id 或字段不对，500 看日志 |
| 图片 404，但 imagefile 里查得到同号记录 | 那个号在 imagefile 里是别的文件；业务表存的是 docid，按 `DocImageFile.docid` 查真正的 imagefileid |
| 照片返回 401/403 | 文件地址需要登录态或加密参数，换用 `/api/checkboard/image?id=` 代理 |

**数据源健康**：如果某张表连续两天 0 条记录，先怀疑表单链接、字段改名、权限变化，而不是「大家都不点检」。看板本身不应该把「数据源断了」显示成「完成率 0%」；目前只能靠 `/config` 的值域抽样和历史记录人工判断，自动检测还没做。

## 八、设计取舍

- **白名单判异常**：不在 `normal` 清单里的值才算异常。旧版用黑名单 + `contains`，「无异常」会被误判成异常。
- **聚合下推**：每张表一条 `group by`，返回行数等于格数而不是记录数；明细才查记录。
- **配置即契约**：新增一张表只改 JSON，不动代码；代价是配置本身要校验、要维护，写错了页面必须看得见。
- **错误可见**：配置校验失败保留上一版并出横幅；查询失败的表单仍计入应检格，不会让完成率虚高。

## 九、已知限制与待办

- 只按「点检日期」字段归日，不猜夜班跨零点。填单人把生产日期填到哪一天，就算哪一天。
- 焊机表单截图里设备编号有 0~4 五个选项，配置按 4 台（0~3）。若真有人选 4，会以「未登记编号」提示，不会算进应检格。
- 1111 的合格/不良数量没有接入指标。这张表同时承载质量数据，完成率只看「有没有自检」，产量与不良率另行统计。
- 产线 10 个点检项的字段是 013~022 连续列推断的，label 是序号占位；拿到表单原文后替换 label，并确认 `field027` / `field035` / `field038` 是否也需要接入。
- 叉车 14 项的 `remarkField` / `imageField` 需要按属性面板核对一次（见「当前三张表的映射」）。
- `/config` 的 `columnsOk` 只校验 date/time/inspector/result/unit 五个字段，`items` / `images` / `remarkField` 写错不会在 `/config` 报出来，要到明细页才暴露。待办：把这些字段也加进 `checkColumns`。
- 没有节假日/停产日历。周末、停产日会一直显示 0/9；如果要区分「没人点检」和「今天不该点检」，需要加应检日配置。
- 补录仍算完成，只做标记。如果要统计「当天按时完成率」，需要把 `late` 从 `done` 里剔出去或单独出一列。
- 图片走 `/api/checkboard/image` 代理，按当前要求不做鉴权、不加缓存；知道 docid 就能拉图，且能读到 `imagefile` 里任意 id 的文件。待办：加业务表白名单和大小限制。
- 明细最多返回 200 条，超过只显示最近 200 条。
- 时间字段是 varchar，`MAX()` 按字符串比较，依赖 `HH:mm` 零填充；如果表单会存 `9:05` 这种，最晚时间会不准。
- 页面没有自动刷新，壁挂场景需要手动点「刷新」。
- 没有 Maven/Gradle，编译依赖 `F:/fwLib` 这个 IDEA 全局库；换机器或接 CI 需要把依赖目录固化下来。
