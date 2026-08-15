package com.comm.pingscore;

import java.util.Objects;
import java.util.concurrent.Executor;

/** Dispatches optional score feedback away from the UI event handler. */
public final class FeedbackDispatcher {
    private final Executor executor;

    public FeedbackDispatcher(Executor executor) {
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    public void dispatch(Runnable effect) {
        if (effect != null) {
            executor.execute(effect);
        }
    }
}
