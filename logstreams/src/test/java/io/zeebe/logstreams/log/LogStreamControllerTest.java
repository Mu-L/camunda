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
package io.zeebe.logstreams.log;

import static org.assertj.core.api.Assertions.assertThat;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.flagFailed;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.flagsOffset;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.lengthOffset;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.messageOffset;
import static io.zeebe.logstreams.impl.LogEntryDescriptor.positionOffset;
import static io.zeebe.logstreams.log.LogTestUtil.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.status.AtomicLongPosition;
import io.zeebe.dispatcher.BlockPeek;
import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.dispatcher.Subscription;
import io.zeebe.logstreams.fs.FsLogStreamBuilder;
import io.zeebe.logstreams.impl.LogStreamController;
import io.zeebe.logstreams.impl.log.index.LogBlockIndex;
import io.zeebe.logstreams.spi.LogStorage;
import io.zeebe.logstreams.spi.SnapshotPolicy;
import io.zeebe.logstreams.spi.SnapshotStorage;
import io.zeebe.logstreams.spi.SnapshotWriter;
import io.zeebe.util.actor.ActorReference;
import io.zeebe.util.actor.Actor;
import io.zeebe.util.actor.ActorScheduler;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;

public class LogStreamControllerTest
{
    private static final int MAX_APPEND_BLOCK_SIZE = 1024 * 1024 * 6;
    private static final int INDEX_BLOCK_SIZE = 1024 * 1024 * 2;

    private LogStreamController controller;

    @Mock
    private ActorScheduler mockTaskScheduler;
    @Mock
    private ActorReference mockControllerRef;
    @Mock
    private ActorReference mockSendBufferRef;

    @Mock
    private Dispatcher mockWriteBuffer;
    @Mock
    private Subscription mockWriteBufferSubscription;
    @Mock
    private Actor mockWriteBufferConductor;

    @Mock
    private LogBlockIndex mockBlockIndex;

    @Mock
    private LogStorage mockLogStorage;

    @Mock
    private LogStreamFailureListener mockFailureListener;

    @Mock
    private SnapshotStorage mockSnapshotStorage;
    @Mock
    private SnapshotWriter mockSnapshotWriter;
    @Mock
    private SnapshotPolicy mockSnapshotPolicy;

    private ByteBuffer writeBuffer;

    @Before
    public void init() throws Exception
    {
        MockitoAnnotations.initMocks(this);

        final FsLogStreamBuilder builder = new FsLogStreamBuilder(TOPIC_NAME_BUFFER, PARTITION_ID);

        builder
            .actorScheduler(mockTaskScheduler)
            .writeBuffer(mockWriteBuffer)
            .logStorage(mockLogStorage)
            .snapshotStorage(mockSnapshotStorage)
            .snapshotPolicy(mockSnapshotPolicy)
            .logBlockIndex(mockBlockIndex)
            .maxAppendBlockSize(MAX_APPEND_BLOCK_SIZE)
            .indexBlockSize(INDEX_BLOCK_SIZE);

        when(mockBlockIndex.lookupBlockAddress(anyLong())).thenReturn(LOG_ADDRESS);
        when(mockWriteBuffer.getSubscriptionByName("log-appender")).thenReturn(mockWriteBufferSubscription);
        when(mockWriteBuffer.getConductor()).thenReturn(mockWriteBufferConductor);
        when(mockSnapshotStorage.createSnapshot(anyString(), anyLong())).thenReturn(mockSnapshotWriter);

        when(mockTaskScheduler.schedule(any(LogStreamController.class))).thenReturn(mockControllerRef);
        when(mockTaskScheduler.schedule(mockWriteBufferConductor)).thenReturn(mockSendBufferRef);

        controller = new LogStreamController(builder);

        controller.registerFailureListener(mockFailureListener);

        controller.doWork();
    }

    @Test
    public void shouldGetRoleName()
    {
        assertThat(controller.name()).isEqualTo(LOG_NAME);
    }

    @Test
    public void shouldOpen()
    {
        assertThat(controller.isOpen()).isFalse();

        final CompletableFuture<Void> future = controller.openAsync();

        controller.doWork();

        assertThat(future).isCompleted();
        assertThat(controller.isOpen()).isTrue();

        verify(mockTaskScheduler).schedule(controller);
        verify(mockTaskScheduler).schedule(mockWriteBufferConductor);
    }

