package com.webtrends.qa.webtesting

import groovy.util.logging.Log4j
import groovy.json.*
import javax.ws.rs.*
import javax.ws.rs.core.*
import java.nio.file.Files
import java.nio.file.Paths
import java.security.SecureRandom
import static org.apache.commons.lang.SystemUtils.IS_OS_WINDOWS

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

        def suitePath = Paths.get(config.testRunner.testSuites, suite)
        if (Files.notExists(suitePath)) {
            log.error "suite $suite does not exist"
            return Response.status(Response.Status.NOT_FOUND).build()
        }

        if (Files.notExists(suitePath.resolve(Paths.get('bin', "runTests${IS_OS_WINDOWS ? '.bat' : ''}")))) {
            log.error 'suite does not contain a script to run its tests'
            return Response.status(Response.Status.NOT_FOUND).build()
        }

        Map<String, String> body
        try {
            body = new JsonSlurper().parse(bodyStream) as Map<String, String>
        } catch (e) {
            log.error 'Was not able to read request body', e
            return Response.status(Response.Status.BAD_REQUEST).build()
        }

        if (!body.testsToRun) {
            log.error 'POST body did not contain a property called testsToRun! Will not run any tests.'
            return Response.status(Response.Status.BAD_REQUEST).build()
        }

        QueueItem item = makeItem(suite, body)
        createResultsDirectory(item)
        fetchHealthCheck(item)
        runTests(item)

        // Add item to cache
        cache[suite][item.id] = item
        Response.created(uriInfo.absolutePathBuilder.path(item.id).build())
                .type('application/json')
                .entity(JsonOutput.toJson(item.toMap()))
                .build()
    }

    private QueueItem makeItem(String suite, Map<String, String> body) {
        def id = createId()

        // A queue item to track the progress of an execution.
        new QueueItem(
            id: id,
            assembly: suite,
            testsToRun: body.testsToRun,
            startTime: new Date(),
            links: [uriInfo.absolutePathBuilder.path(id).build().toString()],
            environment: body.environment,
            credentials: body.credentials ?: '',
            healthCheck: uriInfo.baseUriBuilder.path("results/$suite/$id/systemHealthCheck.json").build().toString()
        )
    }

    static createResultsDirectory(QueueItem item) {
        Files.createDirectories(Paths.get(config.testResults, item.assembly, item.id))
    }

    static private fetchHealthCheck(QueueItem item) {
        if (item.environment) {
            try {
                def healthCheck = parseHealthCheck(new URL(config.healthCheckEndpoint), item.environment)
                writeHealthCheck(healthCheck, item.assembly, item.id)
            }
            catch (e) {
                log.error 'Error occurred while contacting health check endpoint', e
            }
        } else {
            log.warn 'POST body did not contain a property called environment!'
        }
    }

    static parseHealthCheck(URL healthCheck, String environment) {
        new JsonSlurper().parse(healthCheck)[environment]?.collectEntries {
            [ it.shortname, it.boxroles.findAll { it.service != 'chef-client' } .collectEntries {
                [ service: it.service.trim() , status: it.status.trim() , version: it.version.trim() ]
            } ]
        } ?.findAll { it.value }
    }

    static void writeHealthCheck(healthCheck, String suite, String id) {
        Paths.get(config.testResults, suite, id, 'systemHealthCheck.json').write JsonOutput.toJson(healthCheck)
    }

    static void spliceHealthCheck(QueueItem item) {
        def slurper = new JsonSlurper()

        def overallHealth = slurper.parse(item.healthCheck.toURL(), requestProperties: [Accept: 'application/json'])
            .any { it.value.status == 'down' } ? 'FAIL' : 'PASS'

        def additionalTest = [
            name: item.assembly + '.systemHealthCheck',
            state: overallHealth,
            comment: item.healthCheck
        ]

        def results = Paths.get(config.testResults, item.assembly, item.id, 'testResults.json').toFile()
        def contents = slurper.parse(results)
        contents.tests.add(0, additionalTest)
        results.write(JsonOutput.toJson(contents))
    }

    private runTests(QueueItem item) {
        def resultsDir = Paths.get(config.testResults)
        def relativeLogFile = Paths.get(item.assembly, item.id, 'logOutput.txt')
        def logFile = Files.createFile(resultsDir.resolve(relativeLogFile))
        item.output = uriInfo.baseUriBuilder.path("results/$relativeLogFile").build().toString()

        // Run the tests
        def executable = Paths.get('bin', "runTests${IS_OS_WINDOWS ? '.bat' : ''}")
        def cmd = "$executable ${resultsDir.resolve(logFile.parent)} $item.environment $item.testsToRun"
        def workingDirectory = Paths.get(config.testRunner.testSuites, item.assembly)
        log.info "$item.id Running '$cmd'"
        item.process = cmd.execute(null as List, workingDirectory.toFile())
        item.process.out << item.credentials
        item.credentials = 'REDACTED'
        log.info "$item.id Waiting for up to $config.testRunner.timeout ms before killing"

        item.process.consumeProcessOutput(
            closureWriter {
                log.info it
                logFile << "$it\n"
            },
            closureWriter {
                log.error it
                logFile << "$it\n"
            }
        )

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

    static closureWriter(Closure closure) {
        [write: { byte[] bytes, offset, len -> new String(bytes).eachLine(closure) } ] as OutputStream
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
        new SecureRandom().with { (0..9).collect { (('A'..'Z') + ('a'..'z') + ('0'..'9'))[nextInt(62)] } }.join('')
    }
}
