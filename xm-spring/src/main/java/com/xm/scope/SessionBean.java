package com.xm.scope;

import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;

/**
 * Session作用域
 * @see RequestBean  操作 HttpSession 而不是 HttpServletRequest
 *
 * @author xm
 */
@Component
@Scope(value = WebApplicationContext.SCOPE_SESSION, proxyMode = ScopedProxyMode.TARGET_CLASS)
public class SessionBean {
    public SessionBean() {
        System.out.println("🌕 创建了 SessionBean 实例");
    }
}
