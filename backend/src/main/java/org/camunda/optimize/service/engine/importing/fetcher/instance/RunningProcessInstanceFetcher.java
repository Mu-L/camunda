package org.camunda.optimize.service.engine.importing.fetcher.instance;

import org.camunda.optimize.dto.engine.HistoricProcessInstanceDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.index.page.TimestampBasedImportPage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.INCLUDE_ONLY_UNFINISHED_INSTANCES;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.MAX_RESULTS_TO_RETURN;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.SORT_BY;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.SORT_ORDER;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.SORT_ORDER_TYPE_ASCENDING;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.SORT_TYPE_START_TIME;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.STARTED_AFTER;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.STARTED_BEFORE;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.TRUE;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class RunningProcessInstanceFetcher extends
  RetryBackoffEngineEntityFetcher<HistoricProcessInstanceDto> {

  @Autowired
  private DateTimeFormatter dateTimeFormatter;

  public RunningProcessInstanceFetcher(EngineContext engineContext) {
    super(engineContext);
  }

  public List<HistoricProcessInstanceDto> fetchHistoricFinishedProcessInstances(TimestampBasedImportPage page) {
    return fetchHistoricFinishedProcessInstances(
      page.getTimestampOfLastEntity(),
      configurationService.getEngineImportActivityInstanceMaxPageSize()
    );
  }

  private List<HistoricProcessInstanceDto> fetchHistoricFinishedProcessInstances(OffsetDateTime timeStamp,
                                                                                 long pageSize) {
    logger.debug("Fetching running historic process instances ...");
    long requestStart = System.currentTimeMillis();
    List<HistoricProcessInstanceDto> entries =
      fetchWithRetry(() -> performFinishedHistoricProcessInstanceRequest(timeStamp, pageSize));
    long requestEnd = System.currentTimeMillis();
    logger.debug(
      "Fetched [{}] running historic process instances which started after set timestamp with page size [{}] within [{}] ms",
      entries.size(),
      pageSize,
      requestEnd - requestStart
    );
    return entries;
  }

  private List<HistoricProcessInstanceDto> performFinishedHistoricProcessInstanceRequest(OffsetDateTime timeStamp, long pageSize) {
    return getEngineClient()
      .target(configurationService.getEngineRestApiEndpointOfCustomEngine(getEngineAlias()))
      .path(configurationService.getHistoricProcessInstanceEndpoint())
      .queryParam(SORT_BY, SORT_TYPE_START_TIME)
      .queryParam(SORT_ORDER, SORT_ORDER_TYPE_ASCENDING)
      .queryParam(STARTED_AFTER, dateTimeFormatter.format(timeStamp))
      .queryParam(MAX_RESULTS_TO_RETURN, pageSize)
      .queryParam(INCLUDE_ONLY_UNFINISHED_INSTANCES, TRUE)
      .request(MediaType.APPLICATION_JSON)
      .acceptEncoding(UTF8)
      .get(new GenericType<List<HistoricProcessInstanceDto>>() {
      });
  }

  public List<HistoricProcessInstanceDto> fetchHistoricFinishedProcessInstances(OffsetDateTime endTimeOfLastInstance) {
    logger.debug("Fetching running historic process instances ...");
    long requestStart = System.currentTimeMillis();
    List<HistoricProcessInstanceDto> secondEntries =
      fetchWithRetry(() -> performFinishedHistoricProcessInstanceRequest(endTimeOfLastInstance));
    long requestEnd = System.currentTimeMillis();
    logger.debug(
      "Fetched [{}] running historic process instances for set start time within [{}] ms",
      secondEntries.size(),
      requestEnd - requestStart
    );
    return secondEntries;
  }

  private List<HistoricProcessInstanceDto> performFinishedHistoricProcessInstanceRequest(OffsetDateTime endTimeOfLastInstance) {
    return getEngineClient()
      .target(configurationService.getEngineRestApiEndpointOfCustomEngine(getEngineAlias()))
      .path(configurationService.getHistoricProcessInstanceEndpoint())
      .queryParam(STARTED_AFTER, dateTimeFormatter.format(endTimeOfLastInstance))
      .queryParam(STARTED_BEFORE, dateTimeFormatter.format(endTimeOfLastInstance))
      .queryParam(INCLUDE_ONLY_UNFINISHED_INSTANCES, TRUE)
      .request(MediaType.APPLICATION_JSON)
      .acceptEncoding(UTF8)
      .get(new GenericType<List<HistoricProcessInstanceDto>>() {
      });
  }
}
