package com.lfls.hotfix.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.util.concurrent.TimeUnit;

/**
 * @author lingfenglangshao
 * @since 27/01/2020
 */
public class Client {

    public void start() throws Exception {
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        int count = 100;

        for (int i = 0; i < count; i++){
            Bootstrap b = new Bootstrap();
            b.group(workerGroup)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel ch) throws Exception {
                            ch.pipeline().addLast(new ClientReadHandler());
                            ch.pipeline().addLast(new ChannelOutboundHandlerAdapter(){
                                @Override
                                public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                                    ctx.writeAndFlush(msg);
                                }
                            });
                            ch.pipeline().addLast(new ClientWriteHandler());
                        }
                    })
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                    .option(ChannelOption.TCP_NODELAY, true);

            b.connect("127.0.0.1", 8989).sync();

            TimeUnit.SECONDS.sleep(2);
        }
    }

}
