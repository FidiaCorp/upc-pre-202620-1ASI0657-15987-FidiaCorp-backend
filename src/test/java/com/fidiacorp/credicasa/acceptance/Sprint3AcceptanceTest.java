package com.fidiacorp.credicasa.acceptance;

import org.junit.platform.suite.api.*;
import static io.cucumber.junit.platform.engine.Constants.*;

@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features/sprint3.feature")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.fidiacorp.credicasa.acceptance")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty,json:target/cucumber/cucumber-sprint3.json,html:target/cucumber/sprint3-index.html")
@ConfigurationParameter(key = "cucumber.execution.parallel.enabled", value = "false")
public class Sprint3AcceptanceTest {}
