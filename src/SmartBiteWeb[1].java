import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

public class SmartBiteWeb {
    private static final String MODEL = "gemini-3.5-flash-lite";
    private static final String API_URL =
        "https://generativelanguage.googleapis.com/v1beta/models/"
        + MODEL + ":generateContent";

    private static final HttpClient CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20)).build();

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);

        server.createContext("/", exchange -> {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                send(exchange, 405, "text/plain; charset=utf-8", "Method not allowed");
                return;
            }
            byte[] page = Files.readAllBytes(Path.of("public", "index.html"));
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            exchange.sendResponseHeaders(200, page.length);
            try (var out = exchange.getResponseBody()) { out.write(page); }
        });

        server.createContext("/api/chat", exchange -> {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                send(exchange, 405, "application/json; charset=utf-8",
                    "{\"error\":\"Method not allowed\"}");
                return;
            }
            try {
                String body = new String(exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8);
                String message = jsonField(body, "message");
                boolean recommendation = body.matches("(?s).*\\\"recommendation\\\"\\s*:\\s*true.*");
                if (message == null || message.isBlank()) {
                    send(exchange, 400, "application/json; charset=utf-8",
                        "{\"error\":\"Please enter a message.\"}");
                    return;
                }
                String answer = askGemini(message, recommendation);
                send(exchange, 200, "application/json; charset=utf-8",
                    "{\"answer\":\"" + jsonEscape(answer) + "\"}");
            } catch (Exception ex) {
                send(exchange, 500, "application/json; charset=utf-8",
                    "{\"error\":\"" + jsonEscape(safeMessage(ex)) + "\"}");
            }
        });

        server.start();
        System.out.println("SmartBite AI web server running on port " + port);
    }

    private static String askGemini(String message, boolean recommendation) throws Exception {
        String apiKey = System.getenv("GEMINI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("The server administrator has not configured the AI key.");
        }

        String prompt = "You are SmartBite AI, a helpful food ordering assistant. "
            + "Our menu is Burger Rs.120, Pizza Rs.250, Biryani Rs.180 and Sandwich Rs.90. "
            + (recommendation
                ? "Recommend suitable food from this menu based on these preferences: "
                : "Answer the customer's question: ")
            + message
            + (recommendation ? ". Explain why you recommend it." : "");

        String json = "{\"contents\":[{\"parts\":[{\"text\":\""
            + jsonEscape(prompt) + "\"}]}]}";

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(API_URL))
            .timeout(Duration.ofSeconds(60))
            .header("Content-Type", "application/json")
            .header("x-goog-api-key", apiKey)
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();

        HttpResponse<String> response = CLIENT.send(request,
            HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("AI service returned HTTP "
                + response.statusCode() + ". Please check the server's AI configuration.");
        }
        String result = extractJsonString(response.body(), "\"text\"");
        if (result == null || result.isBlank()) {
            throw new RuntimeException("No AI response was received.");
        }
        return result;
    }

    private static String jsonField(String json, String field) {
        return extractJsonString(json, "\"" + field + "\"");
    }

    private static String extractJsonString(String json, String key) {
        int keyAt = json.indexOf(key);
        if (keyAt < 0) return null;
        int colon = json.indexOf(':', keyAt + key.length());
        if (colon < 0) return null;
        int start = json.indexOf('"', colon + 1);
        if (start < 0) return null;
        StringBuilder value = new StringBuilder();
        boolean escaped = false;
        for (int i = start + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) {
                switch (c) {
                    case 'n' -> value.append('\n');
                    case 'r' -> value.append('\r');
                    case 't' -> value.append('\t');
                    case 'b' -> value.append('\b');
                    case 'f' -> value.append('\f');
                    case 'u' -> {
                        if (i + 4 < json.length()) {
                            try {
                                value.append((char) Integer.parseInt(json.substring(i + 1, i + 5), 16));
                                i += 4;
                            } catch (NumberFormatException ignored) { }
                        }
                    }
                    default -> value.append(c);
                }
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                return value.toString();
            } else {
                value.append(c);
            }
        }
        return null;
    }

    private static String jsonEscape(String value) {
        return value.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }

    private static String safeMessage(Exception ex) {
        String m = ex.getMessage();
        if (m == null || m.isBlank()) return "Something went wrong. Please try again.";
        return m.length() > 240 ? m.substring(0, 240) : m;
    }

    private static void send(HttpExchange exchange, int status, String contentType,
                             String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var out = exchange.getResponseBody()) { out.write(bytes); }
    }
}
