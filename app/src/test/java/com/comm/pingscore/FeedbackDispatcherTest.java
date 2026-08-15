package com.comm.pingscore;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

public class FeedbackDispatcherTest {
    @Test
    public void dispatchDoesNotRunEffectOnTheCallingThread() {
        AtomicReference<Runnable> queuedEffect = new AtomicReference<>();
        AtomicBoolean played = new AtomicBoolean(false);
        FeedbackDispatcher dispatcher = new FeedbackDispatcher(queuedEffect::set);

        dispatcher.dispatch(() -> played.set(true));

        assertFalse(played.get());
        assertNotNull(queuedEffect.get());
        queuedEffect.get().run();
        assertTrue(played.get());
    }

    @Test
    public void runImmediateExecutesEffectBeforeReturning() {
        FeedbackDispatcher dispatcher = new FeedbackDispatcher(Runnable::run);
        AtomicBoolean played = new AtomicBoolean(false);

        dispatcher.runImmediate(() -> played.set(true));

        assertTrue(played.get());
    }
}
