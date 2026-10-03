package com.lulu.luluaiagent.agent;

import java.util.Optional;

@FunctionalInterface
public interface IntentClassifier {
    Optional<AgentRequestRouter.Route> classify(String message);
}
