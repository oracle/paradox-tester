package com.webtrends.qa.webtesting

import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.handler.ContextHandler
import org.eclipse.jetty.server.handler.HandlerList
import org.eclipse.jetty.server.handler.ResourceHandler
import org.eclipse.jetty.servlet.ServletContextHandler
import org.glassfish.jersey.servlet.ServletContainer
import javax.ws.rs.core.MediaType

/**
 * The main entry point for starting the webservice.
 */
class MainClass {
    static void main(String[] args) {
        def port = BaseController.config.port as int
        def server = startServer(port)
        try {
            server.join()
        } finally {
            try {
                server.stop()
                server.destroy()
            } catch (ignored) {
            }
        }
    }

    static Server startServer(int port) {
        String testResultsPath = BaseController.config.testResults
        def resultsHandler = new ContextHandler(
            handler: new AcceptHandler(
                    [MediaType.TEXT_HTML_TYPE],
                    new ResourceHandler(
                            directoriesListed: true,
                            baseResource: new TimeSortedResource(new File(testResultsPath)))),
            contextPath: '/webtesting/results/')

        def jsonHandler = new ServletContextHandler(ServletContextHandler.SESSIONS)
        jsonHandler.contextPath = '/'
        jsonHandler.addServlet(ServletContainer, '/webtesting/*').with {
            setInitParameter('jersey.config.server.provider.packages', 'com.webtrends.qa.webtesting')
            setInitParameter('com.sun.jersey.api.json.POJOMappingFeature', 'true')
        }

        def server = new Server(port)
        server.handler = new HandlerList(handlers: [resultsHandler, jsonHandler])
        server.start()
        server
     }
}
