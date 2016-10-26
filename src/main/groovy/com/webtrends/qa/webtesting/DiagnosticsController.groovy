package com.webtrends.qa.webtesting

import groovy.json.JsonOutput
import groovy.util.logging.Log4j

import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.core.Response

/**
 * Handles ping and healthcheck endpoints
 */
@Log4j
@Path('/')
class DiagnosticsController {
    @GET
    @Path('ping')
    def ping() {
        Response.ok('pong ' + System.currentTimeMillis()).build()
    }

    @GET
    @Path('healthcheck')
    def healthcheck() {
        def health = [
                applicationName: 'webtesting',
                version: (new File('VERSION.txt') << '').text,
                time: new Date(),
                state: 'NORMAL',
                details: 'Thunderbirds are GO',
        ]
        Response.ok(JsonOutput.toJson(health), 'application/json').build()
    }

    @GET
    @Path('healthcheck/lb')
    def healthcheckLb() {
        Response.ok('UP').build()
    }

    @GET
    @Path('healthcheck/nagios')
    def healthcheckNagios() {
        Response.ok('NORMAL|Thunderbirds are GO').build()
    }
}
