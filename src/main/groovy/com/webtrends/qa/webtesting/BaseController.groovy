package com.webtrends.qa.webtesting

import groovy.util.logging.Log4j

import javax.ws.rs.core.Context
import javax.ws.rs.core.Request
import javax.ws.rs.core.UriInfo

/**
 * A controller for others to inherit from.  Handles creating and loading a config object
 */
@Log4j
class BaseController {
    @Context
    Request request

    @Context
    UriInfo uriInfo

    static config

    static {
        def slurper = new ConfigSlurper()

        config = slurper.parse(new File('config.groovy').toURI().toURL())
        if (new File('config.local.groovy').exists()) {
            config.merge(slurper.parse(new File('config.local.groovy').toURI().toURL()))
        }
    }

    static File getExecutable(String suite, String name) {
        def executableFileName = System.getProperty('os.name')?.startsWith('Windows') ? "${name}.bat" : name
        def executable = new File([config.testRunner.testSuites, suite, 'bin', executableFileName].join(File.separator))
        if (!executable.exists()) {
            log.error "No file with path $executable.path exists"
            return null
        }

        executable
    }
}
