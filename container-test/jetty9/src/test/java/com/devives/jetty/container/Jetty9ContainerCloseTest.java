package com.devives.jetty.container;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Регрессионный тест: {@link Container#close()} должен останавливать запущенный Jetty-сервер
 * ({@code close()} эквивалентен {@code stop()} плюс удаление временных файлов инъекций).
 * <p>
 * Ранее в {@code Container.close()} условие было перевёрнуто: {@code if (!isStarted()) { stop(); }} —
 * для запущенного контейнера {@code close()} сервер не останавливал (Jetty продолжал отвечать
 * на HTTP-запросы, start-classloader не закрывался). До исправления тест падал на
 * {@code assertFalse(server.isRunning())}. Существующие тесты баг не обнаруживали, т.к. явно
 * вызывали {@code container.stop()} до выхода из try-with-resources.
 * <p>
 * Порты 18080/18443 — чтобы не конфликтовать с остальными тестами (8080/9080),
 * в т.ч. с {@code startStop_OnJvmExit}, который держит 8080 до выхода JVM.
 */
public class Jetty9ContainerCloseTest {

    private static final Path rootPath = Paths.get("").toAbsolutePath();
    private static final Path tempPath = rootPath.resolve("build").resolve("test-temp");
    private static final Path jettyHomePath = rootPath.resolve("jetty-distribution-9.4.58.v20250814");
    private static final Path jettyBasePath = jettyHomePath.resolve("demo-base");
    private static final int HTTP_PORT = 18080;
    private static final int HTTPS_PORT = 18443;

    @BeforeAll
    static void beforeAll() throws IOException {
        Files.createDirectories(tempPath);
    }

    @Test
    public void close_shouldStopStartedServer() throws Exception {
        Container container = new ContainerBuilder(Thread.currentThread().getContextClassLoader(), jettyHomePath)
                .setJettyBase(jettyBasePath)
                .setTempPath(tempPath)
                .setHttpPort(HTTP_PORT)
                .setHttpsPort(HTTPS_PORT)
                // В `demo-base\etc\test-realm.xml` прописан относительный путь `etc/realm.properties`, что приводит к ошибке.
                .addStartArgument("jetty.demo.realm", jettyBasePath.resolve("etc/realm.properties").toString())
                .build();
        try {
            container.start();
            Server server = container.getServer();
            Assertions.assertTrue(server.isStarted(), "Сервер должен быть запущен перед close().");
            validateServerAvailable(HTTP_PORT);

            container.close(); // БЕЗ предварительного container.stop().

            // Ожидание: close() эквивалентен stop() — сервер остановлен.
            Assertions.assertFalse(server.isRunning(),
                    "close() должен остановить Jetty-сервер, но сервер остался запущен"
                            + " (Container.close(): if (!isStarted()) stop();).");
            Assertions.assertTrue(server.isStopped(), "close() должен перевести сервер в состояние STOPPED.");
            Assertions.assertThrows(ContainerException.class, container::getServer,
                    "После close() контейнер не должен возвращать сервер.");
        } finally {
            // Уборка: если close() не остановил сервер (баг активен) — останавливаю принудительно,
            // чтобы не оставить запущенный сервер на порту для следующих тестов.
            try {
                container.stop();
            } catch (Exception ignore) {
            }
        }
    }

    private void validateServerAvailable(int httpPort) throws Exception {
        HttpURLConnectionResponse response = new HttpURLConnectionBuilder()
                .get(URI.create("http://localhost:" + httpPort + "/").toURL())
                .send();
        Assertions.assertEquals(200, response.getStatusCode());
    }
}
