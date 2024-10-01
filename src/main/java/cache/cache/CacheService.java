package cache.cache;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
import java.io.FileInputStream;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Реализация кэша в памяти с возможностью задания времени жизни (TTL) для записей.
 * Поддерживает операции добавления, чтения, удаления и сохранения состояния кэша.
 *
 * @param <K> Тип ключа в кэше.
 * @param <V> Тип значения в кэше.
 */
public class CacheService<K, V> {

    private final ConcurrentHashMap<K, Value> cacheMap = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final long defaultTTL;

    /**
     * Конструктор, создающий кэш с заданным значением времени жизни по умолчанию.
     *
     * @param defaultTTL Время жизни по умолчанию в мс.
     */
    public CacheService(long defaultTTL) {
        this.defaultTTL = defaultTTL;
    }

    /**
     * Вспомогательный класс для хранения значений в кэше.
     */
    public class Value {
        private final V value;
        private final ScheduledFuture<?> removalTask;

        /**
         * Конструктор класса Value.
         *
         * @param value Значение, хранимое в кэше.
         * @param task Задача, которая удаляет запись по истечении времени жизни.
         */
        public Value(V value, ScheduledFuture<?> task) {
            this.value = value;
            this.removalTask = task;
        }
    }

    /**
     * Получает значение из кэша по указанному ключу.
     *
     * @param key Ключ, по которому нужно получить значение.
     * @return Значение, связанное с указанным ключом, или null, если ключ не найден.
     */
    public V get(K key) {
        Value val = cacheMap.getOrDefault(key, null);
        return val != null ? val.value : null;
    }

    /**
     * Добавляет или обновляет значение в кэше с указанным временем жизни.
     * Если время жизни не указано, используется значение по умолчанию.
     *
     * @param key Ключ, по которому нужно сохранить значение.
     * @param value Значение, которое нужно сохранить.
     * @param ttl Время жизни записи в миллисекундах. Если null, используется значение по умолчанию.
     * @return true, если операция была успешной.
     */
    public boolean set(K key, V value, Long ttl) {
        Value val = cacheMap.get(key);

        if (val != null) {
            if (val.removalTask != null && !val.removalTask.isCancelled()) {
                val.removalTask.cancel(false);
            }
        }

        long ttlValue = ttl != null ? ttl : defaultTTL;

        ScheduledFuture<?> task = scheduler.schedule(() -> {
            cacheMap.remove(key);
        }, ttlValue, TimeUnit.MILLISECONDS);

        cacheMap.put(key, new Value(value, task));

        return true;
    }

    /**
     * Удаляет запись из кэша по указанному ключу.
     * Если запись существует, связанная задача удаления также отменяется.
     *
     * @param key Ключ, по которому нужно удалить запись.
     * @return Значение, связанное с указанным ключом, или null, если ключ не найден.
     */
    public V remove(K key) {
        Value val = cacheMap.remove(key);
        if (val != null) {
            if (val.removalTask != null && !val.removalTask.isCancelled()) {
                val.removalTask.cancel(false);
            }
            return val.value;
        }
        return null;
    }

    /**
     * Сохраняет текущее состояние кэша в файл.
     *
     * @param filename Имя файла для сохранения состояния кэша.
     * @return Файл, в который было сохранено состояние кэша.
     * @throws IOException Если произошла ошибка при записи в файл.
     */
    public File dump(String filename) throws IOException {
        ConcurrentHashMap<K, V> serializableCache = new ConcurrentHashMap<>();
        for (K key : cacheMap.keySet()) {
            serializableCache.put(key, cacheMap.get(key).value);
        }

        File file = new File(filename);
        File directory = file.getParentFile();
        if (directory != null && !directory.exists()) {
            if (!directory.mkdirs()) {
                throw new IOException("Не удалось создать директорию: " + directory);
            }
        }

        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(file))) {
            out.writeObject(serializableCache);
        }

        return file;
    }

    /**
     * Загружает состояние кэша из файла. TTL у всех загруженных записей будет обновлен и выставлен по умолчанию.
     *
     * @param filename Имя файла, из которого нужно загрузить состояние кэша.
     * @throws IOException Если произошла ошибка при чтении файла.
     * @throws ClassNotFoundException Если тип данных в файле не совпадает с ожидаемым.
     */
    public void load(String filename) throws IOException, ClassNotFoundException {
        File file = new File(filename);
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(file))) {
            Object obj = in.readObject();
            if (obj instanceof ConcurrentHashMap<?, ?>) {
                @SuppressWarnings("unchecked")
                ConcurrentHashMap<K, V> loadedStorage = (ConcurrentHashMap<K, V>) obj;
                cacheMap.clear();
                for (K key : loadedStorage.keySet()) {
                    set(key, loadedStorage.get(key), null);
                }
            } else {
                throw new ClassCastException("Loaded object is not of type ConcurrentHashMap.");
            }
        }
    }

    /**
     * Завершает работу планировщика задач.
     * Ожидает завершения всех текущих задач и принудительно завершает их, если это необходимо.
     */
    public void shutdown() {
        try {
            scheduler.shutdown();
            if (!scheduler.isShutdown()) {
                scheduler.shutdownNow();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
