/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 */
package io.ddd4j.core.exception;


import lombok.Getter;

import java.io.Serial;
import java.util.Objects;

@Getter
public class PayloadExceptionEvent implements java.io.Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
    private final Object source;
    private final Exception payload;

    /**
     * Create a new PayloadExceptionEvent.
     *
     * @param source  the object on which the event initially occurred (never {@code null})
     * @param payload the Exception object (never {@code null})
     */
    public PayloadExceptionEvent(Object source, Exception payload) {
        this.source = source;
        Objects.requireNonNull(payload, "Payload must not be null");
        this.payload = payload;
    }

}
