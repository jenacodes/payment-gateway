package com.payment.gateway.server;
import com.payment.gateway.config.AppConfig;
import com.payment.gateway.handlers.AuthorizeHandler;
import com.payment.gateway.handlers.PaymentActionHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;

public class Server {
    public static void start(int serverPortNo, AppConfig config) throws IOException{

        //Start the server and listen for incoming requests
        HttpServer server = HttpServer.create(new InetSocketAddress(serverPortNo), 0);
        server.createContext("/authorize", new AuthorizeHandler(config));
        server.createContext("/payments", new PaymentActionHandler(config));
        server.setExecutor(null); // creates a default executor
        server.start();
        System.out.println("Server started on port " + serverPortNo);
    }
}
