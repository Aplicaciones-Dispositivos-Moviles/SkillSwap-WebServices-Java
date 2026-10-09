package com.innovify.skillswap.learningpathengine.infrastructure.ai;

import com.innovify.skillswap.shared.infrastructure.json.Json;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * A fake Gemini REST API on the loopback interface, so no test uses the network or a real key. Each request is
 * recorded and answered by the configured function, which receives the prompt.
 */
public final class FakeGeminiServer implements AutoCloseable {

    /** A request the server received. */
    public record Request(String path, String apiKey, String body) {

        /** The text of the prompt sent to the model. */
        public String prompt() {
            Map<?, ?> json = (Map<?, ?>) Json.parse(body);
            Map<?, ?> content = (Map<?, ?>) ((List<?>) json.get("contents")).get(0);
            Map<?, ?> part = (Map<?, ?>) ((List<?>) content.get("parts")).get(0);
            return (String) part.get("text");
        }
    }

    /** What the server answers; {@link #HANG} never answers until the server stops. */
    public record Reply(int status, String body) {

        public static final Reply HANG = new Reply(-1, "");

        /** A successful answer whose model text is the given JSON text. */
        public static Reply text(String modelText) {
            return new Reply(200, Json.write(Map.of("candidates", List.of(Map.of(
                    "content", Map.of("parts", List.of(Map.of("text", modelText)), "role", "model"),
                    "finishReason", "STOP")))));
        }

        /** A successful answer whose model text is the JSON of the value. */
        public static Reply json(Object value) {
            return text(Json.write(value));
        }

        public static Reply status(int status) {
            return new Reply(status, "{\"error\": {\"message\": \"fake failure\"}}");
        }
    }

    private final HttpServer server;
    private final List<Request> requests = new CopyOnWriteArrayList<>();
    private final CountDownLatch release = new CountDownLatch(1);
    private volatile Function<String, Reply> responder = prompt -> Reply.status(500);

    private FakeGeminiServer(HttpServer server) {
        this.server = server;
    }

    public static FakeGeminiServer start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            FakeGeminiServer fake = new FakeGeminiServer(server);
            server.setExecutor(Executors.newCachedThreadPool(runnable -> {
                Thread thread = new Thread(runnable);
                thread.setDaemon(true);
                return thread;
            }));
            server.createContext("/", fake::handle);
            server.start();
            return fake;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /** The base URL to configure as gemini.base-url. */
    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1beta/";
    }

    public void respond(Function<String, Reply> responder) {
        this.responder = responder;
    }

    public void respond(Reply reply) {
        this.responder = prompt -> reply;
    }

    public List<Request> requests() {
        return requests;
    }

    @Override
    public void close() {
        release.countDown();
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Request request = new Request(exchange.getRequestURI().getPath(),
                exchange.getRequestHeaders().getFirst("x-goog-api-key"), body);
        requests.add(request);

        Reply reply = responder.apply(request.prompt());
        if (reply == Reply.HANG) {
            try {
                release.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
            return;
        }

        byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(reply.status(), bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
