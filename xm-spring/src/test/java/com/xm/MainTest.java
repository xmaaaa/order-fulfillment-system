package com.xm;

import com.xm.service.TestService;
import com.xm.web.XmBootStarter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * @author XM
 * @date 2025/5/18
 */
@SpringBootTest(classes = XmBootStarter.class, properties = {
        // 单测不连 Jaeger：无 endpoint 时不注册 OTLP exporter（与主配置合并，不覆盖 seata 等）
        "management.otlp.tracing.endpoint="
})
public class MainTest {

    static {
        System.setProperty("csp.sentinel.log.dir", "target/sentinel");
    }

    @Autowired
    private TestService testService;

    @Test
    public void test() {
        String test = testService.test();
        Assertions.assertEquals("test", test);
    }
}
