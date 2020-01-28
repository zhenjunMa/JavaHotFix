package com.lfls.hotfix.server;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

import java.nio.charset.StandardCharsets;


/**
 * @author lingfenglangshao
 * @since 28/01/2020
 */
public class ServerWriteHandler extends MessageToByteEncoder<String> {

    @Override
    protected void encode(ChannelHandlerContext ctx, String msg, ByteBuf out) throws Exception {
        ByteBuf head = ctx.alloc().buffer(4);
        ByteBuf body = Unpooled.copiedBuffer(msg, StandardCharsets.UTF_8);
        head.writeInt(body.readableBytes());
        out.writeBytes(head);
        out.writeBytes(body);
    }

}
