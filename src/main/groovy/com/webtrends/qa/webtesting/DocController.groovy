package com.webtrends.qa.webtesting

import groovy.json.JsonOutput
import groovy.util.logging.Log4j

import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.PathParam
import javax.ws.rs.core.Response

/**
 * Handles the /doc endpoints for getting test names, descriptions, ids, test steps and assertions
 */
@Log4j
@Path('/doc')
class DocController extends BaseController {
    @GET
    def index() {
        def suites = new File(config.testRunner.testSuites).listFiles()
        if (!suites) {
            log.error 'Config.groovy does not contain a valid location for testSuites'
            return Response.serverError().build()
        }

        def list = suites.collect { uriInfo.absolutePathBuilder.path(it.name).build().toString() }
        Response.ok(JsonOutput.toJson(list), 'application/json').build()
    }

    @GET
    @Path('{suite}')
    static show(@PathParam('suite') String suite) {
        def testSuites = config.testRunner.testSuites
        def docs = new File("$testSuites/$suite/docs.json")
        if (!docs.exists()) {
            return Response.status(Response.Status.NOT_FOUND).build()
        }

        Response.ok(docs.text, 'application/json').build()
    }
}
