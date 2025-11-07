package com.xm.web;

import com.xm.scope.RequestBean;
import com.xm.scope.SessionBean;
import com.xm.service.TestService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author hongwan
 * @date 2023/1/17
 */
@RestController
public class TestController {

    @Autowired
    private RequestBean requestBean;

    @Autowired
    private SessionBean sessionBean;

    @Autowired
    private ObjectProvider<TestService> myServiceProvider;

    @GetMapping("test")
    public String test() throws Exception {
        // 如果有 MyService，就用它；没有不会抛异常，返回 null
        TestService testService = myServiceProvider.getIfAvailable();
        if (testService != null) {
            return testService.test();
        } else {
            System.out.println("MyService bean not available");
            throw new Exception("MyService bean not available");
        }

    }

    @GetMapping("testScope")
    public String testScope() {
        requestBean.test();
        sessionBean.test();
        return "RequestBean: " + requestBean.hashCode() + ", SessionBean: " + sessionBean.hashCode();
    }
}
