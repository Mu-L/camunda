package org.camunda.tngp.broker.taskqueue;

import org.camunda.tngp.broker.log.LogEntryHandler;
import org.camunda.tngp.broker.log.LogEntryProcessor;
import org.camunda.tngp.broker.services.HashIndexManager;
import org.camunda.tngp.broker.taskqueue.log.TaskInstanceReader;
import org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor;
import org.camunda.tngp.hashindex.Bytes2LongHashIndex;
import org.camunda.tngp.hashindex.Long2LongHashIndex;
import org.camunda.tngp.log.LogReader;
import org.camunda.tngp.log.LogReaderImpl;
import org.camunda.tngp.taskqueue.data.TaskInstanceState;

import uk.co.real_logic.agrona.DirectBuffer;

public class TaskQueueIndexWriter implements LogEntryHandler<TaskInstanceReader>
{
    protected final LogReader logReader;
    protected final HashIndexManager<Long2LongHashIndex> lockedTasksIndexManager;
    protected final HashIndexManager<Bytes2LongHashIndex> taskTypeIndexManager;

    protected LogEntryProcessor<TaskInstanceReader> logEntryProcessor;

    public TaskQueueIndexWriter(TaskQueueContext taskQueueContext)
    {
        lockedTasksIndexManager = taskQueueContext.getLockedTaskInstanceIndex();
        taskTypeIndexManager = taskQueueContext.getTaskTypePositionIndex();
        logReader = new LogReaderImpl(taskQueueContext.getLog(), TaskInstanceReader.MAX_LENGTH);

        final long lastCheckpointPosition = Math.min(lockedTasksIndexManager.getLastCheckpointPosition(), taskTypeIndexManager.getLastCheckpointPosition());
        if (lastCheckpointPosition != -1)
        {
            logReader.setPosition(lastCheckpointPosition);
        }

        logEntryProcessor = new LogEntryProcessor<>(logReader, new TaskInstanceReader(), this);
    }

    public int update(int maxFragments)
    {
        return logEntryProcessor.doWork(maxFragments);
    }

    public void writeCheckpoints()
    {
        lockedTasksIndexManager.writeCheckPoint(logReader.position());
        taskTypeIndexManager.writeCheckPoint(logReader.position());
    }

    @Override
    public void handle(long position, TaskInstanceReader reader)
    {
        final long id = reader.id();
        final TaskInstanceState state = reader.state();

        if (state == TaskInstanceState.LOCKED)
        {
            lockedTasksIndexManager.getIndex().put(id, position);

            final DirectBuffer taskType = reader.getTaskType();
            final int taskTypeLength = taskType.capacity();

            final Bytes2LongHashIndex taskTypePositionIndex = taskTypeIndexManager.getIndex();
            final long currentPosition = taskTypePositionIndex.get(taskType, 0, taskTypeLength, -1);

            // TODO: this is next line is completely broken and only works if the previous version has the exact same length as this entry
            // SEE: https://github.com/camunda-tngp/broker/issues/4
            final long newPosition = reader.prevVersionPosition() + DataFrameDescriptor.alignedLength(reader.length());

            // TODO: put if larger
            if (newPosition > currentPosition)
            {
                taskTypePositionIndex.put(taskType, 0, taskTypeLength, newPosition);
            }
        }
        else if (state == TaskInstanceState.COMPLETED)
        {
            lockedTasksIndexManager.getIndex().remove(id, -1);
        }
    }
}
