package io.ula.keri.network;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakeException;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import org.apache.logging.log4j.Logger;

public class WSClientHandler extends SimpleChannelInboundHandler<Object> {
    private WebSocketClientHandshaker handshaker;
    private Logger logger;
    private ChannelPromise handshakeFuture;
    public WSClientHandler(WebSocketClientHandshaker hs, Logger logger){
        this.handshaker = hs;
        this.logger = logger;
    }
    @Override
    public void handlerAdded(ChannelHandlerContext ctx){
        handshakeFuture = ctx.newPromise();
    }
    @Override
    public void channelActive(ChannelHandlerContext ctx){
        handshaker.handshake(ctx.channel());
        logger.info("Start handshaking...");
    }
    @Override
    public void channelRead0(ChannelHandlerContext ctx,Object content) throws Exception{
        Channel channel = ctx.channel();
        if(handshaker.isHandshakeComplete()){
            try{
                handshaker.finishHandshake(channel,(FullHttpResponse) content);
                logger.info("WebSocked Client Connected");
            } catch (WebSocketClientHandshakeException e){
                logger.error("Failed to connect: handshake failed");
                handshakeFuture.setFailure(e);
            }
            return;
        }

        if(content instanceof FullHttpResponse){
            FullHttpResponse response = (FullHttpResponse) content;
            throw new IllegalStateException("Unexcepted http response:"+response.status());
        }

        WebSocketFrame frame = (WebSocketFrame) content;
        if(frame instanceof TextWebSocketFrame){

        }
    }
}
