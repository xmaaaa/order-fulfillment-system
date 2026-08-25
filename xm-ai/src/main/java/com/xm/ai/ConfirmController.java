package com.xm.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 人工确认入口：agent 提议的敏感操作，必须由人调用这里才真正执行。
 * 这是 human-in-the-loop 的"人"这一环。
 */
@RestController
public class ConfirmController {

    private static final Logger log = LoggerFactory.getLogger(ConfirmController.class);

    private final PendingActionStore pendingStore;

    public ConfirmController(PendingActionStore pendingStore) {
        this.pendingStore = pendingStore;
    }

    @PostMapping("/confirm")
    public String confirm(@RequestParam String actionId) {
        log.info("✅ [人工确认] 执行 actionId={}", actionId);
        return pendingStore.confirm(actionId);
    }
}
