package com.lulu.luluaiagent.model.runtime;

public enum ModelRoute {
    COACH("coach"),
    AGENT("agent"),
    FAST("fast"),
    MEMORY("memory");

    private final String id;

    ModelRoute(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }
}
