package com.lfls.hotfix.transfer;

import com.lfls.hotfix.server.Server;
import com.lfls.hotfix.server.ServerReadHandler;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * @author lingfenglangshao
 * @since 28/01/2020
 */
public class TransferServerDataHandler extends ByteToMessageDecoder {

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        //1: type (0:read 1:write)
        //4: newChannelId length
        //length: newChannelId
        //4: remain data length
        //length : remain data

        int channelIdLength = in.getInt(in.readerIndex() + 1);
        int remainDataLength = in.getInt(1 + 4 + channelIdLength);

        if (in.readableBytes() >= 1 + 4 + channelIdLength + 4 + remainDataLength){

            byte type = in.readByte();
            channelIdLength = in.readInt();
            String newChannelId = in.toString(in.readerIndex(), channelIdLength, StandardCharsets.UTF_8);
            in.skipBytes(channelIdLength);

            int dataLength = in.readInt();
            Channel channel = TransferServer.getInstance().getChannelById(newChannelId);

            ByteBuf remainData = channel.alloc().buffer(dataLength);
            //加一次
            remainData.retain();
            in.readBytes(remainData, dataLength);
            //这里会释放一次
            out.add(remainData);

            if (type == 0){
                //处理遗留的读数据
                ServerReadHandler decode = (ServerReadHandler) channel.pipeline().get("decode");
                Class<? extends ServerReadHandler> aClass = decode.getClass();
                Field cumulation = aClass.getSuperclass().getDeclaredField("cumulation");
                cumulation.setAccessible(true);
                cumulation.set(decode, remainData);
                //存量读取数据进来以后就可以进行注册了，写数据不用等，来了以后帮忙写出去即可。
                Server.getInstance().registerChannel(channel).addListener(future -> {
                    if (!future.isSuccess()){
                        future.cause().printStackTrace();
                    }
//                    if (future.isSuccess()){
//                        new Thread(() -> {
//                            while (task.size() > 0){
//                                ByteBuf poll = null;
//                                try {
//                                    poll = task.poll(1, TimeUnit.SECONDS);
//                                    channel.writeAndFlush(poll);
//                                } catch (InterruptedException e) {
//                                    e.printStackTrace();
//                                }
//                            }
//                        });
//                    }
                });
            }else {
                if (channel.isRegistered()){
                    //处理遗留的写数据
                    channel.writeAndFlush(remainData);
                }else {
                    //TODO 注册之前收到old server未写出去的数据
//                    task.offer(remainData);
                }
            }
        }
    }
}
