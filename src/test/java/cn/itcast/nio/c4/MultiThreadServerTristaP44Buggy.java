package cn.itcast.nio.c4;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * With this version, I have commented out several logging sentence, like,
 * line 87, 89, 97
 * I have run into some issues that I can't explain, which I believe that HAS something to do with java's compilation,
 * they reordered some line which makes selector blocking on the select method, and can't wake up to register with the socketchannel
 *
 * Running Log:
 * /Library/Java/JavaVirtualMachines/amazon-corretto-8.jdk/Contents/Home/bin/java -Dvisualvm.id=164253801053083 -javaagent:/Applications/IntelliJ IDEA.app/Contents/lib/idea_rt.jar=57522:/Applications/IntelliJ IDEA.app/Contents/bin -Dfile.encoding=UTF-8 -classpath /Library/Java/JavaVirtualMachines/amazon-corretto-8.jdk/Contents/Home/jre/lib/charsets.jar:/Library/Java/JavaVirtualMachines/amazon-corretto-8.jdk/Contents/Home/jre/lib/ext/cldrdata.jar:/Library/Java/JavaVirtualMachines/amazon-corretto-8.jdk/Contents/Home/jre/lib/ext/dnsns.jar:/Library/Java/JavaVirtualMachines/amazon-corretto-8.jdk/Contents/Home/jre/lib/ext/jaccess.jar:/Library/Java/JavaVirtualMachines/amazon-corretto-8.jdk/Contents/Home/jre/lib/ext/jfxrt.jar:/Library/Java/JavaVirtualMachines/amazon-corretto-8.jdk/Contents/Home/jre/lib/ext/localedata.jar:/Library/Java/JavaVirtualMachines/amazon-corretto-8.jdk/Contents/Home/jre/lib/ext/nashorn.jar:/Library/Java/JavaVirtualMachines/amazon-corretto-8.jdk/Contents/Home/jre/lib/ext/sunec.jar:/Library/Java/JavaVirtualMachines/amazon-corretto-8.jdk/Contents/Home/jre/lib/ext/sunjce_provider.jar:/Library/Java/JavaVirtualMachines/amazon-corretto-8.jdk/Contents/Home/jre/lib/ext/sunpkcs11.jar:/Library/Java/JavaVirtualMachines/amazon-corretto-8.jdk/Contents/Home/jre/lib/ext/zipfs.jar:/Library/Java/JavaVirtualMachines/amazon-corretto-8.jdk/Contents/Home/jre/lib/jce.jar:/Library/Java/JavaVirtualMachines/amazon-corretto-8.jdk/Contents/Home/jre/lib/jfr.jar:/Library/Java/JavaVirtualMachines/amazon-corretto-8.jdk/Contents/Home/jre/lib/jfxswt.jar:/Library/Java/JavaVirtualMachines/amazon-corretto-8.jdk/Contents/Home/jre/lib/jsse.jar:/Library/Java/JavaVirtualMachines/amazon-corretto-8.jdk/Contents/Home/jre/lib/management-agent.jar:/Library/Java/JavaVirtualMachines/amazon-corretto-8.jdk/Contents/Home/jre/lib/resources.jar:/Library/Java/JavaVirtualMachines/amazon-corretto-8.jdk/Contents/Home/jre/lib/rt.jar:/Volumes/workplace/netty-demo/target/test-classes:/Volumes/workplace/netty-demo/target/classes:/Users/tosavana/.m2/repository/io/netty/netty-all/4.1.39.Final/netty-all-4.1.39.Final.jar:/Users/tosavana/.m2/repository/org/projectlombok/lombok/1.16.18/lombok-1.16.18.jar:/Users/tosavana/.m2/repository/com/google/code/gson/gson/2.8.5/gson-2.8.5.jar:/Users/tosavana/.m2/repository/com/google/guava/guava/19.0/guava-19.0.jar:/Users/tosavana/.m2/repository/ch/qos/logback/logback-classic/1.2.3/logback-classic-1.2.3.jar:/Users/tosavana/.m2/repository/ch/qos/logback/logback-core/1.2.3/logback-core-1.2.3.jar:/Users/tosavana/.m2/repository/org/slf4j/slf4j-api/1.7.25/slf4j-api-1.7.25.jar:/Users/tosavana/.m2/repository/com/google/protobuf/protobuf-java/3.11.3/protobuf-java-3.11.3.jar cn.itcast.nio.c4.MultiThreadServerTristaP44
 * 18:13:31 [DEBUG] [main] c.i.n.c.MultiThreadServerTristaP44 - sscKey:sun.nio.ch.SelectionKeyImpl@64cee07
 * 18:13:34 [DEBUG] [main] c.i.n.c.MultiThreadServerTristaP44 - key: sun.nio.ch.SelectionKeyImpl@64cee07
 * 18:13:34 [DEBUG] [main] c.i.n.c.MultiThreadServerTristaP44 - before register READ event with worker's selector.....
 */
