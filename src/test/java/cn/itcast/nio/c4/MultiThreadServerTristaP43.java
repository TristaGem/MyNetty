package cn.itcast.nio.c4;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;


@Slf4j
/**
 * boss will work on building connections, while worker work on read/write
 * p43 Learning node
 * there are 2 lines of code needs to execute in correct order to make it work
 * 1. sc.register(selector, SelectionKey.OP_READ, null); boss thread needs to register the worker's
 * selector with the connected socket channel
 * 2. selector.select(); this is a blocking line running in worker thread
 *
 * But they are running in different thread, which is difficult to control the order of exeuction.
 * Thus we need to use ConcurrentLinkedQueue to decouple.
 * We wrap the 1st line as a task and enqueue it, and worker thread will pop the task and run it.
 * But what if the 1st line didn't run before 2nd line? Or there are more connections coming from other clients?
 * So after the enqueue of the task, we need to wake up the worker's selector.
 *
 *
 */
public class MultiThreadServerTristaP43 {

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

        Worker worker = new Worker("worker-0");


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
                    worker.register(sc);
//                    sc.register(worker.selector, SelectionKey.OP_READ, null);
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

        private ConcurrentLinkedQueue<Runnable> queue = new ConcurrentLinkedQueue<>();

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

            // add a task to the queue, not execute yet.
            queue.add(() -> {
                try {
                    sc.register(selector, SelectionKey.OP_READ, null);
                } catch(ClosedChannelException e) {
                    e.printStackTrace();
                }
            });
            selector.wakeup();
        }

        @Override
        public void run() {
            while (true) {
                try {
                    while(!queue.isEmpty()) {
                        queue.poll().run();
                    }

                    selector.select();
                    Iterator<SelectionKey> iter = selector.selectedKeys().iterator();
                    while (iter.hasNext()) {
                        SelectionKey key = iter.next();
                        // 处理key 时，要从 selectedKeys 集合中删除，否则下次处理就会有问题
                        iter.remove();
                        log.debug("key: {}", key);
                        if (key.isReadable()) {
                            ByteBuffer buffer = ByteBuffer.allocate(16);
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
