package com.bapel_slimefun_mod;

import org.junit.jupiter.api.Test;

public class ScratchTest {

    @Test
    public void testPayloadTypeRegistry() {
        try {
            Class<?> clazz = Class.forName("net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry");
            for (java.lang.reflect.Method m : clazz.getMethods()) {
                System.out.println("PayloadTypeRegistry method: " + m.getName() + " -> " + m.getReturnType().getName() + " parameters: " + java.util.Arrays.toString(m.getParameterTypes()));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