@Slf4j
public class MultiThreadServerTristaP44Buggy {

    public static void main(String[] args) throws IOException {
        // 1. 创建 selector, 管理多个 channel
        Selector boss = Selector.open();
        ServerSocketChannel ssc = ServerSocketChannel.open();
        ssc.configureBlocking(false);
        // 2. 建立 selector 和 channel 的联系（注册）
        // SelectionKey 就是将来事件发生后，通过它可以知道事件和哪个channel的事件
        SelectionKey sscKey = ssc.register(boss, 0, null);
        // key 只关注 accept 事件
        sscKey.interestOps(SelectionKey.OP_ACCEPT);
        log.debug("sscKey:{}", sscKey);
        ssc.bind(new InetSocketAddress(8080));

        Worker[] workers = new Worker[2];
        for(int i = 0; i < workers.length; i++) {
            workers[i] = new Worker("worker-" + i);
        }


        AtomicInteger index = new AtomicInteger();
        while (true) {
            // 3. select 方法, 没有事件发生，线程阻塞，有事件，线程才会恢复运行
            // select 在事件未处理时，它不会阻塞, 事件发生后要么处理，要么取消，不能置之不理
            boss.select();
            // 4. 处理事件, selectedKeys 内部包含了所有发生的事件
            Iterator<SelectionKey> iter = boss.selectedKeys().iterator(); // accept, read
            while (iter.hasNext()) {
                SelectionKey key = iter.next();
                // 处理key 时，要从 selectedKeys 集合中删除，否则下次处理就会有问题
                iter.remove();
                log.debug("key: {}", key);
                // 5. 区分事件类型
                if (key.isAcceptable()) { // 如果是 accept
                    ServerSocketChannel channel = (ServerSocketChannel) key.channel();
                    SocketChannel sc = channel.accept();
                    sc.configureBlocking(false);
                    log.debug("before register READ event with worker's selector.....");
                    workers[index.incrementAndGet()% workers.length].register(sc);
                    log.debug("after register READ event with worker's selector.....");

                }
            }
        }
    }
    static  class Worker implements Runnable {
        private Thread thread;
        private String name;
        private Selector selector;
        private volatile boolean isStarted;

        public Worker(String name) {
            this.name = name;
            this.isStarted = false;
        }

        public void register(SocketChannel sc) throws IOException {
            if (!isStarted) {
                selector = Selector.open();
                isStarted = true;
                thread = new Thread(this, this.name);
                thread.start();
            }
//            log.debug("before wakeup.....");
            selector.wakeup();
//            log.debug("after wakeup......");
            sc.register(selector, SelectionKey.OP_READ, null);
        }

        @Override
        public void run() {
            while (true) {
                try {
//                    log.debug("start to select, blocking......");
                    selector.select();
                    Iterator<SelectionKey> iter = selector.selectedKeys().iterator();
                    while (iter.hasNext()) {
                        SelectionKey key = iter.next();
                        // 处理key 时，要从 selectedKeys 集合中删除，否则下次处理就会有问题
                        iter.remove();
                        log.debug("worker: {}, key: {}", name, key);
                        if (key.isReadable()) {
                            ByteBuffer buffer = ByteBuffer.allocate(128);
                            SocketChannel channel = (SocketChannel) key.channel();
                            log.debug("read...{}", channel.getRemoteAddress());
                            int read = channel.read(buffer);
                            if(read == -1) {
                                key.cancel();
                            } else {
                                buffer.flip();
                                System.out.println(Charset.defaultCharset().decode(buffer));
                            }

                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }


            }

        }
    }
}
