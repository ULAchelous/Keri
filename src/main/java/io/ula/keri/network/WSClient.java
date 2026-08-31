package io.ula.keri.network;

import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;

public class WSClient {
    private enum Status{
        PREPARED,
        HAND_SHAkING,
        FAILED
    }

    private Status status = Status.PREPARED;
    public WSClient(WebSocketClientHandshaker handshaker){
       this.handshaker = handshaker;
    }
}
