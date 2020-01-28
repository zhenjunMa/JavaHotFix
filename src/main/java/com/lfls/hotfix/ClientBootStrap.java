package com.lfls.hotfix;

import com.lfls.hotfix.client.Client;

/**
 * @author lingfenglangshao
 * @since 28/01/2020
 */
public class ClientBootStrap {

    public static void main(String[] args) {
        try {
            new Client().start();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

}
