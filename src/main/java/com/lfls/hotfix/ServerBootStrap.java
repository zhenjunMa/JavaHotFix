package com.lfls.hotfix;

import com.lfls.hotfix.server.Server;

/**
 * @author lingfenglangshao
 * @since 28/01/2020
 */
public class ServerBootStrap {

    public static void main(String[] args) {
        try {
            Server.getInstance().start();
        }catch (Exception e){
            e.printStackTrace();
            System.exit(1);
        }
    }

}