    @Test
    public void shouldNotOpenIfNotClosed()
    {
        controller.openAsync();
        controller.doWork();

        assertThat(controller.isOpen()).isTrue();

        // try to open again
        final CompletableFuture<Void> future = controller.openAsync();

        controller.doWork();

        assertThat(future).isCompletedExceptionally();
        assertThat(controller.isOpen()).isTrue();

        verify(mockTaskScheduler, times(1)).schedule(controller);
        verify(mockTaskScheduler, times(1)).schedule(mockWriteBufferConductor);
    }

    @Test
    public void shouldClose()
    {
        controller.openAsync();
        controller.doWork();

        assertThat(controller.isClosed()).isFalse();

        final CompletableFuture<Void> future = controller.closeAsync();

        // -> closing
        controller.doWork();
        // -> closed
        controller.doWork();

        assertThat(future).isCompleted();
        assertThat(controller.isClosed()).isTrue();

        verify(mockControllerRef).close();
        verify(mockSendBufferRef).close();

    }

    @Test
    public void shouldNotCloseIfNotOpen()
    {
        assertThat(controller.isClosed()).isTrue();

        // try to close again
        final CompletableFuture<Void> future = controller.closeAsync();

        controller.doWork();

        assertThat(future).isCompletedExceptionally();
        assertThat(controller.isClosed()).isTrue();
    }

    @Test
    public void shouldNotCreateSnapshotIfSnapshotPolicyNotApplies() throws Exception
    {
        when(mockWriteBufferSubscription.peekBlock(any(BlockPeek.class), anyInt(), anyBoolean())).thenAnswer(peekBlock(LOG_POSITION, 64));
        when(mockWriteBufferSubscription.getPosition()).thenReturn(LOG_POSITION);
        when(mockLogStorage.append(any(ByteBuffer.class))).thenReturn(LOG_ADDRESS);

        when(mockSnapshotPolicy.apply(anyLong())).thenReturn(false);

        controller.openAsync();
        // -> opening
        controller.doWork();
        // -> open
        controller.doWork();
        // -> snapshotting
        controller.doWork();

        assertThat(controller.isOpen()).isTrue();

        verify(mockSnapshotStorage, never()).createSnapshot(LOG_NAME, LOG_POSITION);
    }

    @Test
    public void shouldWriteBlockFromBufferToLogStorage() throws Exception
    {
        when(mockWriteBufferSubscription.peekBlock(any(BlockPeek.class), anyInt(), anyBoolean())).thenAnswer(peekBlock(LOG_POSITION, 64));
        when(mockLogStorage.append(any(ByteBuffer.class))).thenReturn(LOG_ADDRESS);

        controller.openAsync();
        // -> opening
        controller.doWork();
        // -> open
        final int result = controller.doWork();

        assertThat(result).isEqualTo(1);

        verify(mockLogStorage).append(writeBuffer);
    }

    @Test
    public void shouldSpinIfWriteBufferHasNoMoreBytes() throws Exception
    {
        when(mockWriteBufferSubscription.peekBlock(any(BlockPeek.class), anyInt(), anyBoolean())).thenReturn(0);

        controller.openAsync();
        // -> opening
        controller.doWork();
        // -> open
        final int result = controller.doWork();

        assertThat(controller.isOpen()).isTrue();
        assertThat(result).isEqualTo(0);

        verify(mockLogStorage, never()).append(any(ByteBuffer.class));
        verify(mockBlockIndex, never()).addBlock(anyLong(), anyLong());
        verify(mockSnapshotPolicy, never()).apply(anyLong());
    }

    @Test
    public void shouldInvokeFailureListenerIfFailToWriteTheBlock() throws Exception
    {
        when(mockWriteBufferSubscription.peekBlock(any(BlockPeek.class), anyInt(), anyBoolean())).thenAnswer(peekBlock(LOG_POSITION, 64));
        when(mockLogStorage.append(any(ByteBuffer.class))).thenReturn(-1L);

        controller.openAsync();
        // -> opening
        controller.doWork();
        // -> open
        controller.doWork();
        // -> failing
        controller.doWork();

        assertThat(controller.isFailed()).isTrue();

        verify(mockFailureListener).onFailed(LOG_POSITION);

        verify(mockBlockIndex, never()).addBlock(anyLong(), anyLong());
        verify(mockSnapshotPolicy, never()).apply(anyLong());

        final byte flags = writeBuffer.get(flagsOffset(0));
        assertThat(flagFailed(flags)).isTrue();
    }

