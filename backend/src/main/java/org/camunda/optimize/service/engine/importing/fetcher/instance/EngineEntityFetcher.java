package org.camunda.optimize.service.engine.importing.fetcher.instance;

import org.camunda.optimize.dto.engine.EngineDto;
import org.camunda.optimize.service.engine.importing.fetcher.AbstractEngineAwareFetcher;
import org.camunda.optimize.service.engine.importing.index.page.ImportPage;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.client.Client;
import java.util.List;

@Component
public abstract class EngineEntityFetcher<ENG extends EngineDto, PAGE extends ImportPage>
    extends AbstractEngineAwareFetcher {

  public static final String UTF8 = "UTF-8";

  public EngineEntityFetcher(String engineAlias) {
    super(engineAlias);
  }

  /**
   * Queries the engine to fetch the entities from there given a page,
   * which contains all the information of which chunk of data should be fetched.
   */
  public abstract List<ENG> fetchEngineEntities(PAGE page);

}
