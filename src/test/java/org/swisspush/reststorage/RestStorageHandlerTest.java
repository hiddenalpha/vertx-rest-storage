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
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.swisspush.reststorage.mocks.FastFailHttpServerRequest;
import org.swisspush.reststorage.mocks.FastFailHttpServerResponse;
import org.swisspush.reststorage.util.HttpRequestHeader;
import org.swisspush.reststorage.util.ModuleConfiguration;
import org.swisspush.reststorage.util.StatusCode;

import java.io.File;
import java.io.IOException;
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
    private org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(RestStorageHandlerTest.class);

    private HttpServerRequest request;
    private HttpServerResponse response;


    @Before
    public void setUp() {
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


    ///////////////////////////////////////////////////////////////////////////////
    // Testing behaviour on erroneous requests.
    ///////////////////////////////////////////////////////////////////////////////

    @Test
    public void tmpFileGetsDeletedOnUnexpectedConnectionClose(TestContext testContext) throws InterruptedException {
        final String fileSystemStorageRoot = "target/testFileSystemStorage-a85zupw0q9v8e5zu/";

        logger.debug( "Using fileSystemStorage root '"+fileSystemStorageRoot+"'.");

        // Setup victim
        final RestStorageHandler victim;
        {
            final Storage fileSystemStorage = new FileSystemStorage( vertx , fileSystemStorageRoot );
            ModuleConfiguration moduleConfig = new ModuleConfiguration();
            victim = new RestStorageHandler( vertx , log , fileSystemStorage , moduleConfig );
        }

        // Mock a request.
        final String path = "/houston/server/storage-file/my-half-done-file";
        final boolean[] responseCompletePtr = new boolean[]{ false };
        final HttpServerRequest request;
        {
            final HttpServerResponse response = createMonitoringResponse( responseCompletePtr );
            request = createRequestWhichClosesBeforeBodyTransferred( path , response );
        }

        // Make sure file storage is empty
        {
            final File[] children = new File(fileSystemStorageRoot).listFiles();
            if( children != null ){
                for( File child : children ){
                    vertx.fileSystem().deleteRecursiveBlocking( child.getAbsolutePath() , true );
                }
            }
        }

        // Try to fool victim :)
        logger.info( "Trigger work" );
        victim.handle( request );

        // Wait until response is complete
        for( int i=0 ;; ++i ){
            if( i > 50 ) testContext.fail( "Request didn't complete in time." );
            logger.debug( "Awaiting response..." );
            Thread.sleep(100);
            synchronized (responseCompletePtr) {
                if (responseCompletePtr[0]) break;
            }
        }

        // Validate file store is empty
        {
            final File[] files = new File( fileSystemStorageRoot ).listFiles();
            testContext.assertNotNull( files );
            testContext.assertEquals( 0 , files.length );
        }
    }


    ///////////////////////////////////////////////////////////////////////////////
    // Helpers
    ///////////////////////////////////////////////////////////////////////////////

    private HttpServerRequest createRequestWhichClosesBeforeBodyTransferred(final String path , final HttpServerResponse response ) {
        final MultiMap requestHeaders = new CaseInsensitiveHeaders();
        requestHeaders.set( "Content-Length" , "1000" );
        final Buffer buffer = new BufferImpl(){{
            setBytes(0, ("LessBytesThanSpecifiedInHeader").getBytes());
        }};
        final Exception mockedException = new IOException("Sorry but this connection is closed :)");

        return new FastFailHttpServerRequest(){
            private boolean running = true;
            private boolean consumed = false;
            private Handler<Throwable> errorHandler;
            @Override public HttpMethod method() { return HttpMethod.PUT; }
            @Override public String path() { return path; }
            @Override public String uri() { return path(); }
            @Override public String getHeader( String headerName ) { return null; }
            @Override public MultiMap headers() { return requestHeaders; }
            @Override public HttpServerResponse response() { return response; }
            @Override public HttpServerRequest pause() {
                logger.debug( "Pause request input stream." );
                running = false;
                return null;
            }
            @Override public HttpServerRequest resume() {
                logger.debug( "Resume request input stream.");
                running = true;
                if( !consumed ){
                    consumed = true;
                    if( errorHandler != null ){
                        errorHandler.handle( mockedException );
                    }
                }
                return null;
            }
            @Override public HttpServerRequest endHandler(Handler<Void> handler) {
                logger.debug( "endHandler registered. But will be ignored for this test.");
                return null;
            }
            @Override public HttpServerRequest exceptionHandler(Handler<Throwable> handler) {
                logger.debug( "exceptionHandler registered." );
                if( consumed ){
                    handler.handle( mockedException );
                }else{
                    errorHandler = handler;
                }
                return null;
            }
            @Override public String query() { return null; }
            @Override public HttpServerRequest handler(Handler<Buffer> handler) {
                logger.debug( "dataHandler registered" );
                if( running  && !consumed ){
                    consumed = true;
                    handler.handle( buffer );
                }
                if( errorHandler != null ){
                    errorHandler.handle( mockedException );
                }
                return null;
            }
        };
    }

    /**
     * @param responseCompletePtr
     *      First element of array will be used to track if this response is ended.
     *      This usually should be 'false' initially.
     */
    private HttpServerResponse createMonitoringResponse( final boolean[] responseCompletePtr ) {
        return new FastFailHttpServerResponse(){
            @Override public boolean ended() {
                synchronized( responseCompletePtr ){
                    return responseCompletePtr[0];
                }
            }
            @Override public void end() {
                synchronized( responseCompletePtr ){
                    responseCompletePtr[0] = true;
                }
            }
            @Override public void end(String chunk) { end(); }
            @Override public HttpServerResponse setStatusCode(int statusCode) { return null; }
            @Override public HttpServerResponse setStatusMessage(String statusMessage) { return null; }
        };
    }

}
