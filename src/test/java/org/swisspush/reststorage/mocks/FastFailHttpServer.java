package org.swisspush.reststorage.mocks;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.streams.ReadStream;


public class FastFailHttpServer implements HttpServer {

    private static final String msg = "No behaviour specified. Override to implement your behaviour.";

    @Override
    public ReadStream<HttpServerRequest> requestStream() {
        throw new UnsupportedOperationException(msg);
    }

    @Override
    public HttpServer requestHandler(Handler<HttpServerRequest> handler) {
        throw new UnsupportedOperationException(msg);
    }

    @Override
    public Handler<HttpServerRequest> requestHandler() {
        throw new UnsupportedOperationException(msg);
    }

    @Override
    public HttpServer connectionHandler(Handler<HttpConnection> handler) {
        throw new UnsupportedOperationException(msg);
    }

    @Override
    public HttpServer exceptionHandler(Handler<Throwable> handler) {
        throw new UnsupportedOperationException(msg);
    }

    @Override
    public ReadStream<ServerWebSocket> websocketStream() {
        throw new UnsupportedOperationException(msg);
    }

    @Override
    public HttpServer websocketHandler(Handler<ServerWebSocket> handler) {
        throw new UnsupportedOperationException(msg);
    }

    @Override
    public Handler<ServerWebSocket> websocketHandler() {
        throw new UnsupportedOperationException(msg);
    }

    @Override
    public HttpServer listen() {
        throw new UnsupportedOperationException(msg);
    }

    @Override
    public HttpServer listen(int port, String host) {
        throw new UnsupportedOperationException(msg);
    }

    @Override
    public HttpServer listen(int port, String host, Handler<AsyncResult<HttpServer>> listenHandler) {
        throw new UnsupportedOperationException(msg);
    }

    @Override
    public HttpServer listen(int port) {
        throw new UnsupportedOperationException(msg);
    }

    @Override
    public HttpServer listen(int port, Handler<AsyncResult<HttpServer>> listenHandler) {
        throw new UnsupportedOperationException(msg);
    }

    @Override
    public HttpServer listen(Handler<AsyncResult<HttpServer>> listenHandler) {
        throw new UnsupportedOperationException(msg);
    }

    @Override
    public void close() {
        throw new UnsupportedOperationException(msg);
    }

    @Override
    public void close(Handler<AsyncResult<Void>> completionHandler) {
        throw new UnsupportedOperationException(msg);
    }

    @Override
    public int actualPort() {
        throw new UnsupportedOperationException(msg);
    }

    @Override
    public boolean isMetricsEnabled() {
        throw new UnsupportedOperationException(msg);
    }
}
