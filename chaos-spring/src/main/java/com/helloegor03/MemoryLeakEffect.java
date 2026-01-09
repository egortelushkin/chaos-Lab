package com.helloegor03;

import java.util.ArrayList;
import java.util.List;

public class MemoryLeakEffect implements ChaosEffect {

    private final int bytes;

    public MemoryLeakEffect(int bytes) {
        this.bytes = bytes;
    }

    @Override
    public void apply() {
        List<byte[]> leak = new ArrayList<>();
        leak.add(new byte[bytes]); // tip: this will create a memory leak
        System.out.println("Memory leak simulated: " + bytes + " bytes");
    }
}
