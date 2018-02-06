package org.camunda.optimize.service.alert;

import com.icegreen.greenmail.util.GreenMail;
import org.camunda.optimize.dto.optimize.query.alert.AlertCreationDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertInterval;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.mail.internet.MimeMessage;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * @author Askar Akhmerov
 */
public class AlertStateChangeIT extends AbstractAlertSchedulerIT {

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule)
      .around(engineRule)
      .around(embeddedOptimizeRule)
      .around(engineDatabaseRule);

  private GreenMail greenMail;

  @Before
  public void cleanUp() throws Exception {
    embeddedOptimizeRule.getAlertService().getScheduler().clear();
    greenMail = initGreenMail();
  }

  @After
  public void tearDown() {
    greenMail.stop();
  }

  @Test
  public void changeNotificationIsNotSentByDefault() throws Exception {
    //given
    setEmailConfiguration();
    String token = embeddedOptimizeRule.getAuthenticationToken();
    long daysToShift = 0L;
    long durationInSec = 2L;

    ProcessInstanceEngineDto processInstance = deployWithTimeShift(daysToShift, durationInSec);

    String processDefinitionId = processInstance.getDefinitionId();
    // when
    String reportId = createAndStoreDurationNumberReport(processDefinitionId);
    AlertCreationDto simpleAlert = createSimpleAlert(reportId);
    AlertInterval reminderInterval = new AlertInterval();
    reminderInterval.setValue(1);
    reminderInterval.setUnit("Seconds");
    simpleAlert.setReminder(reminderInterval);

    simpleAlert.setThreshold(1000);

    String id = createAlert(token, simpleAlert);

    triggerAndCompleteJob(id);

    //when
    engineRule.startProcessInstance(processDefinitionId);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();
    greenMail.purgeEmailFromAllMailboxes();

    //then
    triggerAndCompleteJob(id);

    MimeMessage[] emails = greenMail.getReceivedMessages();
    assertThat(emails.length, is(0));
  }

  @Test
  public void changeNotificationIsSent() throws Exception {
    //given
    setEmailConfiguration();

    String token = embeddedOptimizeRule.getAuthenticationToken();
    long daysToShift = 0L;
    long durationInSec = 2L;

    ProcessInstanceEngineDto processInstance = deployWithTimeShift(daysToShift, durationInSec);

    String processDefinitionId = processInstance.getDefinitionId();
    // when
    String reportId = createAndStoreDurationNumberReport(processDefinitionId);
    AlertCreationDto simpleAlert = createSimpleAlert(reportId);
    AlertInterval reminderInterval = new AlertInterval();
    reminderInterval.setValue(3);
    reminderInterval.setUnit("Seconds");
    simpleAlert.setReminder(reminderInterval);
    simpleAlert.setFixNotification(true);
    simpleAlert.setThreshold(1000);
    simpleAlert.getCheckInterval().setValue(5);

    String id = createAlert(token, simpleAlert);

    triggerAndCompleteJob(id);

    //when
    engineRule.startProcessInstance(processDefinitionId);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    greenMail.purgeEmailFromAllMailboxes();
    triggerAndCompleteJob(id);
    //then

    assertThat("email received", greenMail.waitForIncomingEmail(3000, 1), is(true));
    MimeMessage[] emails = greenMail.getReceivedMessages();
    assertThat(emails.length, is(1));
    assertThat(emails[0].getSubject(), is("[Camunda-Optimize] - Report status"));
    assertThat(emails[0].getContent().toString(), containsString("is not exceeded anymore."));
  }

}
