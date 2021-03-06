package org.swisspush.reststorage;

import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.buffer.impl.BufferImpl;
import io.vertx.core.http.CaseInsensitiveHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.logging.Logger;
import io.vertx.core.streams.WriteStream;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.RoutingContext;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.swisspush.reststorage.mocks.*;
import org.swisspush.reststorage.util.HttpRequestHeader;
import org.swisspush.reststorage.util.LockMode;
import org.swisspush.reststorage.util.ModuleConfiguration;
import org.swisspush.reststorage.util.StatusCode;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests for the {@link RestStorageHandler} class
 *
 * @author https://github.com/mcweba [Marc-Andre Weber]
 */
@RunWith(VertxUnitRunner.class)
public class RestStorageHandlerTest {

    private Vertx vertx;
    private Storage storage;
    private RestStorageHandler restStorageHandler;
    private Logger log;

    private HttpServerRequest request;
    private HttpServerResponse response;


    @Before
    public void setUp(TestContext context) {
        vertx = Vertx.vertx();
        storage = mock(Storage.class);
        log = mock(Logger.class);

        request = Mockito.mock(HttpServerRequest.class);
        response = Mockito.mock(HttpServerResponse.class);

        when(request.method()).thenReturn(HttpMethod.PUT);
        when(request.uri()).thenReturn("/some/resource");
        when(request.path()).thenReturn("/some/resource");
        when(request.query()).thenReturn("");
        when(request.pause()).thenReturn(request);
        when(request.resume()).thenReturn(request);
        when(request.response()).thenReturn(response);
        when(request.headers()).thenReturn(new CaseInsensitiveHeaders());
    }

    @Test
    public void testPUTWithInvalidImportanceLevelHeader(TestContext testContext) {
        ModuleConfiguration config = new ModuleConfiguration().prefix("/").rejectStorageWriteOnLowMemory(true);
        restStorageHandler = new RestStorageHandler(vertx, log, storage, config);

        // ARRANGE
        when(request.headers()).thenReturn(new CaseInsensitiveHeaders().add(HttpRequestHeader.IMPORTANCE_LEVEL_HEADER.getName(), "not_a_number"));

        // ACT
        restStorageHandler.handle(request);

        // ASSERT
        verify(response, times(1)).setStatusCode(eq(StatusCode.BAD_REQUEST.getStatusCode()));
        verify(response, times(1)).setStatusMessage(eq(StatusCode.BAD_REQUEST.getStatusMessage()));
        verify(response, times(1)).end(eq("Invalid x-importance-level header: not_a_number"));
        verify(log, times(1)).error(
                eq("Rejecting PUT request to /some/resource because x-importance-level header, has an invalid value: not_a_number"));
    }

    @Test
    public void testPUTWithEnabledRejectStorageWriteOnLowMemoryButNoHeaders(TestContext testContext) {
        ModuleConfiguration config = new ModuleConfiguration().prefix("/").rejectStorageWriteOnLowMemory(true);
        restStorageHandler = new RestStorageHandler(vertx, log, storage, config);

        // ACT
        restStorageHandler.handle(request);

        // ASSERT
        verify(log, times(1)).info(
                eq("Received PUT request to /some/resource without x-importance-level header. " +
                        "Going to handle this request with highest importance"));
    }

    @Test
    public void testPUTWithDisabledRejectStorageWriteOnLowMemoryButHeaders(TestContext testContext) {
        ModuleConfiguration config = new ModuleConfiguration().prefix("/").rejectStorageWriteOnLowMemory(false);
        restStorageHandler = new RestStorageHandler(vertx, log, storage, config);

        // ARRANGE
        when(request.headers()).thenReturn(new CaseInsensitiveHeaders().add(HttpRequestHeader.IMPORTANCE_LEVEL_HEADER.getName(), "50"));

        // ACT
        restStorageHandler.handle(request);

        // ASSERT
        verify(log, times(1)).warn(
                eq("Received request with x-importance-level header, but rejecting storage writes on " +
                        "low memory feature is disabled"));
    }

    @Test
    public void testPUTWithNoMemoryUsageAvailable(TestContext testContext) {
        ModuleConfiguration config = new ModuleConfiguration().prefix("/").rejectStorageWriteOnLowMemory(true);
        restStorageHandler = new RestStorageHandler(vertx, log, storage, config);

        // ARRANGE
        when(request.headers()).thenReturn(new CaseInsensitiveHeaders().add(HttpRequestHeader.IMPORTANCE_LEVEL_HEADER.getName(), "50"));
        when(storage.getCurrentMemoryUsage()).thenReturn(Optional.empty());

        // ACT
        restStorageHandler.handle(request);

        // ASSERT
        verify(log, times(1)).warn(
                eq("Rejecting storage writes on low memory feature disabled, because current memory usage not available"));
    }

