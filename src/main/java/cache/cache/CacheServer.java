package cache.cache;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;

/**
 * CacheServer запускает HTTP-сервер для управления кешем с помощью простых HTTP-запросов.
 * Поддерживает операции GET, POST, PUT и DELETE, а также операции для сохранения и загрузки состояния кеша.
 */
public class CacheServer {

    private final int port;
    final CacheService<String, String> cacheService;

    /**
     * Конструктор, создающий экземпляр CacheServer.
     *
     * @param port Порт, на котором будет запущен HTTP-сервер.
     * @param cacheService Сервис кеширования, который будет использоваться сервером.
     */
    public CacheServer(int port, CacheService<String, String> cacheService) {
        this.port = port;
        this.cacheService = cacheService;
    }

    /**
     * Точка входа в программу. Инициализирует сервер кеша и запускает его.
     *
     * @param args Аргументы командной строки для настройки порта и времени жизни записей в кэше (TTL).
     */
    public static void main(String[] args) {
        int port = 8080;
        long cacheTTL = 5000L;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--port":
                    if (i + 1 < args.length) {
                        port = Integer.parseInt(args[++i]);
                    } else {
                        System.out.println("Ошибка: отсутствует значение для параметра --port, " +
                                "установлен порт по умолчанию - 8080");
                    }
                    break;
                case "--ttl":
                    if (i + 1 < args.length) {
                        cacheTTL = Long.parseLong(args[++i]);
                    } else {
                        System.out.println("Ошибка: отсутствует значение для параметра --ttl, " +
                                "установлено значение по умолчанию - 5000 мс.");
                    }
                    break;
                default:
                    System.out.println("Неизвестный параметр: " + args[i]);
                    return;
            }
        }

        try {
            CacheService<String, String> service = new CacheService<>(cacheTTL);
            CacheServer server = new CacheServer(port, service);
            server.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Запускает HTTP-сервер и регистрирует обработчики для управления кешем.
     *
     * @throws IOException
     */
    public void start() throws IOException {
        HttpServer httpServer = HttpServer.create(new InetSocketAddress("localhost", this.port), 0);

        httpServer.createContext("/cache", this::handleCacheOperations);
        httpServer.createContext("/cache/dump", this::handleDump);
        httpServer.createContext("/cache/load", this::handleLoad);

        httpServer.setExecutor(Executors.newFixedThreadPool(10));
        httpServer.start();

        System.out.println("Сервер запущен: порт " + this.port);
    }

    /**
     * Отправляет HTTP-ответ клиенту.
     *
     * @param exchange Контекст HTTP-запроса/ответа.
     * @param response Тело ответа.
     * @param statusCode Код состояния HTTP-ответа.
     * @throws IOException
     */
    private void sendResponse(HttpExchange exchange, String response, int statusCode) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, response.getBytes(StandardCharsets.UTF_8).length);

        OutputStream os = exchange.getResponseBody();
        os.write(response.getBytes(StandardCharsets.UTF_8));
        os.flush();
        os.close();
    }

    /**
     * Преобразует строку запроса в карту ключ-значение.
     *
     * @param query Строка запроса из URL.
     * @return Карта параметров, извлеченных из строки запроса.
     */
    private Map<String, String> queryToMap(String query) {
        Map<String, String> result = new HashMap<>();
        if (query != null) {
            for (String param : query.split("&")) {
                String[] entry = param.split("=");
                if (entry.length > 1) {
                    result.put(entry[0], entry[1]);
                } else {
                    result.put(entry[0], "");
                }
            }
        }

        return result;
    }

    /**
     * Обрабатывает операции с кешем: GET, POST, PUT и DELETE.
     *
     * @param exchange Контекст HTTP-запроса/ответа.
     * @throws IOException
     */
    private void handleCacheOperations(HttpExchange exchange) throws IOException {
        try {
            if ("GET".equals(exchange.getRequestMethod())) {
                Map<String, String> params = queryToMap(exchange.getRequestURI().getQuery());
                String key = params.get("key");

                if (key != null) {
                    String value = cacheService.get(key);
                    if (value != null) {
                        sendResponse(exchange, value, 200);
                    } else {
                        sendResponse(exchange, "Значение не найдено", 200);
                    }
                } else {
                    sendResponse(exchange, "Ключ отсутствует", 400);
                }

            } else if ("POST".equals(exchange.getRequestMethod()) || "PUT".equals(exchange.getRequestMethod())) {
                Map<String, String> params = queryToMap(new String(exchange.getRequestBody().readAllBytes(),
                        StandardCharsets.UTF_8));
                String key = params.get("key");
                String value = params.get("value");
                Long ttl;

                try {
                    ttl = !Objects.equals(params.get("ttl"), "") ? Long.parseLong(params.get("ttl")) : null;
                } catch (NumberFormatException e) {
                    sendResponse(exchange, "Неверный формат TTL", 400);
                    return;
                }

                if (key != null && value != null) {
                    cacheService.set(key, value, ttl);
                    sendResponse(exchange, "Значение установлено", 200);
                } else {
                    sendResponse(exchange, "Отсутствует ключ или значение", 400);
                }

            } else if ("DELETE".equals(exchange.getRequestMethod())) {
                Map<String, String> params = queryToMap(exchange.getRequestURI().getQuery());
                String key = params.get("key");

                if (key != null) {
                    String value = cacheService.remove(key);
                    if (value != null) {
                        sendResponse(exchange, value, 200);
                    } else {
                        sendResponse(exchange, "Значение не найдено", 200);
                    }
                } else {
                    sendResponse(exchange, "Ключ отсутствует", 400);
                }

            } else {
                sendResponse(exchange, "Неверный метод запроса", 405);
            }

        } catch (Exception e) {
            sendResponse(exchange, "Ошибка сервера: " + e.getMessage(), 500);
        }
    }

    /**
     * Обрабатывает запросы для сохранения состояния кеша в файл.
     *
     * @param exchange Контекст HTTP-запроса/ответа.
     * @throws IOException
     */
    private void handleDump(HttpExchange exchange) throws IOException {
        if ("POST".equals(exchange.getRequestMethod())) {
            Map<String, String> params = queryToMap(new String(exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8));
            String filename = params.get("filename");

            if (filename != null) {
                try {
                    cacheService.dump(filename);
                    sendResponse(exchange, "Состояние сохранено успешно", 200);
                } catch (IOException e) {
                    sendResponse(exchange, "Ошибка при попытке сохранения состояния: "
                            + e.getMessage(), 500);
                }
            } else {
                sendResponse(exchange, "Отсутствует имя файла", 400);
            }

        } else {
            sendResponse(exchange, "Неверный метод запроса", 405);
        }
    }

    /**
     * Обрабатывает запросы для загрузки состояния кеша из файла.
     *
     * @param exchange Контекст HTTP-запроса/ответа.
     * @throws IOException
     */
    private void handleLoad(HttpExchange exchange) throws IOException {
        if ("POST".equals(exchange.getRequestMethod())) {
            Map<String, String> params = queryToMap(new String(exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8));
            String filename = params.get("filename");

            if (filename != null) {
                try {
                    cacheService.load(filename);
                    sendResponse(exchange, "Состояние успешно загружено", 200);
                } catch (IOException | ClassNotFoundException e) {
                    sendResponse(exchange, "Ошибка при попытке загрузки состояния: "
                            + e.getMessage(), 500);
                }
            } else {
                sendResponse(exchange, "Отсутствует имя файла", 400);
            }

        } else {
            sendResponse(exchange, "Неверный метод запроса", 405);
        }
    }

    /**
     * Останавливает сервер и завершает работу кеш-сервиса.
     */
    public void stop() {
        this.cacheService.shutdown();
    }
}
