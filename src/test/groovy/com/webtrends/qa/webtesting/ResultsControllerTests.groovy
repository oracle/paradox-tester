package com.webtrends.qa.webtesting

import groovy.json.JsonSlurper
import groovy.util.logging.Log4j
import groovyx.net.http.ContentType
import org.apache.http.entity.ContentType as EntityContentType
import org.eclipse.jetty.server.Server
import org.testng.annotations.*
import groovyx.net.http.RESTClient

/**
 * Tests for the /results endpoints
 */
@Log4j
class ResultsControllerTests {
    Server server

    def port = ResultsController.config.port as int
    def url = "http://localhost:$port/webtesting/"
    def rest = new RESTClient(url)

    @BeforeMethod
    def setupResults() {
        ResultsController.config.testRunner.testResults = new File('tmpResults').canonicalPath
        rest.contentType = ContentType.JSON
        def results = new File('tmpResults/all-the-things/someIdHere/testResults.json')
        results.parentFile.mkdirs()
        results.write '''
{
    "name": "all-the-things",
    "date": "2000-01-01",
    "assembly": "all-the-things",
    "tests":[
        {"name":"aPassingTest", "state":"PASS", "comment":"", "defect":""},
        {"name":"aFailingTest", "state":"FAIL", "comment":"We failed", "defect":""}
    ]
}
'''
        def html = new File('tmpResults/all-the-things/someIdHere/index.html')
        html.write '''
<html>
<body>
Non empty html body
</body>
</html>
'''
    }

    @BeforeClass
    def startServer() {
        ResultsController.config.testResults = new File('tmpResults').canonicalPath
        server = MainClass.startServer(port)
    }

    @Test
    void overwriteResults() {
        def resp = rest.put path: 'results/all-the-things/someIdHere', body: '{"updated": true}'
        assert resp.data.updated == true

        resp = rest.get path: 'results/all-the-things/someIdHere'
        assert resp.data.updated == true
    }

    @Test
    void getListOfSuites() {
        def resp = rest.get path: 'results'
        assert resp.data.size() == 1
        assert resp.data[0] == "${url}results/all-the-things"
    }

    @Test
    void getListOfResults() {
        def resp = rest.get path: 'results/all-the-things'
        assert resp.data.size() == 1
        assert resp.data[0] == "${url}results/all-the-things/someIdHere"
    }

    @Test
    void getSingleResultJson() {
        def resp = rest.get path: 'results/all-the-things/someIdHere'
        def item = resp.data
        def expected = new JsonSlurper().parse(new File('tmpResults/all-the-things/someIdHere/testResults.json'))
        assert item == expected
    }

    @DataProvider(name = 'acceptHeaderMimeTypePairs')
    Object[][] acceptHeaderContentTypePairs() {
        [
            [ContentType.ANY, ContentType.HTML], // The results endpoint should return html by default,
            [ContentType.HTML, ContentType.HTML], // HTML when specifically requested, and
            [ContentType.JSON, ContentType.JSON], // JSON when specifically requested
            [ResultsController.VND_TYPE, ResultsController.VND_TYPE], // specialty type
        ]
    }

    @Test(dataProvider = 'acceptHeaderMimeTypePairs')
    void getResultsWithAcceptHeader(requestContentType, responseContentType) {
        def resp = rest.get (path: 'results/all-the-things/someIdHere', contentType: requestContentType)
        def contentType = EntityContentType.get resp.entity
        assert contentType.mimeType == responseContentType.toString()
    }

    @AfterClass
    def stopServer() {
        server.stop()
        server.destroy()
    }

    @AfterMethod
    def tearDownTestSuite() {
        new File('tmpResults').deleteDir()
    }
}
