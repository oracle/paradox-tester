package com.webtrends.qa.webtesting

import groovy.util.logging.Log4j2

import javax.ws.rs.core.Context
import javax.ws.rs.core.Request
import javax.ws.rs.core.UriInfo
import java.nio.file.Paths

/**
 * A controller for others to inherit from.  Handles creating and loading a config object
 */
@Log4j2
class BaseController {
    @Context
    Request request

    @Context
    UriInfo uriInfo

    static TypedConfigObject config

    static {
        def slurper = new ConfigSlurper()
        def configObject = slurper.parse(Paths.get('config.groovy').toUri().toURL())
        if (new File('config.local.groovy').exists()) {
            configObject.merge(slurper.parse(new File('config.local.groovy').toURI().toURL()))
        }

        config = configObject as TypedConfigObject
    }
}
