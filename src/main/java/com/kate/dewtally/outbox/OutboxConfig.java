package com.kate.dewtally.outbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on @Scheduled for the outbox relay, only in asynchronous mode.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
// Only where uploads are written: the API, or the all-in-one app during development.
@ConditionalOnProperty(name = {"dewtally.processing.async", "dewtally.outbox.relay-enabled"}, havingValue = "true")
class OutboxConfig {
}