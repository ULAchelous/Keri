package io.ula.keri.network.packet;

import com.google.gson.JsonObject;

import java.util.List;

public class C2SHelloPacket implements Packet {
    private String type = "hello";
    private List<JsonObject> tools;
    public C2SHelloPacket(List<JsonObject> tools){
        this.tools = tools;
    }

    @Override
    public JsonObject build() {
        JsonObject result = new JsonObject();
        result.add("");
    }
}
