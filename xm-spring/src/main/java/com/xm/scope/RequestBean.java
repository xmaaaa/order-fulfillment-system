package com.xm.scope;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;

/**
 * 在一次request中的作用域
 * <p>
 * 用户请求1进来
 *  └─ DispatcherServlet
 *      └─ Spring 从 request.getAttribute("myBean") 获取 Bean
 *          ├─ 没有 -> 创建 -> 设置回 request.setAttribute(...)
 *          └─ 有 -> 直接复用
 * 请求结束，request Bean 自动消失
 *
 * @author xm
 */
@Component
@Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
public class RequestBean {
    private static final Logger logger = LoggerFactory.getLogger(RequestBean.class);

    public RequestBean() {
        logger.info("🚀 创建了 RequestBean 实例: {}", this);
    }

    public void test() {
        logger.info("调用了 test 方法");
    }
}
