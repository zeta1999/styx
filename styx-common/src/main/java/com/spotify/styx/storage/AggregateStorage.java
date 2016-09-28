/*
 * -\-\-
 * Spotify Styx Common
 * --
 * Copyright (C) 2016 Spotify AB
 * --
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * -/-/-
 */
package com.spotify.styx.storage;

import com.google.cloud.datastore.Datastore;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import com.spotify.styx.model.SequenceEvent;
import com.spotify.styx.model.Workflow;
import com.spotify.styx.model.WorkflowExecutionInfo;
import com.spotify.styx.model.WorkflowId;
import com.spotify.styx.model.WorkflowInstance;
import com.spotify.styx.model.WorkflowInstanceExecutionData;
import com.spotify.styx.model.WorkflowState;

import org.apache.hadoop.hbase.client.Connection;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * A {@link Storage} implementation backed by Datastore and Bigtable
 */
public class AggregateStorage implements Storage, EventStorage {

  private final ThreadFactory threadFactory = new ThreadFactoryBuilder()
      .setNameFormat("storage-darkload-%d")
      .setDaemon(true)
      .build();
  private final ExecutorService darkloadExecutor = Executors.newSingleThreadExecutor(threadFactory);

  private final BigtableStorage bigtableStorage;
  private final DatastoreStorage datastoreStorage;

  public AggregateStorage(Connection connection, Datastore datastore, Duration retryBaseDelay) {
    this.bigtableStorage = new BigtableStorage(connection, retryBaseDelay);
    this.datastoreStorage = new DatastoreStorage(datastore, retryBaseDelay);
  }

  @Override
  public SortedSet<SequenceEvent> readEvents(WorkflowInstance workflowInstance) throws IOException {
    return bigtableStorage.readEvents(workflowInstance);
  }

  @Override
  public Map<WorkflowInstance, Long> readActiveWorkflowInstances() throws IOException {
    return bigtableStorage.readActiveWorkflowInstances();
  }

  @Override
  public void writeEvent(SequenceEvent sequenceEvent) throws IOException {
    bigtableStorage.writeEvent(sequenceEvent);
  }

  @Override
  public void writeActiveState(WorkflowInstance workflowInstance, long counter) throws IOException {
    darkloadExecutor.submit(() -> {
      try {
        datastoreStorage.writeActiveState(workflowInstance, counter);
      } catch (IOException ignore) {
      }
    });
    bigtableStorage.writeActiveState(workflowInstance, counter);
  }

  @Override
  public void deleteActiveState(WorkflowInstance workflowInstance) throws IOException {
    darkloadExecutor.submit(() -> {
      try {
        datastoreStorage.deleteActiveState(workflowInstance);
      } catch (IOException ignore) {
      }
    });
    bigtableStorage.deleteActiveState(workflowInstance);
  }

  @Override
  public boolean globalEnabled() throws IOException {
    return datastoreStorage.globalEnabled();
  }

  @Override
  public boolean setGlobalEnabled(boolean enabled) throws IOException {
    return datastoreStorage.setGlobalEnabled(enabled);
  }

  @Override
  public List<WorkflowInstanceExecutionData> executionData(WorkflowId workflowId)
      throws IOException {
    return bigtableStorage.executionData(workflowId);
  }

  @Override
  public void store(WorkflowExecutionInfo workflowExecutionInfo) throws IOException {
    bigtableStorage.store(workflowExecutionInfo);
  }

  @Override
  public Map<WorkflowInstance, List<WorkflowExecutionInfo>> getExecutionInfo(WorkflowId workflowId)
      throws IOException {
    return bigtableStorage.getExecutionInfo(workflowId);
  }

  @Override
  public List<WorkflowExecutionInfo> getExecutionInfo(WorkflowInstance workflowInstance)
      throws IOException {
    return bigtableStorage.getExecutionInfo(workflowInstance);
  }

  @Override
  public boolean enabled(WorkflowId workflowId) throws IOException {
    return datastoreStorage.enabled(workflowId);
  }

  @Override
  public Set<WorkflowId> enabled() throws IOException {
    return datastoreStorage.enabled();
  }

  @Override
  public Optional<Long> getLatestStoredCounter(WorkflowInstance workflowInstance)
      throws IOException {
    return bigtableStorage.getLatestStoredCounter(workflowInstance);
  }

  @Override
  public WorkflowInstanceExecutionData executionData(WorkflowInstance workflowInstance) throws IOException {
    return bigtableStorage.executionData(workflowInstance);
  }

  @Override
  public void store(Workflow workflow) throws IOException {
    datastoreStorage.store(workflow);
  }

  @Override
  public Optional<Workflow> workflow(WorkflowId workflowId) throws IOException {
    return datastoreStorage.workflow(workflowId);
  }

  @Override
  public void patchState(WorkflowId workflowId, WorkflowState state) throws IOException {
    datastoreStorage.patchState(workflowId, state);
  }

  @Override
  public void patchState(String componentId, WorkflowState state) throws IOException {
    datastoreStorage.patchState(componentId, state);
  }

  @Override
  public Optional<String> getDockerImage(WorkflowId workflowId) throws IOException {
    return datastoreStorage.getDockerImage(workflowId);
  }

  @Override
  public Optional<WorkflowState> workflowState(WorkflowId workflowId) throws IOException {
    return datastoreStorage.workflowState(workflowId);
  }
}