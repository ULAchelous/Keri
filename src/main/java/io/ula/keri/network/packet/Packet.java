package io.ula.keri.network.packet;

import com.google.gson.JsonObject;

public interface Packet {
    public JsonObject build();
}
