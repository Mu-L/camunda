/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.report;

import static java.util.stream.Collectors.toMap;

import io.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.SingleReportConfigurationDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.rest.pagination.PaginationDto;
import io.camunda.optimize.service.db.filter.FilterContext;
import io.camunda.optimize.service.db.report.plan.ExecutionPlan;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import lombok.Data;

@Data
public class ExecutionContext<D extends SingleReportDataDto, P extends ExecutionPlan> {
  private P plan;
  private D reportData;
  private ZoneId timezone;
  private long unfilteredTotalInstanceCount;
  private Optional<PaginationDto> pagination;
  private boolean isCsvExport;
  private boolean isJsonExport;

  // used in the context of combined reports to establish identical bucket sizes/ranges across all
  // single reports
  private MinMaxStatDto combinedRangeMinMaxStats;

  // used to ensure a complete list of distributedByResults (to include all keys, even if the result
  // is empty)
  // e.g. used for groupBy usertask - distributedBy assignee reports, where it is possible that
  // a user has been assigned to one userTask but not another, yet we want the userId to appear
  // in all groupByResults (with 0 if they have not been assigned to said task)
  private Map<String, String> allDistributedByKeysAndLabels = new HashMap<>();

  // used to ensure a complete list of variable names can be used to enrich report results that use
  // pagination
  // For example, we include variables for all instances in a raw data report, even if none of the
  // instances in view
  // have a value for that variable
  private Set<String> allVariablesNames = new HashSet<>();

  // For heatmap reports, we exclude the collapsed subprocess data from being visualised. The keys
  // for these nodes are calculated up front and removed during result retrieval
  private Set<String> hiddenFlowNodeIds = new HashSet<>();

  private FilterContext filterContext;

  private boolean multiIndexAlias = false;

  public <R extends ReportDefinitionDto<D>> ExecutionContext(
      final ReportEvaluationContext<? extends R> reportEvaluationContext) {
    this(reportEvaluationContext, null);
  }

  public <R extends ReportDefinitionDto<D>> ExecutionContext(
      final ReportEvaluationContext<? extends R> reportEvaluationContext, final P plan) {
    this.plan = plan;
    reportData = reportEvaluationContext.getReportDefinition().getData();
    timezone = reportEvaluationContext.getTimezone();
    combinedRangeMinMaxStats = reportEvaluationContext.getCombinedRangeMinMaxStats();
    pagination = reportEvaluationContext.getPagination();
    isCsvExport = reportEvaluationContext.isCsvExport();
    filterContext = createFilterContext(reportEvaluationContext);
    isJsonExport = reportEvaluationContext.isJsonExport();
    hiddenFlowNodeIds = reportEvaluationContext.getHiddenFlowNodeIds();
  }

  public SingleReportConfigurationDto getReportConfiguration() {
    return reportData.getConfiguration();
  }

  public Optional<MinMaxStatDto> getCombinedRangeMinMaxStats() {
    return Optional.ofNullable(combinedRangeMinMaxStats);
  }

  public void setAllDistributedByKeys(final Set<String> allDistributedByKeys) {
    allDistributedByKeysAndLabels =
        allDistributedByKeys.stream().collect(toMap(Function.identity(), Function.identity()));
  }

  private <R extends ReportDefinitionDto<D>> FilterContext createFilterContext(
      final ReportEvaluationContext<R> reportEvaluationContext) {
    final FilterContext.FilterContextBuilder builder =
        FilterContext.builder().timezone(reportEvaluationContext.getTimezone());
    final D definitionReportData = reportEvaluationContext.getReportDefinition().getData();
    // not nice to care about type specifics here but it is still the best place to fit this general
    // mapping
    if (definitionReportData instanceof ProcessReportDataDto) {
      builder.userTaskReport(((ProcessReportDataDto) definitionReportData).isUserTaskReport());
    }
    return builder.build();
  }
}
