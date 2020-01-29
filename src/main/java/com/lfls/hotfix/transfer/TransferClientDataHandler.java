package com.lfls.hotfix.transfer;

import com.lfls.hotfix.server.Server;
import com.lfls.hotfix.server.ServerReadHandler;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.nio.charset.StandardCharsets;

/**
 * @author lingfenglangshao
 * @since 28/01/2020
 */
public class TransferClientDataHandler extends ChannelInboundHandlerAdapter {

    private Channel channel;

    public TransferClientDataHandler(Channel channel){
        this.channel = channel;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        Server.getInstance().addTransferDataChannel(channel.id().asLongText(), ctx.channel());
        String newChannelId = Server.getInstance().getNewChannelIdByOldChannelId(channel.id().asLongText());
        ByteBuf newChannelIdBuf = Unpooled.copiedBuffer(newChannelId, StandardCharsets.UTF_8);
        //先注销，注销以后该channel上面不再读取任何数据，但写数据不影响
        channel.deregister().addListener(future -> {
            if (future.isSuccess()){
                //发送读余量数据
                ByteBuf remain = ((ServerReadHandler) channel.pipeline().get("decode")).getRemainData();
                ByteBuf readRemain = remain.retainedSlice();
                //1: read/write
                //4: newChannelId length
                //length: newChannelId
                //4: remain data length
                //length : remain data
                ByteBuf buffer = ctx.alloc().buffer(1 + 4 + newChannelIdBuf.readableBytes() + 4 + readRemain.readableBytes());
                buffer.writeByte(0);
                buffer.writeInt(newChannelIdBuf.readableBytes());
                buffer.writeBytes(newChannelIdBuf);
                buffer.writeInt(readRemain.readableBytes());
                buffer.writeBytes(readRemain);

                ctx.writeAndFlush(buffer).addListener(future1 -> {
                    if (future1.isSuccess()){
                        channel.close().addListener(future2 -> {
                            if (!future2.isSuccess()){
                                future2.cause().printStackTrace();
                            }
                        });
                    }else {
                        future1.cause().printStackTrace();
                    }
                });
            }else {
                future.cause().printStackTrace();
            }
        });

    }

}
