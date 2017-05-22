package com.webtrends.qa.webtesting

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.util.logging.Log4j

import javax.ws.rs.*
import javax.ws.rs.core.*

/**
 * Handles the /results endpoint for viewing results in json format.  The html format is handles by the default handler
 */
@Log4j
@Path('/results')
@Produces('application/json')
class ResultsController extends BaseController {
    static final String VND_TYPE = 'application/vnd.webtrends.automationcommon+json'

    @GET
    def index() {
        def suites = new File(config.testResults as String).listFiles()
        if (!suites) {
            log.error 'Config.groovy does not contain a valid location for testResults'
            return Response.serverError().build()
        }

        def list = suites.collect { uriInfo.absolutePathBuilder.path(it.name).build().toString() }
        Response.ok(JsonOutput.toJson(list), 'application/json').build()
    }

    @GET
    @Path('{suite}')
    def list(@PathParam('suite') String suite) {
        def results = new File([config.testResults, suite].join(File.separator)).listFiles()

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
        def text = showInternal(suite, id)
        if (!text) {
            log.error "No testResults.json found for '$suite'"
            return Response.status(Response.Status.NOT_FOUND).build()
        }

        Response.ok(text, 'application/json').build()
    }

    @GET
    @Path('{suite}/{id}')
    @Produces(ResultsController.VND_TYPE)
    def showVnd(@PathParam('suite') String suite, @PathParam('id') String id) {
        def text = showInternal(suite, id)
        if (!text) {
            log.error "No testResults.json found for '$suite'"
            return Response.status(Response.Status.NOT_FOUND).build()
        }

        Response.ok(text, VND_TYPE).build()
    }

    @PUT
    @Path('{suite}/{id}')
    @Consumes('application/json')
    static save(@PathParam('suite') String suite, @PathParam('id') String id, InputStream bodyStream) {
        // Check arguments
        def file = new File([config.testRunner.testResults, suite, id, 'testResults.json'].join(File.separator))
        if (!file.exists()) {
            log.error "suite $suite with id $id does not exist"
            return Response.status(Response.Status.NOT_FOUND).build()
        }

        def body
        try {
            body = new JsonSlurper().parse(new BufferedReader(new InputStreamReader(bodyStream)))
        } catch (e) {
            log.error 'Was not able to read request body'
            return Response.status(Response.Status.BAD_REQUEST).build()
        }

        file.text = JsonOutput.toJson(body)
        showJson(suite, id)
    }

    private static showInternal(suite, id) {
        def file = new File([config.testResults, suite, id, 'testResults.json'].join(File.separator))
        file.exists() ? file.text : null
    }
}
