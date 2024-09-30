package com.cache;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CacheServerTest {

    private static CacheServer cacheServer;
    private static final int port = 8080;
    private static final CacheService<String, String> cacheService = new CacheService<>(5000);
    private static final HttpClient client = HttpClient.newHttpClient();

    @BeforeAll
    static void setUp() throws IOException {
        cacheServer = new CacheServer(port, cacheService);
        cacheServer.start();
    }

    @AfterAll
    static void tearDown() {
        cacheServer.stop();
    }

    @Test
    void testGetCacheValue() throws IOException, InterruptedException {
        cacheService.set("key1", "value1", null);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/cache?key=key1"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertEquals("value1", response.body());
    }

    @Test
    void testGetCacheMissingKey() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/cache?key=missingKey"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertEquals("Значение не найдено", response.body());
    }

    @Test
    void testSetCacheValue() throws IOException, InterruptedException {
        String requestBody = "key=key2&value=value2&ttl=5000";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/cache"))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertEquals("Значение установлено", response.body());
        assertEquals("value2", cacheService.get("key2"));
    }

    @Test
    void testDeleteCacheValue() throws IOException, InterruptedException {
        cacheService.set("key3", "value3", null);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/cache?key=key3"))
                .DELETE()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertEquals("value3", response.body());
        assertNull(cacheService.get("key3"));
    }

    @Test
    void testDumpCache() throws IOException, InterruptedException {
        cacheService.set("key4", "value4", null);
        String requestBody = "filename=dump_test.dat";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/cache/dump"))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertEquals("Состояние сохранено успешно", response.body());
        assertTrue(Files.exists(Path.of("dump_test.dat")));

        Files.delete(Path.of("dump_test.dat"));
    }

    @Test
    void testLoadCache() throws IOException, InterruptedException, ClassNotFoundException {
        // Создаем дамп-файл с данными
        cacheService.set("key5", "value5", null);
        cacheService.dump("load_test.dat");

        cacheService.remove("key5");

        String requestBody = "filename=load_test.dat";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/cache/load"))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertEquals("Состояние успешно загружено", response.body());
        assertEquals("value5", cacheService.get("key5"));

        // Удаляем созданный файл
        Files.delete(Path.of("load_test.dat"));
    }

    @Test
    void testInvalidMethod() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/cache"))
                .method("PATCH", HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(405, response.statusCode());
        assertEquals("Неверный метод запроса", response.body());
    }

}
