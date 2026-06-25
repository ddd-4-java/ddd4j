/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 */
package io.ddd4j.boot.cmpt.satoken.handler;

import cn.dev33.satoken.exception.SaTokenException;
import io.ddd4j.boot.core.ApiRestResponse;
import io.ddd4j.boot.core.exception.BaseExceptionHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

@ControllerAdvice
@ResponseBody
@Slf4j
public class SaTokenExceptionHandler extends BaseExceptionHandler {

    /**
     * 401 (Unauthorized)
     */
    @ExceptionHandler({SaTokenException.class})
    public ResponseEntity<ApiRestResponse<String>> accessDeniedException(SaTokenException ex) {
        this.logException(ex);
        return new ResponseEntity<>(ApiRestResponse.of(ex.getCode(), ex.getMessage()), HttpStatus.UNAUTHORIZED);
    }

}
