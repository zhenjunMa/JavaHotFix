package com.lfls.hotfix.server;

import com.lfls.hotfix.enums.ServerStatus;
import com.lfls.hotfix.transfer.TransferClientDataHandler;
import com.lfls.hotfix.transfer.TransferServer;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.epoll.*;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.unix.DomainSocketAddress;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.GlobalEventExecutor;

import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.*;

/**
 * @author lingfenglangshao
 * @since 27/01/2020
 */
public class Server {

    //key:old channelId  value:data transfer channel
    public final Map<String, Channel> channelMap = new ConcurrentHashMap<>();

    //key:old channelId  value:new channelId
    private final Map<String, String> channelIdMap = new ConcurrentHashMap<>();

    private final ChannelGroup channelGroup = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

    //1. 接收调用端请求
    //2. 接收热更新事件
    private EventLoopGroup bossGroup = new EpollEventLoopGroup(2);
    private EventLoopGroup workerGroup = new EpollEventLoopGroup();

    private volatile ServerStatus status = ServerStatus.NORMAL;

    private ChannelFuture serverChannelFuture;
    private ChannelFuture hotFixServerChannelFuture;

    private ChannelFuture hotFixChannelFuture;

    private static final Server sever = new Server();

    private Server(){}

    public static Server getInstance() {
        return sever;
    }

