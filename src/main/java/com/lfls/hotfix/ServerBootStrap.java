package com.lfls.hotfix;

import com.lfls.hotfix.server.Server;
import com.lfls.hotfix.transfer.TransferServer;
import io.netty.util.ResourceLeakDetector;

/**
 * @author lingfenglangshao
 * @since 28/01/2020
 */
public class ServerBootStrap {

    public static void main(String[] args) {
        ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.ADVANCED);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Server.getInstance().shutDown();
            TransferServer.getInstance().shutDown();
        }));
        try {
            Server.getInstance().start();
        }catch (Exception e){
            e.printStackTrace();
            System.exit(1);
        }
    }

}
