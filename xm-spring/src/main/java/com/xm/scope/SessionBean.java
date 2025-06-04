package com.xm.scope;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;

/**
 * Session作用域
 *
 * @author xm
 * @see RequestBean  操作 HttpSession 而不是 HttpServletRequest
 */
@Component
@Scope(value = WebApplicationContext.SCOPE_SESSION, proxyMode = ScopedProxyMode.TARGET_CLASS)
public class SessionBean {
    private static final Logger logger = LoggerFactory.getLogger(SessionBean.class);

    public SessionBean() {
        logger.info("🌕 创建了 SessionBean 实例");
    }

    public void test() {
        logger.info("调用了 test 方法");
    }
}
