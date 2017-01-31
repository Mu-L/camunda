package org.camunda.optimize.service.util.schema.type;

import org.camunda.optimize.service.util.ConfigurationService;
import org.camunda.optimize.service.util.schema.TypeMappingCreator;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

@Component
public class EventType implements TypeMappingCreator{

  private Logger logger = LoggerFactory.getLogger(EventType.class);

  @Autowired
  ConfigurationService configurationService;

  @Override
  public String getType() {
    return configurationService.getEventType();
  }

  @Override
  public String getSource() {
    String source = null;
    try {
      XContentBuilder content = jsonBuilder()
        .startObject()
          .startObject("properties")
            .startObject("activityId")
              .field("type", "keyword")
            .endObject()
            .startObject("state")
              .field("type", "keyword")
            .endObject()
            .startObject("activityInstanceId")
              .field("type", "keyword")
            .endObject()
            .startObject("timestamp")
              .field("type", "date")
            .endObject()
            .startObject("processDefinitionKey")
              .field("type", "keyword")
            .endObject()
            .startObject("processDefinitionId")
              .field("type", "keyword")
            .endObject()
            .startObject("processInstanceId")
              .field("type", "keyword")
            .endObject()
          .endObject()
        .endObject();
      source = content.string();
    } catch (IOException e) {
      String message = "Could not add mapping to the index '" + configurationService.getOptimizeIndex() +
        "' , type '" + getType() + "'!";
      logger.error(message, e);
    };
    return source;
  }
}
