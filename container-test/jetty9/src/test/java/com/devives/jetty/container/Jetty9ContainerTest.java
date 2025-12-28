package com.devives.jetty.container;

import com.devives.jetty.container.contract.Environment;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class Jetty9ContainerTest {

    private static final Path rootPath = Paths.get("").toAbsolutePath();
    private static final Path tempPath = rootPath.resolve("build").resolve("test-temp");
    private static final Path jettyHomePath = rootPath.resolve("jetty-distribution-9.4.58.v20250814");
    private static final Path jettyBasePath = jettyHomePath.resolve("demo-base");
    private final Logger logger = Logger.getLogger(getClass().getCanonicalName());

    @BeforeAll
    static void beforeAll() throws IOException {
        Files.createDirectories(tempPath);
    }

    @Test
    public void startStop() throws Exception {
        doStartStop();
    }

    @Test
    public void startStop_manualInjections() throws Exception {
        doStartStop(8080, 8443, true);
    }

    @Test
    public void startStop_Async() throws Exception {
        doStartStopAsync();
    }

    @Test
    public void startStop_Repeatedly() throws Exception {
        for (int i = 0; i < 2; i++) {
            doStartStop();
        }
    }

    private void doStartStop() throws Exception {
        doStartStop(8080, 8443, false);
    }

    private void doStartStopAsync() throws Exception {
        doStartStopAsync(8080, 8443);
    }

    private ContainerBuilder createContainerBuilder(boolean manualInjection) {
        ContainerBuilder builder = new ContainerBuilder(Thread.currentThread().getContextClassLoader(), jettyHomePath)
                //.setIncludeJettyDir(jettyInjectionBasePath)
                .setJettyBase(jettyBasePath)
                .setTempPath(tempPath)
                // В `demo-base\etc\test-realm.xml` прописан относительный путь `etc/realm.properties`, что приводит к ошибке.
                .addStartArgument("jetty.demo.realm", jettyBasePath.resolve("etc/realm.properties").toString());
        if (manualInjection) {
            Path projectGroupRoot = rootPath.getParent().getParent();
            Path resorcesPath = projectGroupRoot.resolve("container/src/main/resources/com/devives/jetty/container");
            builder.disableInjections()
                    .addStartJar(projectGroupRoot.resolve(resorcesPath.resolve("lib/devive-jetty-container-context.jar")))
                    .addLibrary(projectGroupRoot.resolve(resorcesPath.resolve("lib/devive-jetty-container-injection.jar")))
                    .addStartArgument(projectGroupRoot.resolve(resorcesPath.resolve("etc/jetty9/container-injection.xml")).toString());
        }
        return builder;
    }

    private void doStartStop(int httpPort, int httpsPort, boolean manualInjections) throws Exception {
        try (Container container = createContainerBuilder(manualInjections)
                .setHttpPort(httpPort)
                .setHttpsPort(httpsPort)
                .setStopPort(0)
                // .addStartArgument("--exec-properties", jettyBasePath.resolve("etc/realm.properties").toString())
                // .addStartArgument("help")
                //.addStartArgument("--jpms")
                .build()) {
            doStartStop(container);
            //doStartStop(container);
        }
    }


    private void doStartStop(Container container) throws Exception {
        Assertions.assertThrows(ContainerException.class, container::getServer);
        container.addListener(new Container.Listener() {
            @Override
            public void onStarting(Container object) {
                Assertions.assertThrows(ContainerException.class, object::getServer);
            }

            @Override
            public void onServerConstructed(Object jettyServer) {
                Assertions.assertThrows(ContainerException.class, container::getServer);
                ClassLoader jettyClassLoader = jettyServer.getClass().getClassLoader();
                Assertions.assertNotNull(jettyClassLoader);
            }

            @Override
            public void onStarted(Container object) {
                Assertions.assertNotNull(object.getServer());
            }

            @Override
            public void onStopping(Container object) {
                Assertions.assertNotNull(object.getServer());
            }

            @Override
            public void onStopped(Container object) {
                Assertions.assertThrows(ContainerException.class, container::getServer);
            }
        });
        container.start();
        Server server = container.getServer();
        Assertions.assertTrue(server.isStarted());
        ((ServerAdapter) server).getStartFuture().toCompletableFuture().get();
        logger.info("Server started.");

        Assertions.assertThrows(ClassNotFoundException.class, () -> {
            Environment.statics(server.getClassLoader()).getAll();
        });

        validateServerAvailable(container.getHttpPort());

        logger.info("Sleep(1000)");
        Thread.sleep(1000);
        logger.info("Stop the server.");
        container.stop();
        Assertions.assertTrue(server.isStopped());
        ((ServerAdapter) server).getStopFuture().toCompletableFuture().get();
        logger.info("Server stopped.");
        Assertions.assertThrows(ContainerException.class, container::getServer);
        logger.info("Exit test.");

    }


    private void doStartStopAsync(int httpPort, int httpsPort) throws Exception {
        try (Container container = createContainerBuilder(false)
                .setHttpPort(httpPort)
                .setHttpsPort(httpsPort)
                .setStopPort(0) // Запуск сокета остановки на произвольном порте.
                .setStopTimeout(15)
                //.setStopKey("dfgukhdasflgkdf") // Jetty сгенерирует произвольный ключ.
                .build()) {
            Assertions.assertThrows(ContainerException.class, container::getServer);
            container.start();
            Server serverAdapter = container.getServer();
            Assertions.assertTrue(serverAdapter.isStarted());
            ((ServerAdapter) serverAdapter).getStartFuture().toCompletableFuture().get();
            logger.info("Server started.");

            validateServerAvailable(httpPort);

            logger.info("Sleep(1000)");
            Thread.sleep(1000);
            logger.info("Stop the server.");
            container.stopAsync().get();
            Assertions.assertTrue(serverAdapter.isStopped());
            ((ServerAdapter) serverAdapter).getStopFuture().toCompletableFuture().get();
            logger.info("Server stopped.");
            Assertions.assertThrows(ContainerException.class, container::getServer);
            logger.info("Exit test.");
        }
    }

    @Test
    public void startStop_ParallelThreads() throws Exception {
        final CompletableFuture<Void> future1 = new CompletableFuture<>();
        final CompletableFuture<Void> future2 = new CompletableFuture<>();
        final Thread thread1 = new Thread(() -> {
            try {
                doStartStop(8080, 8443, false);
                future1.complete(null);
            } catch (Exception e) {
                future1.completeExceptionally(e);
            }
        });
        final Thread thread2 = new Thread(() -> {
            try {
                doStartStopAsync(9080, 9443);
                future2.complete(null);
            } catch (Exception e) {
                future2.completeExceptionally(e);
            }
        });
        thread1.start();
        thread2.start();
        future1.get();
        future2.get();
    }

    /**
     * Тест остановки Jetty-сервера в результате остановки JMV (пример: нажатие Ctrl+C в консоли).
     * Тест запускается последним, т.к. иначе сервер не останавливается и не освобождает порт.
     * Сервер остановится только поле остановки JVM.
     */
    @Test
    @Order(Integer.MAX_VALUE)
    public void startStop_OnJvmExit() throws Exception {
        // Закрытие контейнера выполняется в com.devives.jetty.container.Container.handleServerAdapterClose(Void)
        final Container container = createContainerBuilder(false)
                .setJettyBase(jettyBasePath)
                // В `demo-base\etc\test-realm.xml` прописан относительный путь `etc/realm.properties`, что приводит к ошибке.
                .addStartArgument("jetty.demo.realm", jettyBasePath.resolve("etc/realm.properties").toString())
                .build();
        container.start();
        Server serverAdapter = container.getServer();
        Assertions.assertTrue(serverAdapter.isStarted());
        logger.info("Server started.");

        validateServerAvailable(8080);
        logger.info("Sleep(1000)");
        Thread.sleep(1000);
        logger.info("Exit test.");
    }

    private void validateServerAvailable(int httpPort) throws Exception {
        HttpURLConnectionResponse response = new HttpURLConnectionBuilder()
                .get(URI.create("http://localhost:" + httpPort + "/").toURL()) //
                .send();
        Assertions.assertEquals(200, response.getStatusCode());
    }
}
