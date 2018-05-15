package org.swisspush.reststorage;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class TestRestStorageRunner extends RestStorageMod {

    private static final Logger logger = LoggerFactory.getLogger(TestRestStorageRunner.class);

    public static void main(String[] args) {
        final String verticleName = TestRestStorageRunner.class.getName();
        Vertx.vertx().deployVerticle( verticleName, event -> {
            logger.info( "Verticle "+ verticleName +" deployed." );
        });
    }

    @Override
    public void init(Vertx vertx, Context context) {
        super.init(vertx, context);
        context.config().put( "root" , "./fileStorage-ag734h9/" );
    }

}
