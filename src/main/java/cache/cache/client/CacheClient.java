package cache.cache.client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

public class CacheClient {

    private final String url;
    private final HttpClient httpClient;

    /**
     * Конструктор CacheClient.
     *
     * @param host Адрес сервера.
     * @param port Порт сервера.
     */
    public CacheClient(String host, int port) {
        this.url = "http://" + host + ":" + port + "/com/cache/client";
        this.httpClient = HttpClient.newHttpClient();
    }

    /**
     * Получить значение по ключу.
     *
     * @param key Ключ.
     * @return Значение, связанное с ключом, или null, если значение не найдено.
     * @throws IOException
     * @throws InterruptedException
     */
    public String get(String key) throws IOException, InterruptedException {
        String url = this.url + "?key=" + key;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return response.body();
        } else {
            return null;
        }
    }

    /**
     * Установить значение по ключу.
     *
     * @param key Ключ.
     * @param value Значение.
     * @param ttl Время жизни записи в мс.
     * @throws IOException
     * @throws InterruptedException
     */
    public String set(String key, String value, Long ttl) throws IOException, InterruptedException {
        Map<String, String> params = new HashMap<>();
        params.put("key", key);
        params.put("value", value);
        if (ttl > 0) {
            params.put("ttl", ttl.toString());
        } else {
            params.put("ttl", "");
        }

        String form = params.entrySet()
                .stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return response.body();
        } else {
            return "Ошибка установки значения: " + response.body();
        }
    }

    /**
     * Перегруженный метод для установки значение по ключу с использованием значения времени жизни по умолчанию.
     *
     * @param key Ключ.
     * @param value Значение.
     * @throws IOException
     * @throws InterruptedException
     */
    public String set(String key, String value) throws IOException, InterruptedException {
        return set(key, value, 0L);
    }

    /**
     * Удалить значение по ключу.
     *
     * @param key Ключ.
     * @throws IOException
     * @throws InterruptedException
     */
    public String remove(String key) throws IOException, InterruptedException {
        String url = this.url + "?key=" + key;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .DELETE()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return response.body();
        } else {
            return "Ошибка удаления значения: " + response.body();
        }
    }

    /**
     * Сохранить состояние кэша в файл на сервере.
     *
     * @param filename Имя файла.
     * @throws IOException
     * @throws InterruptedException
     */
    public void dump(String filename) throws IOException, InterruptedException {
        String url = this.url + "/dump";
        String form = "filename=" + filename;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Ошибка сохранения состояния: " + response.body());
        }
    }

    /**
     * Загрузить состояние кэша из файла на сервере.
     *
     * @param filename Имя файла.
     * @throws IOException
     * @throws InterruptedException
     */
    public void load(String filename) throws IOException, InterruptedException {
        String url = this.url + "/load";
        String form = "filename=" + filename;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Ошибка загрузки состояния: " + response.body());
        }
    }
}
