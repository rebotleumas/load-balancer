import java.io.IOException;
import java.util.List;
import java.net.URI;

public class Main {
    public static void main(String[] args) throws IOException, InterruptedException {
        List<Server> servers = List.of(
                new Server("127.0.0.1", 8081),
                new Server("127.0.0.1", 8082),
                new Server("127.0.0.1", 8083)
        );

        servers.forEach(Server::start);
        List<URI> serverUris = servers
                .stream()
                .map(server -> URI.create(
                        String.format("http://%s:%s", server.getHost(), server.getPort())
                )).toList();
        LoadBalancer lb = new LoadBalancer(serverUris);
        lb.start();
    }
}
