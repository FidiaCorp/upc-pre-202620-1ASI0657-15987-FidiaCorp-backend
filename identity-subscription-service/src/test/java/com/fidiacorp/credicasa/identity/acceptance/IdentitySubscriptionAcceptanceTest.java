package com.fidiacorp.credicasa.identity.acceptance;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

/** BDD US01–US06 del microservicio identity-subscription-service. */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features/identity-subscription.feature")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.fidiacorp.credicasa.identity.acceptance")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty,json:target/cucumber/identity-subscription.json,html:target/cucumber/identity-subscription.html")
public class IdentitySubscriptionAcceptanceTest {
}
