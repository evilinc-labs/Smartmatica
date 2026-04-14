package dev.moar.api;

import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// Lightweight embedded HTTP server exposing REST + Prometheus endpoints
// for Grafana dashboards, n8n webhooks, and external tooling.
public final class ApiServer implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger("MOAR/API");

    private final MoarProperties config;
    private HttpServer server;
    private ExecutorService executor;
    private volatile boolean running;

    public ApiServer(MoarProperties config) {
        this.config = config;
    }

    public boolean start() {
        if (!config.isApiEnabled()) return false;
        if (running) return true;

        try {
            server = HttpServer.create(
                    new InetSocketAddress(config.getApiBindAddress(), config.getApiPort()), 0);
            executor = Executors.newFixedThreadPool(2);
            server.setExecutor(executor);

            ApiHandler h = new ApiHandler(config);

            server.createContext("/api/v1/status", h::handleStatus);
            server.createContext("/api/v1/containers", h::handleContainers);
            server.createContext("/api/v1/search", h::handleSearch);
            server.createContext("/api/v1/stats", h::handleStats);
            server.createContext("/api/v1/metrics", h::handleMetrics);
            server.createContext("/api/v1/organizer", h::handleOrganizer);
            server.createContext("/api/v1/webhook/test", h::handleWebhookTest);

            server.start();
            running = true;
            LOGGER.info("API server listening on {}:{}", config.getApiBindAddress(), config.getApiPort());
            return true;
        } catch (IOException e) {
            LOGGER.error("Failed to start API server on port {}", config.getApiPort(), e);
            return false;
        }
    }

    public boolean isRunning() { return running; }

    @Override
    public void close() {
        if (server != null) {
            server.stop(1);
            running = false;
        }
        if (executor != null) {
            executor.shutdownNow();
        }
        LOGGER.info("API server stopped.");
    }
}
