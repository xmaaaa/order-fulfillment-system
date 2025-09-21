package com.xm.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 在请求到达 DispatcherServlet 之前，以及响应返回之前, 执行范围：所有经过 Servlet 容器的请求，包括：
 * 1. 静态资源（HTML、JS、CSS、图片）
 * 2. REST API 接口
 * 3. 甚至 JSP 页面等
 * 请求 → Filter1 → Filter2 → ... → DispatcherServlet → HandlerInterceptor → Controller → Response
 *
 * @author XM
 * @date 2025/9/9
 */
@Component
public class MyFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // 初始化
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        System.out.println("MyFilter - Request URL: " + req.getRequestURI());

        // 继续调用下一个 Filter 或 DispatcherServlet
        chain.doFilter(request, response);

        // finish
    }

    @Override
    public void destroy() {
        // 销毁
    }
}
