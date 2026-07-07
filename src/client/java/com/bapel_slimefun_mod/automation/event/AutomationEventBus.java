package com.bapel_slimefun_mod.automation.event;

import java.util.*;
import java.util.function.Consumer;

public final class AutomationEventBus {

    private static final AutomationEventBus INSTANCE = new AutomationEventBus();
    private final Map<AutomationEvent, List<Consumer<Object>>> listeners = new EnumMap<>(AutomationEvent.class);

    private AutomationEventBus() {}

    public static AutomationEventBus get() { return INSTANCE; }

    @SuppressWarnings("unchecked")
    public <T> void subscribe(AutomationEvent event, Consumer<T> listener) {
        listeners.computeIfAbsent(event, k -> new ArrayList<>())
                 .add(obj -> listener.accept((T) obj));
    }

    public void publish(AutomationEvent event, Object payload) {
        List<Consumer<Object>> list = listeners.get(event);
        if (list != null) {
            for (Consumer<Object> l : list) {
                l.accept(payload);
            }
        }
    }

    public void publish(AutomationEvent event) {
        publish(event, null);
    }
}