    public void start() throws Exception {
        ServerBootstrap b = new ServerBootstrap();
        b.group(bossGroup, workerGroup)
                .channel(EpollServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline().addLast("decode", new ServerReadHandler("server"));
                        ch.pipeline().addLast(new ChannelOutboundHandlerAdapter(){
                            @Override
                            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                                ByteBuf buf = (ByteBuf) msg;
                                buf.retain();
                                ctx.writeAndFlush(buf).addListener(future -> {
                                    if (future.isSuccess()){
                                        buf.release();
                                    }else {
                                        if (status == ServerStatus.HOT_FIX){
                                            //热更新中，尝试发送写失败数据给new server
                                            String oldChannelId = ctx.channel().id().asLongText();
                                            if (channelIdMap.containsKey(oldChannelId)){

                                                //1: read/write
                                                //4: newChannelId length
                                                //length: newChannelId
                                                //4: remain data length
                                                //length : remain data

                                                String newChannelId = channelIdMap.get(oldChannelId);
                                                ByteBuf newChannelIdBuf = Unpooled.copiedBuffer(newChannelId, StandardCharsets.UTF_8);

                                                Channel transferChannel = channelMap.get(oldChannelId);

                                                ByteBuf buffer = transferChannel.alloc().buffer(1 + 4 + newChannelIdBuf.readableBytes() + 4 + buf.readableBytes());

                                                buffer.writeByte(1);
                                                buffer.writeInt(newChannelIdBuf.readableBytes());
                                                buffer.writeBytes(newChannelIdBuf);
                                                newChannelIdBuf.release();

                                                buffer.writeInt(buf.readableBytes());
                                                buffer.writeBytes(buf);
                                                buf.release();

                                                transferChannel.writeAndFlush(buffer).addListener(future1 -> {
                                                    if (!future1.isSuccess()){
                                                        future1.cause().printStackTrace();
                                                    }
                                                });

                                            }else {
                                                //处于热更新状态，但找不到用于迁移数据的channel，可能是超时了？
                                                buf.release();
                                            }
                                        }else {
                                            //单纯写失败
                                            buf.release();
                                        }
                                    }
                                });
                            }
                        });
                        ch.pipeline().addLast(new ServerWriteHandler());
                    }
                })
                .option(ChannelOption.SO_BACKLOG, 128)
                .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                .option(EpollChannelOption.SO_REUSEADDR, true)
                .option(EpollChannelOption.SO_REUSEPORT, true)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                .childOption(ChannelOption.TCP_NODELAY, true);


        serverChannelFuture = b.bind(8989).sync();

        if (needHotFix()){
            status = ServerStatus.HOT_FIX;
        }else {
            //如果需要热更新，则更新完成以后再启动
            startHotFixServer();
        }
    }

    public boolean needHotFix() {
        try {
            Bootstrap b = new Bootstrap();
            b.group(workerGroup)
                    .channel(EpollDomainSocketChannel.class)
                    .handler(new ChannelInboundHandlerAdapter() {
                        @Override
                        public void channelActive(ChannelHandlerContext ctx) throws Exception {
                            //连接成功，进入hotfix状态
                            TransferServer.getInstance().start();

                            ByteBuf buf = ctx.alloc().buffer(4);
                            buf.writeInt(1);
                            ctx.writeAndFlush(buf).addListener(future -> {
                                if (future.isSuccess()){
                                    hotFixChannelFuture.channel().close().addListener(future1 -> {
                                        if (!future1.isSuccess()){
                                            future1.cause().printStackTrace();
                                        }
                                    });
                                }else {
                                    future.cause().printStackTrace();
                                }
                            });
                        }
                    });
            SocketAddress s = new DomainSocketAddress("/tmp/hotfix.sock");
            hotFixChannelFuture = b.connect(s).sync();
        }catch (Exception e){
            return false;
        }
        return true;
    }

    public void startHotFixServer() {
        new Thread(() -> {
            try {
                ServerBootstrap b = new ServerBootstrap();
                b.group(bossGroup, workerGroup)
                        .channel(EpollServerDomainSocketChannel.class)
                        .childHandler(new ChannelInboundHandlerAdapter() {
                            @Override
                            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                                ByteBuf buf = (ByteBuf) msg;
                                if (buf.readInt() == 1){
                                    //关闭监听
                                    hotFixServerChannelFuture.channel().close().addListener(future -> {
                                        if (!future.isSuccess()){
                                            future.cause().printStackTrace();
                                        }
                                    });

                                    serverChannelFuture.channel().close().addListener(future -> {
                                        if (future.isSuccess()) {
                                            startHotFixTask();
                                        }else {
                                            future.cause().printStackTrace();
                                        }
                                    });
                                }
                            }
                        });
                SocketAddress s = new DomainSocketAddress("/tmp/hotfix.sock");
                hotFixServerChannelFuture = b.bind(s).sync();
            }catch (Exception e){
                e.printStackTrace();
            }
        }).start();
    }

    private final ExecutorService transferExecutors = Executors.newFixedThreadPool(10);

    public void startHotFixTask() {
        for (Channel channel : channelGroup) {
            transferExecutors.execute(() -> {
                try {
                    Bootstrap bootstrap = new Bootstrap();
                    bootstrap.group(workerGroup)
                            .channel(EpollDomainSocketChannel.class)
                            .handler(new ChannelInboundHandlerAdapter(){
                                @Override
                                public void channelActive(ChannelHandlerContext ctx) throws Exception {
                                    ctx.writeAndFlush(((EpollSocketChannel)channel).fd()).addListener(future -> {
                                        if (!future.isSuccess()){
                                            future.cause().printStackTrace();
                                        }
                                    });
                                }

                                @Override
                                public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                                    ByteBuf buf = (ByteBuf) msg;
                                    int newChannelIdLength = buf.readInt();
                                    String newChannelId = buf.toString(buf.readerIndex(), newChannelIdLength, StandardCharsets.UTF_8);

                                    channelIdMap.put(channel.id().asLongText(), newChannelId);

                                    //迁移存量数据
                                    startTransferReadData(channel);

                                    //FD已经迁移完，关闭用来迁移FD的连接
                                    ctx.channel().close().addListener(future -> {
                                        if (!future.isSuccess()){
                                            future.cause().printStackTrace();
                                        }
                                    });
                                }
                            });

                    SocketAddress fdAddr = new DomainSocketAddress("/tmp/transfer-fd.sock");
                    bootstrap.connect(fdAddr).sync();
                }catch (Exception e){
                    e.printStackTrace();
                }
            });
        }
    }

    public void startTransferReadData(Channel channel) {
        transferExecutors.execute(() -> {
            try {
                Bootstrap bootstrap = new Bootstrap();
                bootstrap.group(workerGroup)
                        .channel(EpollDomainSocketChannel.class)
                        .handler(new ChannelInitializer<EpollDomainSocketChannel>() {
                            @Override
                            protected void initChannel(EpollDomainSocketChannel ch) throws Exception {
                                ch.pipeline().addLast(new IdleStateHandler(5, 5, 5));
                                ch.pipeline().addLast(new TransferClientDataHandler(channel));
                                ch.pipeline().addLast(new ChannelInboundHandlerAdapter(){
                                    @Override
                                    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                                        if (TransferServer.getInstance().closeEvent(evt, ctx.channel().closeFuture())){
                                            //清理
                                            channelMap.remove(channel.id().asLongText());
                                            channelIdMap.remove(channel.id().asLongText());

                                            if (channelMap.size() == 0){
                                                //完成迁移，退出老进程
                                                System.exit(1);
                                            }
                                        }else {
                                            super.userEventTriggered(ctx,evt);
                                        }
                                    }
                                });
                            }
                        });

                SocketAddress dataAddr = new DomainSocketAddress("/tmp/transfer-data.sock");
                bootstrap.connect(dataAddr).sync();
            }catch (Exception e){
                e.printStackTrace();
            }
        });
    }

    public ChannelFuture registerChannel(Channel channel){
        return workerGroup.register(channel);
    }

    public boolean addChannel(Channel channel){
        return channelGroup.add(channel);
    }

    public String getNewChannelIdByOldChannelId(String oldChannelId){
        return channelIdMap.get(oldChannelId);
    }

    public void addTransferDataChannel(String channelId, Channel channel) {
        channelMap.put(channelId, channel);
    }

    public void changeStatus(ServerStatus status){
        this.status = status;
    }

    public ServerStatus getServerStatus(){
        return status;
    }

    public void shutDown(){
        workerGroup.shutdownGracefully();
        bossGroup.shutdownGracefully();
    }

}
