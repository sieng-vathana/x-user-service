package com.x.user.telegram;

import com.sun.net.httpserver.HttpServer;
import com.x.user.config.TelegramAuthProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelegramAuthClientTest {
    private record Call(String method, String contentType, String body) { }

    private HttpServer server;
    private final List<Call> calls = new CopyOnWriteArrayList<>();
    private final AtomicBoolean rejectPhotos = new AtomicBoolean();
    private TelegramAuthClient client;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String method = path.substring(path.lastIndexOf('/') + 1);
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1);
            calls.add(new Call(method, exchange.getRequestHeaders().getFirst("Content-Type"), body));
            String response = method.equals("sendPhoto") && rejectPhotos.get()
                    ? "{\"ok\":false,\"description\":\"Bad Request\"}"
                    : method.equals("sendPhoto")
                    ? "{\"ok\":true,\"result\":{\"photo\":[{\"file_id\":\"small\"},{\"file_id\":\"LOGO-ID\"}]}}"
                    : "{\"ok\":true,\"result\":{}}";
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        TelegramAuthProperties properties = new TelegramAuthProperties();
        properties.setAuthBotToken("test-token");
        properties.setAuthApiBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        client = new TelegramAuthClient(RestClient.builder(), properties);
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void uploadsTheLogoOnceThenReusesItsFileId() {
        client.sendBrandedMessage(42, "<b>TinhLuk</b> code <code>12345678</code>");
        client.sendBrandedMessage(42, "<b>TinhLuk</b> second");

        assertEquals(2, calls.size());
        Call upload = calls.get(0);
        assertEquals("sendPhoto", upload.method());
        assertTrue(upload.contentType().startsWith("multipart/form-data"));
        assertTrue(upload.body().contains("filename=\"tinhluk.png\""));
        assertTrue(upload.body().contains("\u0089PNG"), "the logo bytes are uploaded");
        assertTrue(upload.body().contains("<code>12345678</code>"));
        assertTrue(upload.body().contains("HTML"));

        Call reuse = calls.get(1);
        assertEquals("sendPhoto", reuse.method());
        assertTrue(reuse.contentType().startsWith("application/json"));
        assertTrue(reuse.body().contains("\"photo\":\"LOGO-ID\""));
        assertTrue(reuse.body().contains("\"parse_mode\":\"HTML\""));
    }

    @Test
    void fallsBackToATextMessageWhenThePhotoIsRejected() {
        rejectPhotos.set(true);
        client.sendBrandedMessage(42, "<b>TinhLuk</b> code <code>12345678</code>");

        assertEquals(List.of("sendPhoto", "sendMessage"), calls.stream().map(Call::method).toList());
        assertTrue(calls.get(1).body().contains("<code>12345678</code>"));
        assertTrue(calls.get(1).body().contains("\"parse_mode\":\"HTML\""));
    }
}