    @Test
    public void shouldMarkBlocksAsFailedWhileInFailedState() throws Exception
    {
        when(mockWriteBufferSubscription.peekBlock(any(BlockPeek.class), anyInt(), anyBoolean()))
            .thenAnswer(peekBlock(LOG_POSITION, 64))
            .thenAnswer(peekBlock(LOG_POSITION + 1, 86));

        when(mockLogStorage.append(any(ByteBuffer.class))).thenReturn(-1L);

        controller.openAsync();
        // -> opening
        controller.doWork();
        // -> open
        controller.doWork();
        // -> failing
        controller.doWork();
        // -> failed
        controller.doWork();

        assertThat(controller.isFailed()).isTrue();

        verify(mockWriteBufferSubscription, times(2)).peekBlock(any(BlockPeek.class), anyInt(), anyBoolean());

        final byte flags = writeBuffer.get(flagsOffset(0));
        assertThat(flagFailed(flags)).isTrue();
    }

    @Test
    public void shouldRemoveFailureListener() throws Exception
    {
        controller.removeFailureListener(mockFailureListener);

        when(mockWriteBufferSubscription.peekBlock(any(BlockPeek.class), anyInt(), anyBoolean())).thenAnswer(peekBlock(LOG_POSITION, 64));
        when(mockLogStorage.append(any(ByteBuffer.class))).thenReturn(-1L);

        controller.openAsync();
        // -> opening
        controller.doWork();
        // -> open
        controller.doWork();
        // -> failing
        controller.doWork();

        assertThat(controller.isFailed()).isTrue();

        verify(mockFailureListener, never()).onFailed(anyLong());
    }

    @Test
    public void shouldInvokeFailureListenerOnRecovered() throws Exception
    {
        when(mockWriteBufferSubscription.peekBlock(any(BlockPeek.class), anyInt(), anyBoolean())).thenAnswer(peekBlock(LOG_POSITION, 64));
        when(mockLogStorage.append(any(ByteBuffer.class))).thenReturn(-1L);

        controller.openAsync();
        // -> opening
        controller.doWork();
        // -> open
        controller.doWork();
        // -> failing
        controller.doWork();

        controller.recover();
        // -> recovered
        controller.doWork();

        assertThat(controller.isOpen()).isTrue();

        verify(mockFailureListener).onRecovered();
    }

    @Test
    public void shouldGetCurrentAppenderPosition()
    {
        when(mockWriteBufferSubscription.getPosition()).thenReturn(1L, 2L);

        controller.openAsync();
        // -> opening
        controller.doWork();

        assertThat(controller.getCurrentAppenderPosition()).isEqualTo(1L);
        assertThat(controller.getCurrentAppenderPosition()).isEqualTo(2L);
    }

    @Test
    public void shouldNotGetCurrentAppenderPositionIfNotOpen()
    {
        when(mockWriteBufferSubscription.getPosition()).thenReturn(1L);

        assertThat(controller.getCurrentAppenderPosition()).isEqualTo(-1);
    }

    protected Answer<Integer> peekBlock(long logPosition, int bytesRead)
    {
        return invocation ->
        {
            final BlockPeek blockPeek = (BlockPeek) invocation.getArguments()[0];

            final UnsafeBuffer buffer = new UnsafeBuffer(new byte[bytesRead]);
            writeBuffer = ByteBuffer.wrap(buffer.byteArray());

            final int positionOffset = positionOffset(messageOffset(0));
            buffer.putLong(positionOffset, logPosition);

            // TODO: Hier ist was faul: Länge ist kein Long
            buffer.putLong(lengthOffset(0), bytesRead);

            blockPeek.setBlock(writeBuffer, new AtomicLongPosition(), 0, 0, bytesRead, 0, bytesRead);

            return bytesRead;
        };
    }

}
