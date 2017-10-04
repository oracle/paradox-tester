package com.webtrends.qa.webtesting

import groovy.transform.ToString

/**
 * Class for strongly typing the config.groovy file.  Useful for autocompletion
 */
@ToString
class TypedConfigObject {
    int port
    String testResults
    String healthCheckEndpoint
    TestRunner testRunner
}

@ToString
class TestRunner {
    String testSuites
    int timeout
}
