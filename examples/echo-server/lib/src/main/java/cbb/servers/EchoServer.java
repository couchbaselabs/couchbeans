/*
 * This Java source file was generated by the Gradle 'init' task.
 */
package cbb.servers;

import cbb.BeanException;
import cbb.BeanScope;
import cbb.Couchbeans;
import cbb.annotations.Scope;
import cbb.requests.EchoRequest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * This is a global bean, which means that it will be constructed and maintained from DCP stream on all nodes
 * except for those that are prohibited from owning it via `CBB_NODE_IGNORE_PACKAGES`
 * When constructed, this bean will wait for the `running` field to be set to true
 * and then launch an ICMP echo server on port `port` that registers all received
 * echo request as `EchoRequest` beans linked to the `EchoServer` singleton
 */
@Scope(BeanScope.GLOBAL)
public class EchoServer {
    private boolean running;
    private short port = 9000;

    private static transient Thread serverThread;
    private static transient ServerSocket serverSocket;

    private class ServerThread extends Thread {
        @Override
        public void run() {
            try (ServerSocket serverSocket = new ServerSocket(port)) {
                EchoServer.serverSocket = serverSocket;
                while (!(this.isInterrupted() || serverSocket.isClosed())) {
                    String message;
                    String remote;
                    try (Socket socket = serverSocket.accept()) {
                        InputStream in = socket.getInputStream();
                        InputStreamReader inr = new InputStreamReader(in);
                        BufferedReader br = new BufferedReader(inr);
                        message = br.readLine();
                        remote = socket.getRemoteSocketAddress().toString();
                        PrintStream ps = new PrintStream(socket.getOutputStream());
                        ps.println(message);
                    } catch (Exception e) {
                        continue;
                    }
                    EchoRequest request = new EchoRequest(message, remote);
                    Couchbeans.store(request);
                    Couchbeans.link(EchoServer.this, request);
                }
            } catch (Exception e) {
                if (!e.getMessage().contains("Socket closed")) {
                    BeanException.report(EchoServer.this, e);
                }
            } finally {
                serverSocket = null;
                running = false;
                Couchbeans.store(EchoServer.this);
            }
        }
    }


    /**
     * This is a boolean value handler
     * CBB will call it when the corresponding field (running) is set to TRUE on an instance of the bean
     */
    private void whenRunning() {
        start();
    }

    /**
     * This is a boolean value handler
     * CBB will call it when the corresponding field (running) is set to FALSE on an instance of the bean
     */
    private void whenNotRunning() throws IOException {
        stop();
    }

    public void setPort(short port) {
        this.port = port;
    }

    private void restart() throws IOException {
        stop();
        start();
    }

    private void start() {
        System.out.println("Starting the echo server...");
        if (serverThread == null || !serverThread.isAlive()) {
            serverThread = new ServerThread();
            serverThread.start();
        }
        System.out.println("Started the echo server.");
    }

    private void stop() throws IOException {
        System.out.println("Stopping the echo server...");
        if (serverSocket != null) {
            serverSocket.close();
        }
        System.out.println("Stopped the echo server.");
    }

    /**
     * This is a link handler
     * It is called every time a new `EchoRequest` node is linked to the server bean
     * @param request
     */
    public void linkChild(EchoRequest request) {
        System.out.println(String.format("Received echo request '%s' from %s", request.getMessage(), request.getSource()));
    }
}
