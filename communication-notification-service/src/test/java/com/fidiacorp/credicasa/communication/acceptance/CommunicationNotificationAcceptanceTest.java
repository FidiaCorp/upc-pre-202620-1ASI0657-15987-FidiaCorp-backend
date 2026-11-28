package com.fidiacorp.credicasa.communication.acceptance;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

/** BDD US10, US16, US17, US18 del microservicio communication-notification-service. */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features/communication-notification.feature")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.fidiacorp.credicasa.communication.acceptance")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty,json:target/cucumber/communication-notification.json,html:target/cucumber/communication-notification.html")
public class CommunicationNotificationAcceptanceTest {
}
