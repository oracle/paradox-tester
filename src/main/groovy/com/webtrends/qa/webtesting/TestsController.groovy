package com.webtrends.qa.webtesting

import groovy.json.JsonOutput
import groovy.util.logging.Log4j

import javax.ws.rs.*
import javax.ws.rs.core.*
import java.nio.file.Files
import java.nio.file.Paths
import static org.apache.commons.lang.SystemUtils.IS_OS_WINDOWS

/**
 * Handles the /tests endpoints for viewing available tests to run.  Calls out to getTests to get the list of tests.
 */
@Log4j
@Path('/tests')
class TestsController extends BaseController {
    @GET
    def index() {
        def suites = Files.newDirectoryStream(Paths.get(config.testRunner.testSuites))
        if (!suites) {
            log.error 'Config.groovy does not contain a valid location for testSuites'
            return Response.serverError().build()
        }

        def list = suites.collect { uriInfo.absolutePathBuilder.path(it.fileName.toString()).build().toString() }
        Response.ok(JsonOutput.toJson(list), 'application/json').build()
    }

    @GET
    @Path('{suite}')
    static list(@PathParam('suite') String suite) {
        def dir = Paths.get(config.testRunner.testSuites, suite)
        if (Files.notExists(dir)) {
            log.error "No test suite named '$suite' was found"
            return Response.status(Response.Status.NOT_FOUND).build()
        }

        if (Files.notExists(dir.resolve(Paths.get('bin', "getTests${IS_OS_WINDOWS ? '.bat' : ''}")))) {
            log.error 'suite does not contain a script to run its tests'
            return Response.status(Response.Status.NOT_FOUND).build()
        }

        def exe = Paths.get(config.testRunner.testSuites, suite, 'bin', "getTests${IS_OS_WINDOWS ? '.bat' : ''}")
        if (Files.notExists(exe)) {
            log.error 'suite does not contain a script to get its tests'
            return Response.status(Response.Status.NOT_FOUND).build()
        }

        Response.ok(getTests(suite), 'application/json').build()
    }

    // returns the text of the "tests.json" file produced from the run
    static private getTests(String suite) {
        def exe = Paths.get(config.testRunner.testSuites, suite, 'bin', "getTests${IS_OS_WINDOWS ? '.bat' : ''}")
        log.info "Running '$exe'"
        Process process = exe.toString().execute(null as List, exe.parent.toFile())
        log.info "Waiting for up to $config.testRunner.timeout ms before killing"
        Thread.start { process.in.eachLine { log.info it } }
        Thread.start { process.err.eachLine { log.error it } }
        process.waitForOrKill(config.testRunner.timeout as long)
        def testsJson = new File([config.testRunner.testSuites, suite, 'tests.json'].join(File.separator))
        if (!testsJson.exists()) {
            log.error "Running $exe did not produce a tests.json file"
            throw new IllegalStateException("Running $exe did not produce a tests.json file")
        }

        testsJson.text
    }
}
