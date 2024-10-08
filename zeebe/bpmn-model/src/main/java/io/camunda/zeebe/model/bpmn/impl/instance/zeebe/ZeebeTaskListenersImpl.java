/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.zeebe.model.bpmn.impl.instance.zeebe;

import io.camunda.zeebe.model.bpmn.impl.BpmnModelConstants;
import io.camunda.zeebe.model.bpmn.impl.ZeebeConstants;
import io.camunda.zeebe.model.bpmn.impl.instance.BpmnModelElementInstanceImpl;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskListener;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskListeners;
import java.util.Collection;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.child.ChildElementCollection;

public class ZeebeTaskListenersImpl extends BpmnModelElementInstanceImpl
    implements ZeebeTaskListeners {

  protected static ChildElementCollection<ZeebeTaskListener> taskListenersCollection;

  public ZeebeTaskListenersImpl(final ModelTypeInstanceContext instanceContext) {
    super(instanceContext);
  }

  @Override
  public Collection<ZeebeTaskListener> getTaskListeners() {
    return taskListenersCollection.get(this);
  }

  public static void registerType(final ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(ZeebeTaskListeners.class, ZeebeConstants.ELEMENT_TASK_LISTENERS)
            .namespaceUri(BpmnModelConstants.ZEEBE_NS)
            .instanceProvider(ZeebeTaskListenersImpl::new);

    taskListenersCollection =
        typeBuilder.sequence().elementCollection(ZeebeTaskListener.class).build();

    typeBuilder.build();
  }
}
