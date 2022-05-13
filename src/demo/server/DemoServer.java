package demo.server;

import demo.Msg;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

import static demo.Constants.*;
import static demo.Msg.Kind.*;
import static demo.Msg.receiveMsg;
import static demo.Msg.sendMsg;

public class DemoServer {

    public static void main(String[] args) {
        DemoServer server = new DemoServer();
        try {
            server.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private volatile boolean terminate = false;
    private ServerSocketChannel server;
    private Thread selectThread;
    private Selector selector;

    private Map<SelectionKey, ClientHdlr> clientMap = new ConcurrentHashMap<>();
    private CountDownLatch termLatch = new CountDownLatch(1);

    private void start() throws InterruptedException {
        out.println("Start server");

        try {
            selector = Selector.open();
            selector.select(); // blocking

            server = ServerSocketChannel.open();
            server.bind(new InetSocketAddress(PORT));
            server.configureBlocking(false);
            SelectionKey acceptKey = server.register(selector, SelectionKey.OP_ACCEPT);


            out.println("Terminate with 'ENTER'");

            selectThread = new Thread(() -> {
                while (!terminate) {
                    try {
                        selector.select(100);
                        Set<SelectionKey> events = selector.selectedKeys();
                        for (SelectionKey key : events) {
                            if (key.isAcceptable()) {
                                SocketChannel clientChannel = server.accept(); // not blocking
                                ClientHdlr hdlr = new ClientHdlr(clientChannel);
                                clientChannel.configureBlocking(false);
                                SelectionKey readEvtKey = clientChannel.register(selector, SelectionKey.OP_READ);
                                clientMap.put(readEvtKey, hdlr);
                                readEvtKey.attach(hdlr);
                                hdlr.start();
                            } else if (key.isReadable()) {
                                ClientHdlr hdlr = clientMap.get(key);
                                //hdlr = (ClientHdlr) key.attachment();
                                hdlr.handleInput();
                            }
                        }
                        // SocketChannel clientSocket = server.accept(); // blocking bis client connect,
                        // Thread thr = new Thread(hdlr);
                        // clientThreads.add(thr);
                        // thr.start();
                    } catch (SocketTimeoutException ste) {
                        out.println("accept timeout");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                try {
                    server.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
            //acceptThread.start();

            in.nextLine();
            terminate();

            // wait for all clients to terminate
            out.println("Terminated");
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void terminate() throws InterruptedException {
        terminate = true;
        if (!clientMap.isEmpty()) {
            termLatch.await();
        }
    }

    enum State {CONNECTED, WAIT, UPLOAD, LOGOUT}

    private class ClientHdlr {
        private final SocketChannel socketChannel;
        private ByteBuffer buffer = ByteBuffer.allocate(256);
        private String clientName;
        private State state;
        String fileName;
        long fileSize;
        long read;

        private ClientHdlr(SocketChannel socketChannel) throws IOException {
            this.socketChannel = socketChannel;
        }

        /*@Override
        public void run() {
            try {
                out.println("Client handler started");

                sendMsg(writer, HELO, socket.toString());


                if (login.kind() == LOGIN) {
                    clientName = login.param(0);
                    sendMsg(writer, SUCCESS, LOGIN.name(), clientName);
                } else {
                    sendMsg(writer, FAILED, LOGIN.name());
                }

                Msg upCmd = receiveMsg(reader);
                try {
                    String fileName = upCmd.param(0);
                    long fileSize = Long.parseLong(upCmd.param(1));
                    sendMsg(writer, ACKN, fileName);

                } catch (Exception e) {
                    sendMsg(writer, FAILED, UPLOAD.name());
                }

            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    clientThreads.remove(Thread.currentThread());
                    if (terminate && clientThreads.isEmpty())
                        termLatch.countDown();
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

        }*/

        private void start() throws IOException {
            sendMsg(socketChannel, HELO, socketChannel.toString());
            state = State.CONNECTED;
        }

        public void handleInput() throws IOException {

            switch (state) {
                case CONNECTED -> {
                    Msg login = receiveMsg(socketChannel, buffer);
                    if (login.kind() == LOGIN) {
                        clientName = login.param(0);
                        sendMsg(socketChannel, SUCCESS, LOGIN.name(), clientName);
                        state = State.WAIT;
                    } else {
                        sendMsg(socketChannel, FAILED, LOGIN.name());
                    }
                }
                case WAIT -> {
                    Msg cmd = receiveMsg(socketChannel, buffer);
                    if(cmd.kind() == UPLOAD){
                        fileName = cmd.param(0);
                        fileSize = Long.parseLong(cmd.param(1));
                        read = 0;
                        sendMsg(socketChannel, ACKN, fileName);
                        state = State.UPLOAD;
                    } else if(cmd.kind() == LOGOUT){
                        // TODO
                    }

                }
                case UPLOAD -> {
                    buffer.clear();
                    int n = socketChannel.read(buffer);
                    buffer.flip();
                    read += n;
                    if(read == fileSize){
                        sendMsg(socketChannel, SUCCESS, UPLOAD.name(), fileName);
                        state = State.WAIT;
                    }
                    // save in file
                }
            }

        }
    }

}
