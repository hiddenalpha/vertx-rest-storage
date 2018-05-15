package org.swisspush.reststorage.mocks;

import io.netty.channel.EventLoopGroup;
import io.vertx.core.*;
import io.vertx.core.datagram.DatagramSocket;
import io.vertx.core.datagram.DatagramSocketOptions;
import io.vertx.core.dns.DnsClient;
import io.vertx.core.dns.DnsClientOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.file.FileSystem;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.shareddata.SharedData;
import io.vertx.core.spi.VerticleFactory;

import java.util.Set;
import java.util.function.Supplier;


public class VertxProxy implements Vertx {

    private final Vertx delegate;

    public VertxProxy( Vertx delegate ) {
        this.delegate = delegate;
    }

    public static Vertx vertx() {
        return Vertx.vertx();
    }

    public static Vertx vertx(VertxOptions options) {
        return Vertx.vertx(options);
    }

    public static void clusteredVertx(VertxOptions options, Handler<AsyncResult<Vertx>> resultHandler) {
        Vertx.clusteredVertx(options, resultHandler);
    }

    public static Context currentContext() {
        return Vertx.currentContext();
    }

    public Context getOrCreateContext() {
        return delegate.getOrCreateContext();
    }

    public NetServer createNetServer(NetServerOptions options) {
        return delegate.createNetServer(options);
    }

    public NetServer createNetServer() {
        return delegate.createNetServer();
    }

    public NetClient createNetClient(NetClientOptions options) {
        return delegate.createNetClient(options);
    }

    public NetClient createNetClient() {
        return delegate.createNetClient();
    }

    public HttpServer createHttpServer(HttpServerOptions options) {
        return delegate.createHttpServer(options);
    }

    public HttpServer createHttpServer() {
        return delegate.createHttpServer();
    }

    public HttpClient createHttpClient(HttpClientOptions options) {
        return delegate.createHttpClient(options);
    }

    public HttpClient createHttpClient() {
        return delegate.createHttpClient();
    }

    public DatagramSocket createDatagramSocket(DatagramSocketOptions options) {
        return delegate.createDatagramSocket(options);
    }

    public DatagramSocket createDatagramSocket() {
        return delegate.createDatagramSocket();
    }

    public FileSystem fileSystem() {
        return delegate.fileSystem();
    }

    public EventBus eventBus() {
        return delegate.eventBus();
    }

    public DnsClient createDnsClient(int port, String host) {
        return delegate.createDnsClient(port, host);
    }

    public DnsClient createDnsClient() {
        return delegate.createDnsClient();
    }

    public DnsClient createDnsClient(DnsClientOptions options) {
        return delegate.createDnsClient(options);
    }

    public SharedData sharedData() {
        return delegate.sharedData();
    }

    public long setTimer(long delay, Handler<Long> handler) {
        return delegate.setTimer(delay, handler);
    }

    public TimeoutStream timerStream(long delay) {
        return delegate.timerStream(delay);
    }

    public long setPeriodic(long delay, Handler<Long> handler) {
        return delegate.setPeriodic(delay, handler);
    }

    public TimeoutStream periodicStream(long delay) {
        return delegate.periodicStream(delay);
    }

    public boolean cancelTimer(long id) {
        return delegate.cancelTimer(id);
    }

    public void runOnContext(Handler<Void> action) {
        delegate.runOnContext(action);
    }

    public void close() {
        delegate.close();
    }

    public void close(Handler<AsyncResult<Void>> completionHandler) {
        delegate.close(completionHandler);
    }

    public void deployVerticle(Verticle verticle) {
        delegate.deployVerticle(verticle);
    }

    public void deployVerticle(Verticle verticle, Handler<AsyncResult<String>> completionHandler) {
        delegate.deployVerticle(verticle, completionHandler);
    }

    public void deployVerticle(Verticle verticle, DeploymentOptions options) {
        delegate.deployVerticle(verticle, options);
    }

    public void deployVerticle(Class<? extends Verticle> verticleClass, DeploymentOptions options) {
        delegate.deployVerticle(verticleClass, options);
    }

    public void deployVerticle(Supplier<Verticle> verticleSupplier, DeploymentOptions options) {
        delegate.deployVerticle(verticleSupplier, options);
    }

    public void deployVerticle(Verticle verticle, DeploymentOptions options, Handler<AsyncResult<String>> completionHandler) {
        delegate.deployVerticle(verticle, options, completionHandler);
    }

    public void deployVerticle(Class<? extends Verticle> verticleClass, DeploymentOptions options, Handler<AsyncResult<String>> completionHandler) {
        delegate.deployVerticle(verticleClass, options, completionHandler);
    }

    public void deployVerticle(Supplier<Verticle> verticleSupplier, DeploymentOptions options, Handler<AsyncResult<String>> completionHandler) {
        delegate.deployVerticle(verticleSupplier, options, completionHandler);
    }

    public void deployVerticle(String name) {
        delegate.deployVerticle(name);
    }

    public void deployVerticle(String name, Handler<AsyncResult<String>> completionHandler) {
        delegate.deployVerticle(name, completionHandler);
    }

    public void deployVerticle(String name, DeploymentOptions options) {
        delegate.deployVerticle(name, options);
    }

    public void deployVerticle(String name, DeploymentOptions options, Handler<AsyncResult<String>> completionHandler) {
        delegate.deployVerticle(name, options, completionHandler);
    }

    public void undeploy(String deploymentID) {
        delegate.undeploy(deploymentID);
    }

    public void undeploy(String deploymentID, Handler<AsyncResult<Void>> completionHandler) {
        delegate.undeploy(deploymentID, completionHandler);
    }

    public Set<String> deploymentIDs() {
        return delegate.deploymentIDs();
    }

    public void registerVerticleFactory(VerticleFactory factory) {
        delegate.registerVerticleFactory(factory);
    }

    public void unregisterVerticleFactory(VerticleFactory factory) {
        delegate.unregisterVerticleFactory(factory);
    }

    public Set<VerticleFactory> verticleFactories() {
        return delegate.verticleFactories();
    }

    public boolean isClustered() {
        return delegate.isClustered();
    }

    public <T> void executeBlocking(Handler<Future<T>> blockingCodeHandler, boolean ordered, Handler<AsyncResult<T>> asyncResultHandler) {
        delegate.executeBlocking(blockingCodeHandler, ordered, asyncResultHandler);
    }

    public <T> void executeBlocking(Handler<Future<T>> blockingCodeHandler, Handler<AsyncResult<T>> asyncResultHandler) {
        delegate.executeBlocking(blockingCodeHandler, asyncResultHandler);
    }

    public EventLoopGroup nettyEventLoopGroup() {
        return delegate.nettyEventLoopGroup();
    }

    public WorkerExecutor createSharedWorkerExecutor(String name) {
        return delegate.createSharedWorkerExecutor(name);
    }

    public WorkerExecutor createSharedWorkerExecutor(String name, int poolSize) {
        return delegate.createSharedWorkerExecutor(name, poolSize);
    }

    public WorkerExecutor createSharedWorkerExecutor(String name, int poolSize, long maxExecuteTime) {
        return delegate.createSharedWorkerExecutor(name, poolSize, maxExecuteTime);
    }

    public boolean isNativeTransportEnabled() {
        return delegate.isNativeTransportEnabled();
    }

    public Vertx exceptionHandler(Handler<Throwable> handler) {
        return delegate.exceptionHandler(handler);
    }

    public Handler<Throwable> exceptionHandler() {
        return delegate.exceptionHandler();
    }

    public boolean isMetricsEnabled() {
        return delegate.isMetricsEnabled();
    }
}
