package com.api.checkboard;

import com.alibaba.fastjson.JSONObject;
import com.dt.checkboard.service.BoardService;
import com.dt.common.util.LogUtil;

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
 *
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
