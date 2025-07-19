import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

public class Server {
    private final HttpServer server;
    private final int port;
    private final String host;

    public Server(String host, int port) throws IOException {
        this.port = port;
        this.host = host;
        this.server = HttpServer.create(new InetSocketAddress(this.host, this.port), 0);
        this.server.createContext("/", new SimpleHandler(this));
        this.server.createContext("/health", new HealthCheckHandler());
    }

    public int getPort() {
        return this.port;
    }

    public String getHost() {
        return this.host;
    }
    public void start() {
        this.server.start();
        System.out.println("Server listening on port " + this.port);
    }

    public void kill() {
        this.server.stop(1);
    }

    static class SimpleHandler implements HttpHandler {
        private final Server httpServer;

        public SimpleHandler(Server httpServer) {
            this.httpServer = httpServer;
        }

        @Override
        public void handle(HttpExchange exchange) {
            String message = String.format(
                    "Request received on server %s from address %s",
                    this.httpServer.port,
                    exchange.getRemoteAddress()
            );
            System.out.println(message);

            OutputStream os = exchange.getResponseBody();
            String response = "Hello from server " + this.httpServer.port;

            try (exchange) {
                exchange.sendResponseHeaders(200, response.length());
                os.write(response.getBytes());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    static class HealthCheckHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) {
            try (exchange) {
                // Simulate a 30% chance of the server being unresponsive
                if (Math.random() > 0.3) {
                    exchange.sendResponseHeaders(200, 0);
                } else {
                    exchange.sendResponseHeaders(500, 0);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}


