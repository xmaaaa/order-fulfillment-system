package com.xm.scenario.transaction.tcc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 学习用：TCC 协调器，按注册顺序执行 Try -> Confirm，失败则逆序 Cancel。
 */
public class SimpleTccCoordinator implements TccCoordinator {

    private final Map<String, TccParticipant> participants = new LinkedHashMap<>();

    @Override
    public void registerParticipant(String name, TccParticipant participant) {
        participants.put(name, participant);
    }

    @Override
    public boolean execute(TccContext context) {
        List<String> tried = new ArrayList<>();
        for (Map.Entry<String, TccParticipant> e : participants.entrySet()) {
            if (!e.getValue().tryPhase(context)) {
                for (int i = tried.size() - 1; i >= 0; i--) {
                    participants.get(tried.get(i)).cancel(context);
                }
                return false;
            }
            tried.add(e.getKey());
        }
        for (Map.Entry<String, TccParticipant> e : participants.entrySet()) {
            if (!e.getValue().confirm(context)) {
                for (int i = tried.size() - 1; i >= 0; i--) {
                    participants.get(tried.get(i)).cancel(context);
                }
                return false;
            }
        }
        return true;
    }
}
