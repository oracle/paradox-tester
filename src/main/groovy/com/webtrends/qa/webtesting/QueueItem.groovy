package com.webtrends.qa.webtesting

import groovy.transform.Canonical

import java.util.concurrent.atomic.AtomicReference

/**
 * A data transfer object for information about a running test
 */
@Canonical
class QueueItem {
    String id
    Process process
    String assembly
    AtomicReference<String> status = new AtomicReference<>('Running')
    String testsToRun
    Date startTime
    List<String> links
    String environment
    String credentials
    String output
    String healthCheck

    def toMap() {
        [
                Id: this.id,
                Assembly: this.assembly,
                Status: this.status.get(),
                TestsToRun: this.testsToRun,
                StartTime: this.startTime.toString(),
                Links: this.links,
                Environment: this.environment,
                HealthCheck: this.healthCheck,
                Output: this.output,
        ]
    }
}
