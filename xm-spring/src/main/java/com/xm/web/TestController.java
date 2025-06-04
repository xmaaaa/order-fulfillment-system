package com.xm.web;

import com.xm.scope.RequestBean;
import com.xm.scope.SessionBean;
import com.xm.service.TestService;
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
    private TestService testService;

    @Autowired
    private RequestBean requestBean;

    @Autowired
    private SessionBean sessionBean;

    @GetMapping("test")
    public String test(){
        return testService.test();
    }

    @GetMapping("testScope")
    public String testScope() {
        requestBean.test();
        sessionBean.test();
        return "RequestBean: " + requestBean.hashCode()
                + ", SessionBean: " + sessionBean.hashCode();
    }
}
