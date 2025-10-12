package com.devives.jetty.container;

import com.devives.jetty.container.contract.Environment;
import org.junit.jupiter.api.*;

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class Jetty12ContainerTest {

    private static final Path rootPath = Paths.get("").toAbsolutePath();
    private static final Path jettyHomePath = rootPath.resolve("jetty-home-12.1.1");
    private static final Path jettyInjectionBasePath = rootPath.resolve("container");
    private static final Path jettyBasePath = rootPath.resolve("jetty-base");
    //    private static final Path jettyBasePath = rootPath.resolve("jetty-base");
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
                .addStartJar(jettyInjectionBasePath.resolve("lib\\container-context-0.1.0.jar"))
                .addLibrary(jettyInjectionBasePath.resolve("lib\\container-injection-0.1.0.jar"))
                .addStartArgument(jettyInjectionBasePath.resolve("etc\\jetty-container-injection.xml").toString());
    }

    private void doStartStop(int httpPort, int httpsPort) throws Exception {
        Container container = createContainerBuilder()
                .setHttpPort(httpPort)
                .setHttpsPort(httpsPort)
                .setJettyBase(jettyBasePath)
                // В `demo-base\etc\test-realm.xml` прописан относительный путь `etc/realm.properties`, что приводит к ошибке.
                // .addStartArgument("jetty.demo.realm", jettyBasePath.resolve("etc/realm.properties").toString())
                // .addStartArgument("help")
                //.addStartArgument("--jpms")
                .build();
        Assertions.assertThrows(ContainerException.class, container::getServer);
        container.start();
        Server serverAdapter = container.getServer();
        Assertions.assertNotNull(serverAdapter.getClassLoader());
        Environment.Static.at(serverAdapter.getClassLoader()).getAll().forEach(environment -> logger.info("Environment{name=\"" + environment.getName() + "\"}"));
        Environment environmentEE8 = Environment.Static.at(serverAdapter.getClassLoader()).get("ee8");
        Assertions.assertNotNull(environmentEE8);
        Assertions.assertNotNull(environmentEE8.getClassLoader());
        Assertions.assertTrue(serverAdapter.isStarted());
        ((ServerAdapter) serverAdapter).getStartFuture().toCompletableFuture().get();
        logger.info("Server started.");

        validateServerAvailable(httpPort);

        logger.info("Sleep(1000)");
        Thread.sleep(1000);
        logger.info("Stop the server.");
        container.stop();
        Assertions.assertTrue(serverAdapter.isStopped());
        ((ServerAdapter) serverAdapter).getStopFuture().toCompletableFuture().get();
        logger.info("Server stopped.");
        Assertions.assertThrows(ContainerException.class, container::getServer);
        logger.info("Exit test.");
    }

    private void doStartStopAsync(int httpPort, int httpsPort) throws Exception {
        Container container = createContainerBuilder()
                .setHttpPort(httpPort)
                .setHttpsPort(httpsPort)
                .setJettyBase(jettyBasePath)
                // В `demo-base\etc\test-realm.xml` прописан относительный путь `etc/realm.properties`, что приводит к ошибке.
                // .addStartArgument("jetty.demo.realm", jettyBasePath.resolve("etc/realm.properties").toString())
                .setStopPort(0) // Запуск сокета остановки на произвольном порте.
                //.setStopKey("dfgukhdasflgkdf") // Jetty сгенерирует произвольный ключ.
                .build();
        Assertions.assertThrows(ContainerException.class, container::getServer);
        container.start();
        Server server = container.getServer();
        Assertions.assertTrue(server.isStarted());
        ((ServerAdapter) server).getStartFuture().toCompletableFuture().get();
        logger.info("Server started.");

        validateServerAvailable(httpPort);

        logger.info("Sleep(1000)");
        Thread.sleep(1000);
        logger.info("Stop the server.");
        container.stopAsync().get();
        Assertions.assertTrue(server.isStopped());
        ((ServerAdapter) server).getStopFuture().toCompletableFuture().get();
        logger.info("Server stopped.");
        Assertions.assertThrows(ContainerException.class, container::getServer);
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
     * Сервер остановится только поле остановки JVM.
     * Если раскомментировать, будут падать тесты, которые запустятся после этого.
     */
    @Test
    @Order(Integer.MAX_VALUE)
    public void startStop_OnJvmExit() throws Exception {
        Container container = createContainerBuilder()
                .setJettyBase(jettyBasePath)
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
                .get(new URL("http://localhost:" + httpPort + "/")) //
                .send();
        Assertions.assertEquals(200, response.getStatusCode());
    }
}
