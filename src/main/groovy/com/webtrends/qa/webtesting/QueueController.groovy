package com.webtrends.qa.webtesting

import groovy.util.logging.Log4j
import groovy.json.*
import javax.ws.rs.*
import javax.ws.rs.core.*
import java.security.SecureRandom

/**
 * Handles the /queue endpoint for kicking off new tests, cancelling and reviewing the status of running tests
 */
@Log4j
@Path('/queue')
class QueueController extends BaseController {
    @Context
    UriInfo uriInfo

    static Map<String, Map<String, QueueItem>> cache = [:].withDefault { [:] }

    @GET
    def index() {
        def list = cache.keySet().collect { uriInfo.absolutePathBuilder.path(it).build().toString() }
        Response.ok(JsonOutput.toJson(list), 'application/json').build()
    }

    @GET
    @Path('{suite}')
    def list(@PathParam('suite') String suite) {
        def maps = (cache[suite] ?: [:]).collect {
            it.value.toMap() + [Id: uriInfo.absolutePathBuilder.path(it.key).build().toString()]
        }

        Response.ok(JsonOutput.toJson(maps), 'application/json').build()
    }

    @GET
    @Path('{suite}/{id}')
    static show(@PathParam('suite') String suite, @PathParam('id') String id) {
        def item = cache[suite]?.get(id)
        if (item == null) {
            return Response.status(Response.Status.NOT_FOUND).build()
        }

        Response.ok(JsonOutput.toJson(item.toMap()), 'application/json').build()
    }

    @POST
    @Path('{suite}')
    @Consumes('application/json')
    def save(@PathParam('suite') String suite, InputStream bodyStream) {
        // Check arguments
        if (!new File([config.testRunner.testSuites, suite].join(File.separator)).exists()) {
            log.error "suite $suite does not exist"
            return Response.status(Response.Status.NOT_FOUND).build()
        }

        if (!getExecutable(suite, 'runTests')) {
            log.error 'suite does not contain a script to run its tests'
            return Response.status(Response.Status.NOT_FOUND).build()
        }

        def body
        try {
            body = new JsonSlurper().parse(new BufferedReader(new InputStreamReader(bodyStream)))
        } catch (e) {
            log.error 'Was not able to read request body'
            return Response.status(Response.Status.BAD_REQUEST).build()
        }

        if (!body.testsToRun) {
            log.error 'POST body did not contain a property called testsToRun! Will not run any tests.'
            return Response.status(Response.Status.BAD_REQUEST).build()
        }

        QueueItem item = makeItem(suite, body)
        runTests(item)

        // Add item to cache
        cache[suite][item.id] = item
        Response.created(uriInfo.absolutePathBuilder.path(item.id).build())
                .type('application/json')
                .entity(JsonOutput.toJson(item.toMap()))
                .build()
    }

    private QueueItem makeItem(String suite, body) {
        def id = createId()
        def resultsLocation = new File([config.testResults, suite, id].join(File.separator))
        def healthCheck
        resultsLocation.mkdirs()

        if (body.environment) {
            try {
                def healthCheckEndpoint = new URL(config.healthCheckEndpoint)
                def fullHealthCheck = new JsonSlurper().parse(healthCheckEndpoint)
                def environmentHealthCheck = fullHealthCheck[body.environment]
                healthCheck = getHealthCheck(environmentHealthCheck, suite, id)
            }
            catch (e) {
                log.error 'Error occurred while contacting health check endpoint'
            }
        } else {
            log.warn 'POST body did not contain a property called environment!'
        }

        // A queue item to track the progress of an execution.
        new QueueItem(
            id: id,
            assembly: suite,
            testsToRun: body.testsToRun,
            startTime: new Date(),
            links: [uriInfo.absolutePathBuilder.path(id).build().toString()],
            environment: body.environment,
            credentials: body.credentials,
            healthCheck: healthCheck,
        )
    }

