package io.ddd4j.extension.excel.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ExcelHttpKit} 单元测试（用 Spring 提供的 {@link MockHttpServletResponse}）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class ExcelHttpKitTest {

    @Test
    void download_should_set_correct_headers_and_body() throws UnsupportedEncodingException {
        // given
        MockHttpServletResponse response = new MockHttpServletResponse();
        String filename = "订单.xlsx";
        byte[] bytes = "fake-xlsx-content".getBytes(StandardCharsets.UTF_8);

        // when
        ExcelHttpKit.download(response, filename, bytes);

        // then
        assertThat(response.getContentType())
                .startsWith(ExcelAttachment.CONTENT_TYPE_XLSX);
        assertThat(response.getHeader("Content-Disposition"))
                .startsWith("attachment;filename*=utf-8''")
                .contains(URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20"));
        assertThat(response.getContentLength()).isEqualTo(bytes.length);
        assertThat(response.getContentAsByteArray()).isEqualTo(bytes);
    }

    @Test
    void download_with_custom_attachment_should_use_csv_content_type() {
        // given
        MockHttpServletResponse response = new MockHttpServletResponse();
        byte[] bytes = "a,b,c\n1,2,3".getBytes(StandardCharsets.UTF_8);

        // when
        ExcelHttpKit.download(response, ExcelAttachment.csv("data.csv", StandardCharsets.UTF_8), bytes);

        // then
        assertThat(response.getContentType()).startsWith("text/csv");
        assertThat(response.getHeader("Content-Disposition")).contains("data.csv");
    }

    @Test
    void validate_should_reject_empty_file() {
        // using MockMultipartFile with empty content
        org.springframework.mock.web.MockMultipartFile empty =
                new org.springframework.mock.web.MockMultipartFile(
                        "file", "empty.xlsx", null, new byte[0]);

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                ExcelHttpKit.validate(empty, 50, List.of(".xlsx")))
                .isInstanceOf(io.ddd4j.core.exception.BizRuntimeException.class);
    }

    @Test
    void validate_should_reject_invalid_extension() {
        org.springframework.mock.web.MockMultipartFile txt =
                new org.springframework.mock.web.MockMultipartFile(
                        "file", "not_excel.txt", "text/plain", new byte[]{1, 2, 3});

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                ExcelHttpKit.validate(txt, 50, List.of(".xlsx", ".xls")))
                .isInstanceOf(io.ddd4j.core.exception.BizRuntimeException.class);
    }

    @Test
    void validate_should_accept_valid_xlsx() {
        org.springframework.mock.web.MockMultipartFile ok =
                new org.springframework.mock.web.MockMultipartFile(
                        "file", "orders.xlsx",
                        ExcelAttachment.CONTENT_TYPE_XLSX, new byte[]{1, 2, 3, 4, 5});

        // should not throw
        ExcelHttpKit.validate(ok, 50, List.of(".xlsx", ".xls"));
        assertThat(ok.getOriginalFilename()).isEqualTo("orders.xlsx");
    }

    @Test
    void attachment_contentDisposition_should_encode_chinese() {
        // given
        ExcelAttachment a = ExcelAttachment.xlsx("用户列表.xlsx");

        // when
        String cd = a.contentDisposition();

        // then
        assertThat(cd).startsWith("attachment;filename*=utf-8''");
        // %E7%94%A8 = '用' UTF-8 编码首字节
        assertThat(cd).contains("%E7%94%A8");
    }
}
