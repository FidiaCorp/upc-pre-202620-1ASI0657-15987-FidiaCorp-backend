package com.fidiacorp.credicasa.catalog.acceptance;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

/** BDD US07–US09, US13–US15 del microservicio property-catalog-service. */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features/property-catalog.feature")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.fidiacorp.credicasa.catalog.acceptance")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty,json:target/cucumber/property-catalog.json,html:target/cucumber/property-catalog.html")
public class PropertyCatalogAcceptanceTest {
}
