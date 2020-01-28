package com.lfls.hotfix.client;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

import java.nio.charset.StandardCharsets;


/**
 * @author lingfenglangshao
 * @since 19/01/2020
 */
public class ClientWriteHandler extends MessageToByteEncoder<String> {

    @Override
    protected void encode(ChannelHandlerContext ctx, String msg, ByteBuf out) throws Exception {
        ByteBuf head = ctx.alloc().buffer(4);
        ByteBuf body = Unpooled.copiedBuffer(msg, StandardCharsets.UTF_8);
        head.writeInt(body.readableBytes());
        out.writeBytes(head);
        out.writeBytes(body);
    }
}
