package org.swisspush.reststorage;

import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.buffer.impl.BufferImpl;
import io.vertx.core.http.*;
import io.vertx.core.impl.ContextImpl;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.swisspush.reststorage.mocks.FastFailHttpServer;
import org.swisspush.reststorage.mocks.FastFailHttpServerRequest;
import org.swisspush.reststorage.mocks.FastFailHttpServerResponse;
import org.swisspush.reststorage.mocks.VertxProxy;
import org.swisspush.reststorage.util.HttpRequestHeader;
import org.swisspush.reststorage.util.ModuleConfiguration;
import org.swisspush.reststorage.util.StatusCode;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests for the {@link RestStorageHandler} class
 *
 * @author https://github.com/mcweba [Marc-Andre Weber]
 */
@RunWith(VertxUnitRunner.class)
public class RestStorageHandlerTest {

    private static final String fileSystemStorageRoot = "target/"+RestStorageHandlerTest.class.getSimpleName()+"/root/";
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
    public void resourceGetsStoredOnFileSystem() {
        final RestStorageHandler victim = createRestStorageHandler();

        // Mock request
        final HttpServerRequest request;
        {
            request = new FastFailHttpServerRequest(){
            };
        }

        // Trigger work
        victim.handle( request );
    }

    @Test
    public void tmpFileGetsDeletedOnUnexpectedConnectionClose(TestContext testContext) throws InterruptedException {

        logger.debug( "Using fileSystemStorage root '"+fileSystemStorageRoot+"'.");

        // Setup victim
        final RestStorageHandler victim = createRestStorageHandler();

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
            logger.trace( "Awaiting response..." );
            Thread.sleep(100);
            synchronized (responseCompletePtr) {
                if (responseCompletePtr[0]) break;
            }
        }

        // Validate file store is empty
        {
            final File[] files = new File( fileSystemStorageRoot ).listFiles();
            testContext.assertNotNull( files );
            if( files.length == 0 ){
                // Store is empty. That's ok.
            }else if( files.length == 1 ){
                // Only '.tmp' is allowed.
                testContext.assertEquals( ".tmp" , files[0].getName() );
                // And it has to be a directory.
                testContext.assertTrue( files[0].isDirectory() );
            }else{
                testContext.fail( "Too much files/dirs in file storage." );
            }
            testContext.fail( "TODO: Write some more asserts." );
        }
    }

    @Test
    public void cleanupResourcesIfConnectionIsClosedTooEarly(TestContext testContext) throws Exception {
        final String hostname = "127.0.0.1";
        final int port = true ? 8989 : 1234;
        final Socket clientSocket = new Socket( hostname , port );
        final OutputStream oStream = clientSocket.getOutputStream();

        logger.debug( "Sending corrupt request." );
        oStream.write( ("" +
                "PUT /foo/bar HTTP/1.1\r\n" +
                "Host: "+ hostname +":"+ port +"\r\n" +
                "Content-Length: 1000\r\n" +
                "\r\n" +
                "Content far shorter than specified in header.\n"
        ).getBytes(StandardCharsets.US_ASCII));
        logger.debug( "Corrupt request sent." );

        logger.debug( "Close connection before body complete." );
        oStream.close();

        // Assert


        testContext.fail( "TODO: Write some asserts." );
    }


    ///////////////////////////////////////////////////////////////////////////////
    // Helpers
    ///////////////////////////////////////////////////////////////////////////////

    private HttpServerRequest createPutRequest( final String path , final HttpServerResponse response ) {
        return new FastFailHttpServerRequest(){
            @Override public String path() { return path; }
        };
    }

    private RestStorageHandler createRestStorageHandler() {
        final Storage fileSystemStorage = new FileSystemStorage( vertx , fileSystemStorageRoot );
        final ModuleConfiguration moduleConfig = new ModuleConfiguration();
        return new RestStorageHandler( vertx , log , fileSystemStorage , moduleConfig );
    }

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
