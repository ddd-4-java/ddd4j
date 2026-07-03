package io.ddd4j.data.crypto.domain.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 弗兰科信息签名响应 VO
 * <p>封装远程签名接口返回的响应数据</p>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FlkSecSignResponseVO {

    /**
     * 200:成功
     *
     * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
     */
    @JsonProperty("code")
    private int code;
    /**
     * 成功或失败的提示信息
     */
    @JsonProperty("msg")
    private String msg;
    /**
     * 解密后的数据
     */
    @JsonProperty("data")
    private String data;

}
