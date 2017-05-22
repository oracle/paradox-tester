package com.webtrends.qa.webtesting

import groovy.json.JsonOutput
import groovy.util.logging.Log4j

import javax.ws.rs.*
import javax.ws.rs.core.*

/**
 * Handles the /tests endpoints for viewing available tests to run.  Calls out to getTests to get the list of tests.
 */
@Log4j
@Path('/tests')
class TestsController extends BaseController {
    @GET
    def index() {
        def suites = new File(config.testRunner.testSuites as String).listFiles()
        if (!suites) {
            log.error 'Config.groovy does not contain a valid location for testSuites'
            return Response.serverError().build()
        }

        def list = suites.collect { uriInfo.absolutePathBuilder.path(it.name).build().toString() }
        Response.ok(JsonOutput.toJson(list), 'application/json').build()
    }

    @GET
    @Path('{suite}')
    static list(@PathParam('suite') String suite) {
        def dir = new File([config.testRunner.testSuites, suite].join(File.separator))
        if (!dir.exists()) {
            log.error "No test suite named '$suite' was found"
            return Response.status(Response.Status.NOT_FOUND).build()
        }

        if (!getExecutable(suite, 'getTests')) {
            log.error 'suite does not contain a script to get its tests'
            return Response.status(Response.Status.NOT_FOUND).build()
        }

        Response.ok(getTests(suite), 'application/json').build()
    }

    // returns the text of the "tests.json" file produced from the run
    private getTests(String suite) {
        def executable = getExecutable(suite, 'getTests')

        log.info "Running '$executable.path'"
        Process process = executable.path.execute(null as List, new File(executable.parent))
        log.info "Waiting for up to $config.testRunner.timeout ms before killing"
        Thread.start { process.in.eachLine { log.info it } }
        Thread.start { process.err.eachLine { log.error it } }
        process.waitForOrKill(config.testRunner.timeout as long)
        def testsJson = new File([config.testRunner.testSuites, suite, 'tests.json'].join(File.separator))
        if (!testsJson.exists()) {
            log.error "Running $executable.path did not produce a tests.json file"
            throw new IllegalStateException("Running $executable.path did not produce a tests.json file")
        }

        testsJson.text
    }
}
