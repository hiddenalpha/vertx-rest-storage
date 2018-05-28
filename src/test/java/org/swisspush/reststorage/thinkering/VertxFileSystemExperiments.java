package org.swisspush.reststorage.thinkering;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.buffer.impl.BufferImpl;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.FileSystem;
import io.vertx.core.file.OpenOptions;
import org.swisspush.reststorage.CollectionResource;
import org.swisspush.reststorage.DocumentResource;
import org.swisspush.reststorage.FileSystemStorage;
import org.swisspush.reststorage.Resource;


public class VertxFileSystemExperiments {

    public static void main(String[] args) throws Exception {
        new VertxFileSystemExperiments( Vertx.vertx() ).run();
    }

    private static final String root = "C:/work/tmp/vertxFileSystemExperiments";
    private static final String firstDir = "my";
    private static final String firstDirAbs = joinPath( root , firstDir );
    private static final String secondDir_name = "example";
    private static final String secondDirAbs = joinPath( root , firstDir, secondDir_name);
    private static final String newFile_name = "new-file.txt";
    private static final String newFile_parent = joinPath( firstDir , secondDir_name);
    private static final String newFile_path = joinPath( newFile_parent , newFile_name );
    private static final String newFile_parentAbs = joinPath( root , newFile_parent );
    private static final String newFile_full = joinPath( newFile_parentAbs , newFile_name );
    private static final String oldFile_name = "old-file.txt";
    private static final String oldFile_parent = joinPath( firstDir , secondDir_name);
    private static final String oldFile_parentAbs = joinPath( root , oldFile_parent );
    private static final String oldFile_full = joinPath( oldFile_parentAbs , oldFile_name );
    private final Vertx vertx;

    public VertxFileSystemExperiments(Vertx vertx) {
        this.vertx = vertx;
    }

    private void run() throws Exception {
        final FileSystem fileSystem = vertx.fileSystem();
        final FileSystemStorage storage = new FileSystemStorage( vertx , root+"/" );
        setupSituationInFileSystem();

        new Runnable() {
            @Override public void run() {
//                System.out.println( "Aggressively delete 'example/'." );
//                emulateDirCleanup();
                emulatePutRequest();
            }
            // Emulate Dir Cleanup ////////////////////////////////////////////////////////
            private void emulateDirCleanup() {
                System.out.println( "-asdf-> delete '"+secondDirAbs+"'" );
                fileSystem.delete( secondDirAbs , result -> {
                    System.out.println( "-asdf-> deletion done" );
                    emulateDirCleanup(); // <-- Enter async infinite loop.
                });
            }
            // Emulate PUT request ////////////////////////////////////////////////////////
            private void emulatePutRequest() {
                System.out.println( "emulatePutRequest" );
                // Delete old file
                fileSystem.deleteBlocking( oldFile_full );
                System.out.println( "storage.put(...)" );
                // Schedule evil deletion
                vertx.setTimer( 12 , aLong1 -> {
                    System.out.println( "Evil delete of '"+oldFile_parentAbs+"'" );
                    try{
                        fileSystem.deleteBlocking( oldFile_parentAbs );
                    }catch(Exception e ){}
                });
                storage.put( newFile_path, null, false, 0, resource -> {
                    System.out.println( "storage.put DONE");
                    vertx.setTimer( 50 , aLong1 -> {
                        handlerHandle( vertx, resource );
                    });
                });
            }
        }.run();
    }

    private static void handlerHandle(Vertx vertx, Resource resource) {
        System.out.println( "handlerHandle" );
        if (resource.error) {
            return;
        }

        if (resource.rejected) {
            return;
        }
        if (!resource.modified) {
            return;
        }
        if (!resource.exists && resource instanceof DocumentResource) {
            int i=0;
        }
        if (resource instanceof CollectionResource) {
            int i=0;
        }
        if (resource instanceof DocumentResource) {
            final DocumentResource documentResource = (DocumentResource) resource;
            documentResource.addErrorHandler( error -> {
                int i=0;
            });
            final Buffer buffer = new BufferImpl();
            buffer.setBytes(0, ("Hallo Welt. Das ist mein Inhalt, den ich speichern möchte.").getBytes());
            System.out.println( "write to file" );
            documentResource.writeStream.write( buffer );
            vertx.setTimer( 1 , aLong -> {
                System.out.println( "Close file" );
                documentResource.closeHandler.handle(null);
            });
        }
    }

    private void setupSituationInFileSystem() {
        final FileSystem fileSystem = vertx.fileSystem();
        // Drop old garbage.
        if( fileSystem.existsBlocking(root) ){
            fileSystem.deleteRecursiveBlocking( root , true );
        }
        // Create root
        fileSystem.mkdirsBlocking( root );
        { // Add a file to emulate there's already a file.
            fileSystem.mkdirsBlocking( oldFile_parentAbs );
            final AsyncFile oldFile_handle = fileSystem.openBlocking(oldFile_full, new OpenOptions());
            final Buffer buffer = new BufferImpl();
            buffer.setBytes(0, ("Contents of old file.").getBytes());
            oldFile_handle.end( buffer );
        }
    }

    /**
     * @param parts
     *      Parts to concatenate.
     * @return
     *      Path consisting out of specified parts each separated with exactly one
     *      slash. Doesn't have leading nor trailing slash.
     */
    private static String joinPath( String... parts ){
        final StringBuilder builder = new StringBuilder(128);
        for( String part : parts ){
            int start, end;
            for( start=0 ; part.charAt(start)=='/' ; ++start );
            for( end=part.length()-1 ; part.charAt(end)=='/' ; --end );
            builder.append( '/' ).append(part, start, end+1);
        }
        builder.delete(0, 1);
        return builder.toString();
    }

}
