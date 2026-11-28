package com.fidiacorp.credicasa.quotation.acceptance;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

/** BDD US10, US11, US12, US21, US22 del microservicio quotation-deal-service. */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features/quotation-deal.feature")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.fidiacorp.credicasa.quotation.acceptance")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty,json:target/cucumber/quotation-deal.json,html:target/cucumber/quotation-deal.html")
public class QuotationDealAcceptanceTest {
}
