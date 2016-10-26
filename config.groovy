port = 8080
testResults = '/mnt/data/webtrends/testResults'
healthCheckEndpoint = '<HEALTHCHECK ENDPOINT HERE>'

testRunner {
    testSuites = '/opt/webtrends/testSuites' // NOTE: has problems with spaces in path
    timeout = 1000*60*60*6 // 6 hours
}
