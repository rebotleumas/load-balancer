import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class LoadBalancer {
    private final HttpServer server;
    private final int port = 8080;
    private final List<URI> participantServers;
    private final HttpClient client;
    private final RoundRobinHandler roundRobinHandler;
    private final ScheduledExecutorService healthCheckExecutorService;

    public LoadBalancer(List<URI> participantServers) throws IOException {
        String host = "127.0.0.1";
        this.participantServers = participantServers;
        this.client = HttpClient.newHttpClient();
        this.healthCheckExecutorService = new ScheduledThreadPoolExecutor(10);
        this.roundRobinHandler = new RoundRobinHandler(this.participantServers, this.client);

        this.server = HttpServer.create(new InetSocketAddress(host, this.port), 0);
        // Handle all requests using round robin, routing only to healthy servers
        this.server.createContext(
                "/",
                roundRobinHandler
        );
    }

    public void start() {
        this.server.start();
        this.healthCheckExecutorService.scheduleWithFixedDelay(this::updateHealthyServers, 5, 5, TimeUnit.SECONDS);
        System.out.println("Load balancer listening on port " + this.port);
    }

    private void updateHealthyServers() {
        List<URI> healthyServers = participantServers
                .stream()
                .filter(this::isHealthy)
                .toList();
        System.out.println("Healthy servers " + healthyServers);
        this.roundRobinHandler.setServers(healthyServers);
    }

    private boolean isHealthy(URI serverUri) {
        URI healthCheckUri = URI.create(String.format("%s/health", serverUri));
        try {
            HttpResponse<String> response = this.client.send(
                    HttpRequest.newBuilder()
                            .GET()
                            .uri(healthCheckUri)
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            return response.statusCode() == 200;
        } catch(IOException | InterruptedException e) {
            return false;
        }
    }

    static class RoundRobinHandler implements HttpHandler {
        private volatile List<URI> servers;
        private final AtomicInteger requestCount = new AtomicInteger(0);
        private final HttpClient client;
        private final Object serverLock = new Object();  // Lock for server updates

        public RoundRobinHandler(List<URI> servers, HttpClient client) {
            this.servers = servers;
            this.client = client;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            OutputStream os = exchange.getResponseBody();
            URI nextUri = this.getNextUri();
            try {
                HttpResponse<String> response = client.send(
                        HttpRequest.newBuilder()
                                .GET()
                                .uri(nextUri)
                                .build(),
                        HttpResponse.BodyHandlers.ofString()
                );
                String message = String.format(
                    "Response from server: %s %s",
                    response.statusCode(),
                    response.body()
                );
                exchange.sendResponseHeaders(200, message.length());
                os.write(message.getBytes());
                exchange.close();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        public void setServers(List<URI> servers) {
            synchronized (serverLock) {
                this.servers = servers;
                this.requestCount.set(0);
            }
        }

        private URI getNextUri() {
            synchronized (serverLock) {
                if (this.servers.isEmpty()) {
                    throw new RuntimeException("No healthy servers");
                }
                int numberOfServers = this.servers.size();
                int idx = requestCount.getAndIncrement() % numberOfServers;
                return this.servers.get(idx);
            }
        }
    }
}


