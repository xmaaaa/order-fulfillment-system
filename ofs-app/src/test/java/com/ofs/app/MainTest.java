package com.ofs.app;

import com.ofs.app.service.TestService;
import com.ofs.app.web.OfsAppApplication;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * @author XM
 * @date 2025/5/18
 */
@SpringBootTest(classes = OfsAppApplication.class, properties = {
        // 单测不连 Jaeger：无 endpoint 时不注册 OTLP exporter（与主配置合并，不覆盖 seata 等）
        "management.otlp.tracing.endpoint="
})
public class MainTest {

    static {
        System.setProperty("csp.sentinel.log.dir", "target/sentinel");
    }

    @Autowired
    private TestService testService;

    @Autowired
    private ApplicationContext context;

    @Test
    void defaultContextDoesNotLoadKafkaTestListeners() {
        context.getBeansOfType(KafkaListenerEndpointRegistry.class).values().forEach(registry ->
                Assertions.assertTrue(registry.getListenerContainers().isEmpty(),
                        "Kafka is disabled: test listener configurations must not leak into this context"));
    }

    @Test
    public void test() {
        String test = testService.test();
        Assertions.assertEquals("test", test);
    }
}
