package com.lfls.hotfix.server;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * @author lingfenglangshao
 * @since 28/01/2020
 */
public class ServerReadHandler extends ByteToMessageDecoder {

    private String name;

    public ServerReadHandler(String name){
        this.name = name;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        //迁移过来的连接不用再执行active
        System.out.println(name + " connection active!");
        Server.getInstance().addChannel(ctx.channel());
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (in.readableBytes() >= 4){
            int length = in.getInt(in.readerIndex());
            if (in.readableBytes() >= 4 + length){
                in.skipBytes(4);
                ByteBuf remain = ctx.alloc().buffer(length);
                in.readBytes(remain);
                ctx.channel().write(name + ":" + remain.toString(StandardCharsets.UTF_8));
                out.add(remain);
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
    }

    public ByteBuf getRemainData(){
        return this.internalBuffer();
    }

}