    @Test
    public void testRejectPUTRequestWhenMemoryUsageHigherThanImportanceLevel(TestContext testContext) {
        ModuleConfiguration config = new ModuleConfiguration().prefix("/").rejectStorageWriteOnLowMemory(true);
        restStorageHandler = new RestStorageHandler(vertx, log, storage, config);

        // ARRANGE
        when(request.headers()).thenReturn(new CaseInsensitiveHeaders().add(HttpRequestHeader.IMPORTANCE_LEVEL_HEADER.getName(), "50"));
        when(storage.getCurrentMemoryUsage()).thenReturn(Optional.of(75f));

        // ACT
        restStorageHandler.handle(request);

        // ASSERT
        verify(response, times(1)).setStatusCode(eq(StatusCode.INSUFFICIENT_STORAGE.getStatusCode()));
        verify(response, times(1)).setStatusMessage(eq(StatusCode.INSUFFICIENT_STORAGE.getStatusMessage()));
        verify(response, times(1)).end(eq(StatusCode.INSUFFICIENT_STORAGE.getStatusMessage()));
        verify(log, times(1)).info(
                eq("Rejecting PUT request to /some/resource because current memory usage of 75% is higher than provided importance level of 50%"));
    }

    @Test
    public void notifiesResourceAboutExceptionsOnRequest(TestContext testContext) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {

        // Force access to method because mocking Router.router(Vertx) would be much
        // harder...
        final Method putResourceMethod;
        {
            putResourceMethod = RestStorageHandler.class.getDeclaredMethod("putResource", RoutingContext.class);
            putResourceMethod.setAccessible( true );
        }

        // Keep track of state during test
        final boolean[] errorHandlerGotCalledPtr = new boolean[]{ false };

        // Mock victim instance
        final RestStorageHandler victim;
        {
            final Vertx mockedVertx = new FailFastVertx();
            final Storage mockedStorage = new FailFastRestStorage(){
                @Override public void put(String path, String etag, boolean merge, long expire, String lockOwner, LockMode lockMode, long lockExpire, boolean storeCompressed, Handler<Resource> handler) {
                    final DocumentResource resource = new DocumentResource();
                    resource.writeStream = new FailFastVertxWriteStream<Buffer>(){
                        @Override public WriteStream<Buffer> write(Buffer t) {
                            log.debug("Somewhat irrelevant got written to the resource.");
                            return this;
                        }
                        @Override public boolean writeQueueFull() { return false; }
                    };
                    resource.closeHandler = v -> {
                        log.debug("Resource closeHandler got called.");
                    };
                    resource.addErrorHandler( err -> {
                        synchronized (errorHandlerGotCalledPtr){
                            log.debug("Resource errorHandler got called.");
                            errorHandlerGotCalledPtr[0] = true;
                        }
                    });
                    handler.handle( resource );
                }
            };
            final ModuleConfiguration config = new ModuleConfiguration();
            victim = new RestStorageHandler(mockedVertx, log, mockedStorage, config);
        }

        // Mock request
        final RoutingContext routingContext;
        {
            final String requestPath = "/dadadel/gugusel";
            final MultiMap headers = new CaseInsensitiveHeaders();
            headers.set( "Content-Length" , "1000" );
            final HttpServerResponse response = new FailFastVertxHttpServerResponse(){
                @Override public HttpServerResponse setStatusCode(int statusCode) {
                    log.debug( "Response status code got set to {}", statusCode);
                    return this;
                }
                @Override public HttpServerResponse setStatusMessage(String statusMessage) {
                    log.debug( "Response status message got set to '{}'.", statusMessage);
                    return this;
                }
            };
            final HttpServerRequest request = new FailFastVertxHttpServerRequest(){
                @Override public HttpServerRequest pause() {
                    log.debug( "Request paused" );
                    return this;
                }
                @Override public String path() { return requestPath; }
                @Override public MultiMap headers() { return headers; }
                @Override public String query() { return ""; }
                @Override public HttpServerRequest resume() { log.debug("Request resumed."); return this; }
                @Override public HttpServerRequest handler(Handler<Buffer> handler) {
                    final Buffer tooShortBuffer = new BufferImpl();
                    tooShortBuffer.setBytes( 0 , ("This messages intent is to be shorter than specified in header.").getBytes());
                    handler.handle( tooShortBuffer );
                    return this;
                }
                @Override public HttpServerRequest endHandler(Handler<Void> endHandler) {
                    // Ignore this because this request MUST NEVER END!
                    return this;
                }
                @Override public HttpServerRequest exceptionHandler(Handler<Throwable> handler) {
                    handler.handle(new Exception("TODO-what-to-do-here"));
                    return this;
                }
            };
            routingContext = new FailFastVertxWebRoutingContext(){
                @Override public HttpServerRequest request() { return request; }
                @Override public HttpServerResponse response() { return response; }
            };
        }

        // Trigger work
        putResourceMethod.invoke(victim, routingContext);

        synchronized (errorHandlerGotCalledPtr){
            testContext.assertTrue(errorHandlerGotCalledPtr[0], "Victim failed to call error handler.");
        }
    }

}
