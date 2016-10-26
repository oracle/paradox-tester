package com.webtrends.qa.webtesting

import org.eclipse.jetty.server.Handler
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.handler.HandlerWrapper

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import javax.ws.rs.core.MediaType

/**
 * A handler wrapper which will only forward the request onto the handler if the Accept header of the request matches
 * one of the media types of the wrapper
 */
class AcceptHandler extends HandlerWrapper {
    List<MediaType> acceptTypes

    AcceptHandler(List<MediaType> acceptTypes, Handler handler) {
        this.acceptTypes = acceptTypes
        _handler = handler
    }

    @Override
    void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) {
        String accept = request.getHeader('Accept')

        // If no Accept header field is present, then it is assumed that the client accepts all media types
        if (!accept) {
            super.handle(target, baseRequest, request, response)
            baseRequest.handled = true
            return
        }

        // Find candidate media ranges with compatible type/subtype pairs having a quality factor > 0
        // If no quality value is specified, q=1 is assumed and is acceptable
        def mediaTypes = accept.split(',').collect { MediaType.valueOf(it) }
        if (acceptTypes.any { acceptType ->
            mediaTypes.any {
                acceptType.isCompatible(it) && (it.parameters.q ?: 1) as Double
            }
        }) {
            super.handle(target, baseRequest, request, response)
            baseRequest.handled = true
        }
    }
}
