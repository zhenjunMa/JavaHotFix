package com.lfls.hotfix.client;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author lingfenglangshao
 * @since 19/01/2020
 */
public class ClientReadHandler extends ByteToMessageDecoder {

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        new Thread(() -> {
            int count = 0;
            while (true){
                try {
                    count++;
                    System.out.println("request:" + count);
                    ctx.channel().write(String.valueOf(count));
                }catch (Exception e){
                    e.printStackTrace();
                }finally {
                    try {
                        TimeUnit.SECONDS.sleep(1);
                    } catch (InterruptedException e) {}
                }
            }
        }).start();
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (in.readableBytes() >= 4){
            int length = in.getInt(in.readerIndex());
            if (in.readableBytes() >= 4 + length){
                in.skipBytes(4);
                ByteBuf remain = ctx.alloc().buffer(length);
                in.readBytes(remain);
                System.out.println(remain.toString(StandardCharsets.UTF_8));
                //release
                out.add(remain);
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
    }
}
