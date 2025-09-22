package com.xm.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

/**
 * DispatcherServlet 收到请求 → HandlerMapping 找到 Handler → HandlerExecutionChain
 * 注意 triggerAfterCompletion 里面是逆序，也就是postHandle 和 afterCompletion
 * 请求 → Filter1 → Filter2 → ... → DispatcherServlet → HandlerInterceptor → Controller → Response
 *
 * @author XM
 * @date 2025/9/9
 */
public class MyInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (request.getHeader("sss") == null) {
            System.out.println("Not contains sss !!!");
        }
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {

    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {

    }
}
