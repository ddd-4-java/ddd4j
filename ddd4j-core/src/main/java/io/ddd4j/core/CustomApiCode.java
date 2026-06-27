package io.ddd4j.core;

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
