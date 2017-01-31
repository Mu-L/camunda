package org.camunda.optimize.test.rule;

import org.apache.http.HttpHost;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.camunda.optimize.service.util.ConfigurationService;
import org.camunda.optimize.test.util.PropertyUtil;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.Properties;

/**
 * Rule that performs clean up of engine on integration test startup and
 * one more clean up after integration test.
 *
 * Relies on expectation of /purge endpoint available in Tomcat for HTTP GET
 * requests and performing actual purge.
 *
 * @author Askar Akhmerov
 */
@Component
public class EngineIntegrationRule extends TestWatcher {

  private Properties properties;

  private Logger logger = LoggerFactory.getLogger(EngineIntegrationRule.class);

  @Autowired
  ConfigurationService configurationService;

  @PostConstruct
  public void init() {
    properties = PropertyUtil.loadProperties("service-it.properties");
    cleanEngine();
  }

  @Override
  protected void finished(Description description) {
    cleanEngine();
  }

  private void cleanEngine() {
    CloseableHttpClient client = HttpClientBuilder.create().build();
    HttpGet getRequest = new HttpGet(properties.get("camunda.optimize.test.purge").toString());
    try {
      CloseableHttpResponse response = client.execute(getRequest);
      if (response.getStatusLine().getStatusCode() != 200) {
        throw new RuntimeException("Something really bad happened during purge, " +
            "please check tomcat logs of engine-purge servlet");
      }
      client.close();
    } catch (IOException e) {
      logger.error("Error during purge request", e);
    }

  }

  public void deployServiceTaskProcess() {
    CloseableHttpClient client = HttpClientBuilder.create().build();
    HttpGet getRequest = new HttpGet(properties.get("camunda.optimize.test.deploy").toString());
    try {
      CloseableHttpResponse response = client.execute(getRequest);
      if (response.getStatusLine().getStatusCode() != 200) {
        throw new RuntimeException("Something really bad happened during deployment, " +
            "please check tomcat logs of engine-deploy servlet");
      }
      client.close();
    } catch (IOException e) {
      logger.error("Error during deploy request", e);
    }
  }
}
