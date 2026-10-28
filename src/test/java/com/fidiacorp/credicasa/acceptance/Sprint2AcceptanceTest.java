package com.fidiacorp.credicasa.acceptance;
import org.junit.platform.suite.api.*;
import static io.cucumber.junit.platform.engine.Constants.*;
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key=GLUE_PROPERTY_NAME,value="com.fidiacorp.credicasa.acceptance")
@ConfigurationParameter(key=PLUGIN_PROPERTY_NAME,value="pretty,json:target/cucumber/cucumber.json,html:target/cucumber/index.html,junit:target/cucumber/results.xml")
@ConfigurationParameter(key="cucumber.execution.parallel.enabled",value="false")
public class Sprint2AcceptanceTest {}
