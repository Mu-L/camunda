package org.camunda.optimize.service.engine.importing;

import org.camunda.optimize.service.engine.importing.index.handler.ImportIndexHandlerProvider;
import org.camunda.optimize.service.engine.importing.job.factory.ActivityInstanceEngineImportJobFactory;
import org.camunda.optimize.service.engine.importing.job.factory.EngineImportJobFactory;
import org.camunda.optimize.service.engine.importing.job.factory.FinishedProcessInstanceEngineImportJobFactory;
import org.camunda.optimize.service.engine.importing.job.factory.ProcessDefinitionEngineImportJobFactory;
import org.camunda.optimize.service.engine.importing.job.factory.ProcessDefinitionXmlEngineImportJobFactory;
import org.camunda.optimize.service.engine.importing.job.factory.StoreIndexesEngineImportJobFactory;
import org.camunda.optimize.service.engine.importing.job.factory.UnfinishedProcessInstanceEngineImportJobFactory;
import org.camunda.optimize.service.engine.importing.job.factory.VariableInstanceEngineImportJobFactory;
import org.camunda.optimize.service.util.BeanHelper;
import org.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class EngineImportJobSchedulerFactory implements ConfigurationReloadable {

  @Autowired
  private EngineImportJobExecutor engineImportJobExecutor;

  @Autowired
  private ConfigurationService configurationService;

  @Autowired
  private BeanHelper beanHelper;

  private List<EngineImportJobScheduler> schedulers;


  public List<EngineImportJobScheduler> buildSchedulers() {
    List<EngineImportJobScheduler> result = new ArrayList<>();
    for (String engineAlias : this.configurationService.getConfiguredEngines().keySet()) {
      List<EngineImportJobFactory> factories = createFactoryList(engineAlias);
      EngineImportJobScheduler scheduler =
          new EngineImportJobScheduler(
              engineImportJobExecutor,
              factories,
              engineAlias
          );
      result.add(scheduler);
    }

    return result;
  }

  private List<EngineImportJobFactory> createFactoryList(String engineAlias) {
    List<EngineImportJobFactory> factories = new ArrayList<>();

    factories.add(
        beanHelper.getInstance(ActivityInstanceEngineImportJobFactory.class, engineAlias));
    factories.add(
        beanHelper.getInstance(FinishedProcessInstanceEngineImportJobFactory.class, engineAlias));
    factories.add(
        beanHelper.getInstance(ProcessDefinitionEngineImportJobFactory.class, engineAlias));
    factories.add(
        beanHelper.getInstance(ProcessDefinitionXmlEngineImportJobFactory.class, engineAlias));
    factories.add(
        beanHelper.getInstance(StoreIndexesEngineImportJobFactory.class, engineAlias));
    factories.add(
        beanHelper.getInstance(UnfinishedProcessInstanceEngineImportJobFactory.class, engineAlias));
    factories.add(
        beanHelper.getInstance(VariableInstanceEngineImportJobFactory.class, engineAlias));

    return factories;
  }

  public List<EngineImportJobScheduler> getImportSchedulers() {
    if (schedulers == null) {
      this.schedulers = this.buildSchedulers();
    }

    return schedulers;
  }

  @Override
  public void reloadConfiguration(ApplicationContext context) {
    if (schedulers != null) {
      for (EngineImportJobScheduler oldScheduler : schedulers) {
        oldScheduler.disable();
      }
      schedulers = this.buildSchedulers();
    }

  }
}
