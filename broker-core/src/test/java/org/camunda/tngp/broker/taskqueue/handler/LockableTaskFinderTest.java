package org.camunda.tngp.broker.taskqueue.handler;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.camunda.tngp.broker.taskqueue.TaskInstanceWriter;
import org.camunda.tngp.broker.taskqueue.log.TaskInstanceReader;
import org.camunda.tngp.broker.util.mocks.StubLogReader;
import org.camunda.tngp.log.Log;
import org.camunda.tngp.taskqueue.data.TaskInstanceState;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

public class LockableTaskFinderTest
{

    protected StubLogReader logReader;

    @Mock
    protected Log log;

    protected static final byte[] PAYLOAD = "booom".getBytes(StandardCharsets.UTF_8);

    protected static final byte[] TASK_TYPE = "wrooom".getBytes(StandardCharsets.UTF_8);
    protected static final int TASK_TYPE_HASH = TaskTypeHash.hashCode(TASK_TYPE, TASK_TYPE.length);
    protected static final byte[] TASK_TYPE2 = "camunda".getBytes(StandardCharsets.UTF_8);

    @Before
    public void setUp()
    {
        MockitoAnnotations.initMocks(this);
        logReader = new StubLogReader(log)
            .addEntry(createTaskInstanceWriter(1L, TASK_TYPE, TaskInstanceState.COMPLETED))
            .addEntry(createTaskInstanceWriter(2L, TASK_TYPE2, TaskInstanceState.NEW))
            .addEntry(createTaskInstanceWriter(3L, TASK_TYPE, TaskInstanceState.NEW))
            .addEntry(createTaskInstanceWriter(4L, TASK_TYPE, TaskInstanceState.NEW));
    }

    protected TaskInstanceWriter createTaskInstanceWriter(long taskId, byte[] taskType, TaskInstanceState state)
    {
        final TaskInstanceWriter writer = new TaskInstanceWriter();

        writer.lockOwner(1L);
        writer.lockTime(2L);
        writer.id(taskId);
        writer.state(state);
        writer.prevVersionPosition(4L);
        writer.wfActivityInstanceEventKey(5L);
        writer.wfRuntimeResourceId(6);
        writer.payload(new UnsafeBuffer(PAYLOAD), 0, PAYLOAD.length);
        writer.taskType(new UnsafeBuffer(taskType), 0, taskType.length);

        return writer;
    }

    @Test
    public void shouldFindFirstLockableTask()
    {
        // given
        final LockableTaskFinder taskFinder = new LockableTaskFinder(logReader);
        taskFinder.init(log, 0, TASK_TYPE_HASH, new UnsafeBuffer(TASK_TYPE));

        // when
        taskFinder.findNextLockableTask();

        // then
        assertThat(taskFinder.getLockableTaskPosition()).isEqualTo(logReader.getEntryPosition(2));

        final TaskInstanceReader taskFound = taskFinder.getLockableTask();
        assertThat(taskFound).isNotNull();
        assertThat(taskFound.id()).isEqualTo(3L);
    }


}
