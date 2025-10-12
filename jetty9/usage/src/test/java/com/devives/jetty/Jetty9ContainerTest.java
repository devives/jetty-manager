package com.devives.jetty;

import com.devives.jetty.contract.Environment;
import org.junit.jupiter.api.*;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class Jetty9ContainerTest {

    private static final Path rootPath = Paths.get("").toAbsolutePath();
    private static final Path jettyHomePath = rootPath.resolve("jetty-distribution-9.4.58.v20250814");
    private static final Path jettyInjectionBasePath = rootPath.resolve("jetty-container-base");
    private static final Path jettyBasePath = jettyHomePath.resolve("demo-base");
    private final Logger logger = Logger.getLogger(getClass().getCanonicalName());

    @Test
    public void startStop() throws Exception {
        doStartStop();
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
        doStartStop(8080, 8443);
    }

    private void doStartStopAsync() throws Exception {
        doStartStopAsync(8080, 8443);
    }

    private ContainerBuilder createContainerBuilder() {
        return new ContainerBuilder(Thread.currentThread().getContextClassLoader(), jettyHomePath)
                //.setIncludeJettyDir(jettyInjectionBasePath)
                .addStartJar(jettyInjectionBasePath.resolve("lib\\container-starter-0.1.0.jar"))
                .addLibrary(jettyInjectionBasePath.resolve("lib\\container-injection-0.1.0.jar"))
                .addStartArgument(jettyInjectionBasePath.resolve("etc\\jetty-container-injection.xml").toString());
    }

    private void doStartStop(int httpPort, int httpsPort) throws Exception {
        Container container = createContainerBuilder()
                .setHttpPort(httpPort)
                .setHttpsPort(httpsPort)
                .setJettyBase(jettyBasePath)
                // В `demo-base\etc\test-realm.xml` прописан относительный путь `etc/realm.properties`, что приводит к ошибке.
                .addStartArgument("jetty.demo.realm", jettyBasePath.resolve("etc/realm.properties").toString())
                // .addStartArgument("--exec-properties", jettyBasePath.resolve("etc/realm.properties").toString())
                // .addStartArgument("help")
                //.addStartArgument("--jpms")
                .build();
        Assertions.assertThrows(ContainerException.class, container::getServerAdapter);
        container.start();
        ServerAdapter serverAdapter = container.getServerAdapter();
        Assertions.assertTrue(serverAdapter.isStarted());
        ((ServerAdapterImpl) serverAdapter).getStartFuture().toCompletableFuture().get();
        logger.info("Server started.");

        Assertions.assertThrows(ClassNotFoundException.class, () -> {
            Environment.Static.at(serverAdapter.getClassLoader()).get("ee8");
        });

        validateServerAvailable(httpPort);

        logger.info("Sleep(1000)");
        Thread.sleep(1000);
        logger.info("Stop the server.");
        container.stop();
        Assertions.assertTrue(serverAdapter.isStopped());
        ((ServerAdapterImpl) serverAdapter).getStopFuture().toCompletableFuture().get();
        logger.info("Server stopped.");
        Assertions.assertThrows(ContainerException.class, container::getServerAdapter);
        logger.info("Exit test.");
    }


    private void doStartStopAsync(int httpPort, int httpsPort) throws Exception {
        Container container = createContainerBuilder()
                .setHttpPort(httpPort)
                .setHttpsPort(httpsPort)
                .setJettyBase(jettyBasePath)
                // В `demo-base\etc\test-realm.xml` прописан относительный путь `etc/realm.properties`, что приводит к ошибке.
                .addStartArgument("jetty.demo.realm", jettyBasePath.resolve("etc/realm.properties").toString())
                .setStopPort(0) // Запуск сокета остановки на произвольном порте.
                //.setStopKey("dfgukhdasflgkdf") // Jetty сгенерирует произвольный ключ.
                .build();
        Assertions.assertThrows(ContainerException.class, container::getServerAdapter);
        container.start();
        ServerAdapter serverAdapter = container.getServerAdapter();
        Assertions.assertTrue(serverAdapter.isStarted());
        ((ServerAdapterImpl) serverAdapter).getStartFuture().toCompletableFuture().get();
        logger.info("Server started.");

        validateServerAvailable(httpPort);

        logger.info("Sleep(1000)");
        Thread.sleep(1000);
        logger.info("Stop the server.");
        container.stopAsync().get();
        Assertions.assertTrue(serverAdapter.isStopped());
        ((ServerAdapterImpl) serverAdapter).getStopFuture().toCompletableFuture().get();
        logger.info("Server stopped.");
        Assertions.assertThrows(ContainerException.class, container::getServerAdapter);
        logger.info("Exit test.");
    }

    @Test
    public void startStop_ParallelThreads() throws Exception {
        final CompletableFuture<Void> future1 = new CompletableFuture<>();
        final CompletableFuture<Void> future2 = new CompletableFuture<>();
        final Thread thread1 = new Thread(() -> {
            try {
                doStartStop(8080, 8443);
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
     * Тест запускается последним, т.к. после выхода из теста сервер не останавливается и не освобождает порт.
     * Сервер остановиться только поле остановки JVM.
     * Если раскомментировать, будут падать тесты, которые запустятся после этого.
     */
    @Test
    @Order(Integer.MAX_VALUE)
    public void startStop_OnJvmExit() throws Exception {
        Container container = createContainerBuilder()
                .setJettyBase(jettyBasePath)
                // В `demo-base\etc\test-realm.xml` прописан относительный путь `etc/realm.properties`, что приводит к ошибке.
                .addStartArgument("jetty.demo.realm", jettyBasePath.resolve("etc/realm.properties").toString())
                .build();
        container.start();
        ServerAdapter serverAdapter = container.getServerAdapter();
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
