package com.webtrends.qa.webtesting

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.util.logging.Log4j

import javax.ws.rs.*
import javax.ws.rs.core.*
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Handles the /results endpoint for viewing results in json format.  The html format is handles by the default handler
 */
@Log4j
@Path('/results')
@Produces('application/json')
class ResultsController extends BaseController {
    class AcceptType {
        static final VND_TYPE = 'application/vnd.webtrends.automationcommon+json'
    }

    @GET
    def index() {
        def suites = Files.list(Paths.get(config.testResults))
        if (!suites) {
            log.error 'Config.groovy does not contain a valid location for testResults'
            return Response.serverError().build()
        }

        def list = suites.collect { uriInfo.absolutePathBuilder.path(it.fileName.toString()).build().toString() }
        Response.ok(JsonOutput.toJson(list), 'application/json').build()
    }

    @GET
    @Path('{suite}')
    def list(@PathParam('suite') String suite) {
        def results = Paths.get(config.testResults, suite).toFile().listFiles()

        if (!results) {
            log.error "No results for suite named '$suite' was found"
            return Response.status(Response.Status.NOT_FOUND).build()
        }

        def list = results.collect { uriInfo.absolutePathBuilder.path(it.name).build().toString() }
        Response.ok(JsonOutput.toJson(list), 'application/json').build()
    }

    @GET
    @Path('{suite}/{id}')
    static showJson(@PathParam('suite') String suite, @PathParam('id') String id) {
        def text = fileText(suite, id, 'testResults.json')
        if (!text) {
            log.error "No testResults.json found for '$suite'"
            return Response.status(Response.Status.NOT_FOUND).build()
        }

        Response.ok(text, 'application/json').build()
    }

    @GET
    @Path('{suite}/{id}')
    @Produces(AcceptType.VND_TYPE)
    def showVnd(@PathParam('suite') String suite, @PathParam('id') String id) {
        def text = fileText(suite, id, 'testResults.json')
        if (!text) {
            log.error "No testResults.json found for '$suite'"
            return Response.status(Response.Status.NOT_FOUND).build()
        }

        Response.ok(text, AcceptType.VND_TYPE).build()
    }

    @GET
    @Path('{suite}/{id}/systemHealthCheck.json')
    static showHealthCheck(@PathParam('suite') String suite, @PathParam('id') String id) {
        def text = fileText(suite, id, 'systemHealthCheck.json')
        if (!text) {
            log.error "No systemHealthCheck.json found for '$suite'"
            return Response.status(Response.Status.NOT_FOUND).build()
        }

        Response.ok(text, 'application/json').build()
    }

    @PUT
    @Path('{suite}/{id}')
    @Consumes('application/json')
    static save(@PathParam('suite') String suite, @PathParam('id') String id, InputStream bodyStream) {
        // Check arguments
        def file = Paths.get(config.testResults, suite, id, 'testResults.json')
        if (Files.notExists(file)) {
            log.error "suite $suite with id $id does not exist"
            return Response.status(Response.Status.NOT_FOUND).build()
        }

        def body
        try {
            body = new JsonSlurper().parse(new BufferedReader(new InputStreamReader(bodyStream)))
        } catch (e) {
            log.error 'Was not able to read request body', e
            return Response.status(Response.Status.BAD_REQUEST).build()
        }

        file.text = JsonOutput.toJson(body)
        showJson(suite, id)
    }

    private static fileText(String ... more) {
        def file = Paths.get(config.testResults, more)
        Files.exists(file) ? file.text : null
    }
}
