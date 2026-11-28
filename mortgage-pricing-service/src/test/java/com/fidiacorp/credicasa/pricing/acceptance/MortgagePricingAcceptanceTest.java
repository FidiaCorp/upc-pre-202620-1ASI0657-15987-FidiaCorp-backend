package com.fidiacorp.credicasa.pricing.acceptance;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

/** BDD US11, US12, US19, US20 del microservicio mortgage-pricing-service. */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features/mortgage-pricing.feature")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.fidiacorp.credicasa.pricing.acceptance")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty,json:target/cucumber/mortgage-pricing.json,html:target/cucumber/mortgage-pricing.html")
public class MortgagePricingAcceptanceTest {
}
