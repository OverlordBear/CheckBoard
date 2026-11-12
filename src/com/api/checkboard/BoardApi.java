package com.api.checkboard;

import com.alibaba.fastjson.JSONObject;
import com.dt.checkboard.service.BoardService;
import com.dt.common.util.LogUtil;
import weaver.conn.RecordSet;
import weaver.file.ImageFileManager;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 点检看板接口。
 * <ul>
 *   <li>GET /api/checkboard/summary?date=yyyy-MM-dd</li>
 *   <li>GET /api/checkboard/detail?form=&amp;date=&amp;unit=</li>
 *   <li>GET /api/checkboard/config</li>
 *   <li>GET /api/checkboard/page</li>
 * </ul>
 */
@Path("checkboard")
public class BoardApi {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final BoardService service = new BoardService();

    @GET
    @Path("/summary")
    @Produces(MediaType.APPLICATION_JSON)
    public String summary(@QueryParam("date") String date, @QueryParam("force") String force) {
        String day = normalize(date);
        boolean fresh = "1".equals(force) || "true".equalsIgnoreCase(force);
        try {
            return service.summary(day, fresh).toString();
        } catch (Throwable t) {
            LogUtil.error("看板概况查询失败 date=" + day, t);
            return fail(t);
        }
    }

    @GET
    @Path("/detail")
    @Produces(MediaType.APPLICATION_JSON)
    public String detail(@QueryParam("form") String form,
                         @QueryParam("date") String date,
                         @QueryParam("unit") String unit) {
        String day = normalize(date);
        if (form == null || form.trim().isEmpty()) {
            JSONObject res = new JSONObject();
            res.put("success", false);
            res.put("error", "缺少 form 参数");
            return res.toString();
        }
        try {
            return service.detail(form.trim(), day, unit == null ? "" : unit.trim()).toString();
        } catch (Throwable t) {
            LogUtil.error("看板明细查询失败 form=" + form + " date=" + day, t);
            return fail(t);
        }
    }

    @GET
    @Path("/config")
    @Produces(MediaType.APPLICATION_JSON)
    public String config() {
        try {
            return service.config().toString();
        } catch (Throwable t) {
            LogUtil.error("看板配置自检失败", t);
            return fail(t);
        }
    }

    /**
     * 点检图片代理：按 docid（即 imagefileid）直接读图。
     *
     * <p>不拼 E9 的图片查看地址——那个 URL 结构会在渲染时加密，库里只有数字 id，
     * 拼不出来；这里直接读字节流，绕开加密参数和版本差异。目前不做鉴权，也不加缓存。</p>
     */
    @GET
    @Path("/image")
    public Response image(@QueryParam("id") Integer id) {
        if (id == null || id <= 0) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        InputStream in = null;
        try {
            in = ImageFileManager.getInputStreamById(id);
            if (in == null) {
                // 建模表单的图片字段存的是 DocImageFile 的 id 或 docid，需要再解析一层
                int imageFileId = resolveImageFileId(id);
                if (imageFileId > 0) {
                    in = ImageFileManager.getInputStreamById(imageFileId);
                }
            }
            if (in == null) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            byte[] data = readAll(in);
            if (data.length == 0) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            return Response.ok(data)
                    .type(contentType(data))
                    .header("Cache-Control", "no-store")
                    .build();
        } catch (Throwable t) {
            LogUtil.error("读取点检图片失败 id=" + id, t);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Exception ignore) {
                    // ignore
                }
            }
        }
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            bos.write(buf, 0, n);
        }
        return bos.toByteArray();
    }

    /**
     * 把建模图片字段里的值解析成 imagefile 的 id。
     *
     * <p>field 里存的是 DocImageFile.docid（不是 imagefile.id），直接 getInputStreamById
     * 取不到。先按 docid 查，再兜底按 DocImageFile.id 查。</p>
     */
    private static int resolveImageFileId(int id) {
        try {
            RecordSet rs = new RecordSet();
            rs.executeQuery("SELECT imagefileid FROM DocImageFile WHERE docid = ? AND imagefileid > 0"
                    + " ORDER BY versionId DESC", id);
            int imageFileId = readImageFileId(rs);
            if (imageFileId > 0) {
                return imageFileId;
            }
            rs.executeQuery("SELECT imagefileid FROM DocImageFile WHERE id = ? AND imagefileid > 0"
                    + " ORDER BY versionId DESC", id);
            return readImageFileId(rs);
        } catch (Exception e) {
            LogUtil.error("解析点检图片 id 失败 id=" + id, e);
            return 0;
        }
    }

    private static int readImageFileId(RecordSet rs) {
        return rs.next() ? rs.getInt("imagefileid") : 0;
    }

    private static String contentType(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (b.length >= 4 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return "image/png";
        }
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "image/webp";
        }
        if (b.length >= 4 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8') {
            return "image/gif";
        }
        if (b.length >= 2 && b[0] == 'B' && b[1] == 'M') {
            return "image/bmp";
        }
        return "application/octet-stream";
    }

    @GET
    @Path("/page")
    @Produces("text/html;charset=UTF-8")
    public Response page() {
        try (InputStream in = BoardApi.class.getResourceAsStream("/com/dt/checkboard/index.html")) {
            if (in == null) {
                return Response.ok("<h3>缺少页面文件 classbean/com/dt/checkboard/index.html</h3>")
                        .type("text/html; charset=UTF-8").build();
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            return Response.ok(new String(bos.toByteArray(), StandardCharsets.UTF_8))
                    .type("text/html; charset=UTF-8")
                    .header("Cache-Control", "no-cache")
                    .build();
        } catch (Exception e) {
            LogUtil.error("读取看板页面失败", e);
            return Response.ok("<h3>读取页面失败: " + e.getMessage() + "</h3>")
                    .type("text/html; charset=UTF-8").build();
        }
    }

    private String normalize(String date) {
        if (date == null || !date.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return LocalDate.now().format(DATE);
        }
        return date;
    }

    private String fail(Throwable t) {
        JSONObject res = new JSONObject();
        res.put("success", false);
        res.put("error", t.getMessage() == null ? t.toString() : t.getMessage());
        return res.toString();
    }
}
