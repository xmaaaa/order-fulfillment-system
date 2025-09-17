package com.xm.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * controller的异常处理
 *
 * @author XM
 * @date 2025/9/10
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    @ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR, reason = "异常")
    public void handleAll(Exception ex) {
        System.out.println("全局异常：" + ex.getMessage());
    }
}