    def getHealthCheck(Object environmentValues, String suite, String id) {
        def state = environmentValues.any { it.boxroles.any { it.status == 'down' } } ? 'FAIL' : 'PASS'
        def systemHealthCheckFile = new File([config.testResults, suite, id, 'systemHealthCheck.json']
                .join(File.separator))

        def reducedEnvironmentValues = environmentValues.collectEntries {
            [ it.shortname, it.boxroles.findAll { it.service != 'chef-client' } .collectEntries {
                [ service: it.service.trim() , status: it.status.trim() , version: it.version.trim() ]
            } ]
        } .findAll { it.value }

        def url = uriInfo.baseUriBuilder.path("results/$suite/$id/systemHealthCheck.json").build().toString()
        def json = [id: '', name: suite + '.systemHealthCheck', label: '', state: state, comment: url, defect: null]

        systemHealthCheckFile.write(JsonOutput.toJson(reducedEnvironmentValues))
        json
    }

    static spliceHealthCheck(QueueItem item) {
        def testResultsFile = new File([config.testResults, item.assembly, item.id,
                                        'testResults.json'].join(File.separator))
        def slurped = new JsonSlurper().parse(testResultsFile)

        slurped.tests.add(0, item.healthCheck)

        def jsonOutput = JsonOutput.toJson(slurped)
        testResultsFile.write(jsonOutput)
    }

    private runTests(QueueItem item) {
        // Create location for results
        def resultsLocation = new File([config.testResults, item.assembly, item.id].join(File.separator))
        def logFile = new File(resultsLocation, 'logOutput.txt')
        if (!logFile.createNewFile()) {
            throw new IOException("Unable to create $logFile")
        }

        item.output = uriInfo.baseUriBuilder.path("results/$item.assembly/$item.id/$logFile.name").build().toString()

        // Run the tests
        def executable = getExecutable(item.assembly, 'runTests')
        def cmd = "$executable.path $resultsLocation.path $item.environment $item.testsToRun"
        def workingDirectory = new File([config.testRunner.testSuites, item.assembly].join(File.separator))
        log.info "$item.id Running '$cmd'"
        item.process = cmd.execute(null as List, workingDirectory)
        def printer = new PrintWriter(new BufferedOutputStream(item.process.out))
        printer.println item.credentials
        printer.flush()
        item.clearCredentials()

        log.info "$item.id Waiting for up to $config.testRunner.timeout ms before killing"
        Thread.start {
            item.process.in.eachLine {
                log.info("$item.id $it")
                logFile << "$it\n"
            }
        }
        Thread.start {
            item.process.err.eachLine {
                log.error("$item.id $it")
                logFile << "$it\n"
            }
        }

        // Wait for the tests to finish in background.  When done, update status and links
        Thread.start {
            item.process.waitForOrKill(config.testRunner.timeout as long)
            if (item.status.compareAndSet('Running', 'RanToCompletion')) {
                log.info "$item.id has ran to completion"
                item.links = [uriInfo.baseUriBuilder.path("results/$item.assembly/$item.id").build().toString()]
            }

            if (item.healthCheck) {
                spliceHealthCheck(item)
            }

        }
    }

    @PUT
    @Path('{suite}/{id}')
    static cancel(@PathParam('suite') String suite, @PathParam('id') String id) {
        log.info "Cancelling item for assembly $suite and id $id"
        def item = cache[suite]?.get(id)
        if (!item) {
            log.error "No running suite named '$suite' with id '$id' was found"
            return Response.status(Response.Status.NOT_FOUND).build()
        }

        if (!item.status.compareAndSet('Running', 'Cancelled')) {
            log.warn "Item $id not cancelled because status was not 'Running'"
            return Response.status(Response.Status.CONFLICT).build()
        }

        item.process?.destroy()
        item.links.clear()
        Response.ok(JsonOutput.toJson(item.toMap()), 'application/json').build()
    }

    private static createId() {
        new SecureRandom().with { (0..9).collect { (('A'..'Z') + ('a'..'z') + (0..9))[nextInt(62)] } }.join()
    }
}
