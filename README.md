# JavaHotFix

### A、方案对比

Java热更新的几种方式：

1. 基于类加载实现
    1. 类加载有诸多限制， 比如新的Class虽然被加载了，但是旧class创建出来的对象是无法感知新Class
2. forkAndExec
    1. 可以使用Runtime.getRuntime().exec()实现，但是由于JVM的设置，子进程不会继承父进程的文件描述符。
3. 连接迁移
    1. 启动两个独立的Java进程，然后进行文件描述符迁移，Java原生并不支持迁移文件描述符的系统调用，但好在Netty对该系统内调用进行了封装。

### B、实现梗概

1. Old Server启动的时候bind到指定端口，并开启reuse_port。
2. New Server启动以后bind到相同端口，开始接收调用端的连接（此时有两个Server在接收调用端的连接）。
3. Old Server关闭监听，此后所有新建立的连接将由New Server接管。
4. Old Server迁移已经跟调用端建立好的连接到New Server。
5. Old Server迁移存量连接各自对应的存量数据（包括部分读进来的数据以及还没有写出去的数据）给New Server。
6. New Server拿到迁移连接的存量数据以后开始接管后续的读写事件。
