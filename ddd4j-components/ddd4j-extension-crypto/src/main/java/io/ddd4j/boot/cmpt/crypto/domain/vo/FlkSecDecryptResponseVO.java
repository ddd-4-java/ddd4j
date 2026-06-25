package io.ddd4j.boot.cmpt.crypto.domain.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FlkSecDecryptResponseVO {

    /**
     * 200:成功
     */
    @JsonProperty("code")
    private int code;
    /**
     * 成功或失败的提示信息
     */
    @JsonProperty("msg")
    private String msg;
    /**
     * 分段加密时使用
     */
    @JsonProperty("iv")
    private String iv;
    /**
     * 解密后的数据
     */
    @JsonProperty("data")
    private String data;

}
