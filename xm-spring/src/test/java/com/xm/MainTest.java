package com.xm;

import com.xm.service.TestService;
import com.xm.web.XmBootStarter;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * @author XM
 * @date 2025/5/18
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = XmBootStarter.class)
public class MainTest {

    @Autowired
    private TestService testService;

    @Test
    public void test() {
        String test = testService.test();
        Assertions.assertEquals("test", test);
    }
}
