package io.ddd4j.core;

public interface CustomApiCode {

    int getCode();

    String getReason();

    default String getStatus() {
        return Constants.RT_SUCCESS;
    }

    ;

}
