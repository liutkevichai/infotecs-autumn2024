package cache;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class CacheServiceTest {

    private CacheService<String, String> cacheService;
    private final long defaultTTL = 5000;

    @BeforeEach
    void setUp() {
        cacheService = new CacheService<>(defaultTTL);
    }

    @AfterEach
    void tearDown() {
        cacheService.shutdown();
    }

    @Test
    void testSetAndGet() {
        cacheService.set("key1", "value1", null);
        assertEquals("value1", cacheService.get("key1"));
    }

    @Test
    void testSetWithTTL() throws InterruptedException {
        cacheService.set("key2", "value2", 1000L);
        assertEquals("value2", cacheService.get("key2"));

        TimeUnit.MILLISECONDS.sleep(2000);
        assertNull(cacheService.get("key2"));
    }

    @Test
    void testSetOverride() {
        cacheService.set("key5", "value5", null);
        assertEquals("value5", cacheService.get("key5"));

        cacheService.set("key5", "newValue5", null);
        assertEquals("newValue5", cacheService.get("key5"));
    }

    @Test
    void testTTLResetOnSet() throws InterruptedException {
        cacheService.set("key6", "value6", 1000L);
        assertEquals("value6", cacheService.get("key6"));

        TimeUnit.MILLISECONDS.sleep(500);
        cacheService.set("key6", "value7", 1000L);
        assertEquals("value7", cacheService.get("key6"));

        TimeUnit.MILLISECONDS.sleep(600);
        assertEquals("value7", cacheService.get("key6"));

        TimeUnit.MILLISECONDS.sleep(500);
        assertNull(cacheService.get("key6"));
    }

    @Test
    void testRemove() {
        cacheService.set("key3", "value3", null);
        assertEquals("value3", cacheService.remove("key3"));
        assertNull(cacheService.get("key3"));
    }

    @Test
    void testRemoveNonExistingKey() {
        assertNull(cacheService.remove("nonExistingKey"));
    }

    @Test
    void testDumpAndLoad() throws IOException, ClassNotFoundException {
        cacheService.set("key4", "value4", null);
        File file = cacheService.dump("cache_dump_test.dat");
        assertTrue(file.exists());

        CacheService<String, String> newCacheService = new CacheService<>(defaultTTL);
        newCacheService.load("cache_dump_test.dat");

        assertEquals("value4", newCacheService.get("key4"));

        file.delete();
        newCacheService.shutdown();
    }

    @Test
    void testLoadInvalidFile() {
        assertThrows(IOException.class, () -> cacheService.load("nonExistingFile.dat"));
    }

}
