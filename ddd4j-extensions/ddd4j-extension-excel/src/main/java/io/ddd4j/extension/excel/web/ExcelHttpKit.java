package io.ddd4j.extension.excel.web;

import io.ddd4j.core.exception.BizRuntimeException;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/**
 * Excel Web 工具集（下载 / 上传）。
 *
 * <p>封装 {@link HttpServletResponse} 文件下载与 {@link MultipartFile} 文件上传的样板代码：
 * <ul>
 *   <li>下载：自动设置 Content-Type / Content-Disposition / Content-Length</li>
 *   <li>上传：返回 {@link InputStream}（由调用方决定如何解析）</li>
 * </ul>
 *
 * <p><b>注意</b>：本类依赖 {@code jakarta.servlet-api} 与 {@code spring-web}（仅在调用相应方法时需要）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public final class ExcelHttpKit {

    private ExcelHttpKit() {
    }

    /**
     * 下载 xlsx 字节数组到响应。
     *
     * @param response HTTP 响应
     * @param filename 文件名（含 .xlsx 扩展名）
     * @param bytes    xlsx 字节
     */
    public static void download(HttpServletResponse response, String filename, byte[] bytes) {
        download(response, ExcelAttachment.xlsx(filename), bytes);
    }

    /**
     * 下载自定义附件类型的字节数组。
     *
     * @param response  HTTP 响应
     * @param attachment 附件元数据
     * @param bytes      xlsx 字节
     */
    public static void download(HttpServletResponse response, ExcelAttachment attachment, byte[] bytes) {
        response.reset();
        response.setContentType(attachment.contentTypeWithCharset());
        response.setCharacterEncoding(attachment.getCharset().name());
        response.setHeader("Content-Disposition", attachment.contentDisposition());
        response.setHeader("Access-Control-Expose-Headers", "Content-Disposition");
        response.setContentLength(bytes.length);
        try (OutputStream out = response.getOutputStream()) {
            out.write(bytes);
            out.flush();
        } catch (IOException e) {
            throw new BizRuntimeException(500, "excel.download.failed", e);
        }
    }

    /**
     * 下载 xlsx 字节并支持失败回 JSON（推荐 Web 场景）。
     *
     * <p>调用方在外层 try-catch 包裹即可：失败时通过抛 {@link BizRuntimeException} 中断；
     * 本方法不会自行 reset 响应。如需"成功返回 Excel / 失败返回 JSON"的混合模式，
     * 由调用方在 catch 块中 {@code response.reset()} 后写 JSON。
     *
     * @param response HTTP 响应
     * @param filename 文件名
     * @param bytes    xlsx 字节
     * @param autoCloseStream 是否在写完后自动关闭流（默认 true）
     */
    public static void download(HttpServletResponse response, String filename, byte[] bytes,
                                boolean autoCloseStream) {
        response.reset();
        response.setContentType(ExcelAttachment.CONTENT_TYPE_XLSX);
        response.setCharacterEncoding("UTF-8");
        ExcelAttachment attachment = ExcelAttachment.xlsx(filename);
        response.setHeader("Content-Disposition", attachment.contentDisposition());
        response.setHeader("Access-Control-Expose-Headers", "Content-Disposition");
        response.setContentLength(bytes.length);
        try {
            OutputStream out = response.getOutputStream();
            out.write(bytes);
            out.flush();
            if (autoCloseStream) {
                out.close();
            }
        } catch (IOException e) {
            throw new BizRuntimeException(500, "excel.download.failed", e);
        }
    }

    /**
     * 从上传文件中获取输入流（用于后续 ExcelKit.importExcel）。
     *
     * <p>调用方负责关闭返回的 InputStream（建议 try-with-resources）。
     *
     * @param file 上传文件
     * @return 输入流
     */
    public static InputStream upload(MultipartFile file) {
        try {
            return file.getInputStream();
        } catch (IOException e) {
            throw new BizRuntimeException(400, "excel.upload.failed", e);
        }
    }

    /**
     * 校验上传文件大小与扩展名。
     *
     * @param file       上传文件
     * @param maxMB      最大体积（MB）
     * @param extensions 允许的扩展名（如 {@code List.of(".xlsx", ".xls")}）
     * @throws BizRuntimeException 校验失败
     */
    public static void validate(MultipartFile file, int maxMB, List<String> extensions) {
        if (file == null || file.isEmpty()) {
            throw new BizRuntimeException(400, "excel.upload.empty");
        }
        long maxBytes = (long) maxMB * 1024 * 1024;
        if (file.getSize() > maxBytes) {
            throw new BizRuntimeException(400, "excel.upload.too.large",
                    file.getSize(), maxBytes);
        }
        String original = file.getOriginalFilename();
        if (original == null) {
            throw new BizRuntimeException(400, "excel.upload.no.filename");
        }
        String lower = original.toLowerCase();
        boolean ok = extensions == null || extensions.isEmpty()
                || extensions.stream().anyMatch(lower::endsWith);
        if (!ok) {
            throw new BizRuntimeException(400, "excel.upload.invalid.extension", original);
        }
    }
}
