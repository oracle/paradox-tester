package com.webtrends.qa.webtesting

import groovy.json.JsonSlurper
import groovy.util.logging.Log4j2
import org.eclipse.jetty.server.Server
import org.testng.annotations.*
import java.nio.file.Files
import java.nio.file.Paths
import groovyx.net.http.RESTClient
import static org.apache.commons.lang.SystemUtils.IS_OS_WINDOWS

/**
 * Tests for the /queue endpoints
 */
@Log4j2
class QueueControllerTests {
    Server server
    def port = QueueController.config.port as int
    def url = "http://localhost:$port/webtesting/"
    def rest = new RESTClient(url)

    @BeforeClass
    def setupTestSuite() {
        def fileName = IS_OS_WINDOWS ? 'runTests.bat' : 'runTests'
        def quote = IS_OS_WINDOWS ? '\"' : '\\\"'
        def timeout = IS_OS_WINDOWS ? 'waitfor it /t 5' : 'sleep 5'
        def testResultsJson = "echo { ${quote}tests$quote: [ ] } > " +
                "${IS_OS_WINDOWS ? '%1\\testResults.json' : '$1/testResults.json'}"
        def runTests = new File("tmpSuites/all-the-things/bin/$fileName")
        runTests.parentFile.mkdirs()

        runTests.write """
echo windows arguments
echo %*
echo linux arguments
echo \"\$@\"
echo gonna run all the things
$timeout
echo done running all the things
$testResultsJson
"""
        runTests.executable = true
        QueueController.config.testRunner.testSuites = new File('tmpSuites').canonicalPath
        QueueController.config.testResults = new File('tmpResults').canonicalPath
        QueueController.config.healthCheckEndpoint = new File('src/test/resources/healthcheck.json').toURI().toString()
    }

    @BeforeClass
    def startServer() {
        server = MainClass.startServer(port)
    }

    @Test
    void getRunningTestsWhenEmpty() {
        def resp = rest.get path: 'queue/suite-with-no-executions'
        assert resp.data.size() == 0
    }

    @Test
    void startATest() {
        def resp = rest.post(
                path: 'queue/all-the-things',
                body: [testsToRun: 'All the things'],
                requestContentType: 'application/json')
        def item = resp.data
        assert resp.status == 201//  .statusLine.statusCode == 201
        assert item.Id ==~ '[a-zA-Z0-9]{10}'
        assert item.Assembly == 'all-the-things'
        assert item.Links.size() == 1
        assert item.Links[0] == "${url}queue/all-the-things/$item.Id"
        assert item.Status == 'Running'
        assert item.TestsToRun == 'All the things'
        assert item.Output == "${url}results/all-the-things/$item.Id/logOutput.txt"
        assert resp.headers.Location == "${url}queue/all-the-things/$item.Id"
    }

    @Test
    void getRunningTest() {
        def resp = rest.post(
                path: 'queue/all-the-things',
                body: [testsToRun: 'All the things'],
                requestContentType: 'application/json')

        def location = new URL(resp.headers.Location as String)

        resp = rest.get path: location.path
        def item = resp.data
        assert resp.status == 200
        assert item.Id == location.path.split('/')[-1]
        assert item.Assembly == 'all-the-things'
        assert item.Links.size() == 1
        assert item.Links[0] == "${url}queue/all-the-things/$item.Id"
        assert item.Status == 'Running'
        assert item.TestsToRun == 'All the things'
        assert item.Output == "${url}results/all-the-things/$item.Id/logOutput.txt"
    }

    @Test
    void cancelRunningTest() {
        def resp = rest.post(
                path: 'queue/all-the-things',
                body: [testsToRun: 'All the things'],
                requestContentType: 'application/json')

        def location = new URL(resp.headers.Location as String)
        resp = rest.put path: location.path
        def item = resp.data
        assert resp.status == 200
        assert item.Id == location.path.split('/')[-1]
        assert item.Assembly == 'all-the-things'
        assert item.Links.size() == 0
        assert item.Status == 'Cancelled'
        assert item.TestsToRun == 'All the things'
    }

    @Test
    void getCompletedTest() {
        def resp = rest.post(
                path: 'queue/all-the-things',
                body: [testsToRun: 'All the things'],
                requestContentType: 'application/json')

        def location = new URL(resp.headers.Location as String)

        Thread.sleep(6000)

        resp = rest.get path: location.path
        def item = resp.data
        assert resp.status == 200
        assert item.Id == location.path.split('/')[-1]
        assert item.Assembly == 'all-the-things'
        assert item.Links.size() == 1
        assert item.Links[0] == "${url}results/all-the-things/$item.Id"
        assert item.Status == 'RanToCompletion'
        assert item.TestsToRun == 'All the things'
        assert item.Output == "${url}results/all-the-things/$item.Id/logOutput.txt"
    }

    @Test
    void startTestInEnvironment() {
        def resp = rest.post(
                path: 'queue/all-the-things',
                body: [testsToRun: 'All the things', environment: 'reality'],
                requestContentType: 'application/json')
        def item = resp.data
        assert resp.status == 201//  .statusLine.statusCode == 201
        assert item.Id ==~ '[a-zA-Z0-9]{10}'
        assert item.Assembly == 'all-the-things'
        assert item.Links.size() == 1
        assert item.Links[0] == "${url}queue/all-the-things/$item.Id"
        assert item.Status == 'Running'
        assert item.TestsToRun == 'All the things'
        assert item.Environment == 'reality'
        assert resp.headers.Location == "${url}queue/all-the-things/$item.Id"
    }

    @Test
    void testHealthCheck () {
        def resp = rest.post(
                path: 'queue/all-the-things',
                body: [testsToRun: 'All the things', environment: 'dev'],
                requestContentType: 'application/json')

        def location = new URL(resp.headers.Location as String)

        Thread.sleep(6000)

        resp = rest.get path: location.path

        def item = resp.data
        def workingDir = Paths.get(QueueController.config.testResults, item.Assembly as String, item.Id as String)
        assert item.Id ==~ '[a-zA-Z0-9]{10}'
        assert item.Assembly == 'all-the-things'
        assert item.Links.size() == 1
        assert item.Links[0] == "${url}results/all-the-things/$item.Id"
        assert item.TestsToRun == 'All the things'
        assert item.Environment == 'dev'
        assert item.HealthCheck == "${url}results/all-the-things/$item.Id/systemHealthCheck.json"
        assert Files.exists(workingDir.resolve('systemHealthCheck.json'))
        assert Files.exists(workingDir.resolve('testResults.json'))
        def slurped = new JsonSlurper().parse(workingDir.resolve('testResults.json').toFile())
        assert slurped.tests[0].name == item.Assembly + '.systemHealthCheck'
    }

    @Test
    void startTestWithCredentials() {
        def resp = rest.post(
                path: 'queue/all-the-things',
                body: [testsToRun: 'All the things', credentials: 'myusername,mypassword'],
                requestContentType: 'application/json')
        def item = resp.data
        assert resp.status == 201//  .statusLine.statusCode == 201
        assert !item.containsKey('credentials'), 'The credentials should never be shown to the user'
    }

    @AfterClass
    def stopServer() {
        server.stop()
        server.destroy()
    }

    @AfterClass
    def tearDownTestSuite() {
        new File('tmpResults').deleteDir()
        new File('tmpSuites').deleteDir()
    }
}
