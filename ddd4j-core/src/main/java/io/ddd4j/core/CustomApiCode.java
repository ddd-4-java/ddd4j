package io.ddd4j.core;

import io.ddd4j.core.constant.Constants;

/**
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public interface CustomApiCode {

    int getCode();

    String getReason();

    default String getStatus() {
        return Constants.RT_SUCCESS;
    }

    ;

}
