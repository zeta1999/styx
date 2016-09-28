/*
 * -\-\-
 * Spotify Styx Scheduler Service
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
package com.spotify.styx.state.handlers;

import com.spotify.styx.model.Event;
import com.spotify.styx.model.ExecutionDescription;
import com.spotify.styx.model.Workflow;
import com.spotify.styx.model.WorkflowId;
import com.spotify.styx.model.WorkflowInstance;
import com.spotify.styx.model.WorkflowState;
import com.spotify.styx.state.OutputHandler;
import com.spotify.styx.state.RunState;
import com.spotify.styx.state.StateManager;
import com.spotify.styx.storage.Storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

public class ExecutionDescriptionHandler implements OutputHandler {

  private static final Logger LOG = LoggerFactory.getLogger(ExecutionDescriptionHandler.class);

  private final Storage storage;
  private final StateManager stateManager;

  public ExecutionDescriptionHandler(
      Storage storage,
      StateManager stateManager) {
    this.storage = requireNonNull(storage);
    this.stateManager = requireNonNull(stateManager);
  }

  @Override
  public void transitionInto(RunState state) {
    final WorkflowInstance workflowInstance = state.workflowInstance();

    switch (state.state()) {
      case PREPARE:
        try {
          final Event createdEvent =
              Event.submit(state.workflowInstance(), getExecDescription(workflowInstance));
          try {
            stateManager.receive(createdEvent);
          } catch (StateManager.IsClosed isClosed) {
            LOG.warn("Could not send 'created' event", isClosed);
          }
        } catch (Exception e) {
          LOG.warn("Failed to prepare execution description for "
                   + state.workflowInstance().toKey(), e);
          stateManager.receiveIgnoreClosed(Event.halt(workflowInstance));
        }
        break;

      default:
        // do nothing
    }
  }

  private ExecutionDescription getExecDescription(WorkflowInstance workflowInstance) throws Exception {
    final WorkflowId workflowId = workflowInstance.workflowId();

    final Optional<Workflow> workflowOpt = storage.workflow(workflowId);
    final Workflow workflow = workflowOpt.orElseThrow(
        () -> new Exception(format("Missing %s, halting %s", workflowId, workflowInstance)));

    final Optional<List<String>> dockerArgsOpt = workflow.schedule().dockerArgs();
    if (!dockerArgsOpt.isPresent()) {
      throw new IOException(format("%s has no docker args, halting %s",
                                   workflowId, workflowInstance));
    }

    final Optional<WorkflowState> workflowStateOpt = storage.workflowState(workflow.id());

    final WorkflowState workflowState = workflowStateOpt.orElseThrow(
        () -> new Exception(format("Missing state for %s, halting %s",
                                   workflowId, workflowInstance)));

    final Optional<String> dockerImageOpt = workflowState.dockerImage().isPresent()
        ? workflowState.dockerImage()
        : workflow.schedule().dockerImage(); // backwards compatibility

    if (!dockerImageOpt.isPresent()) {
      throw new IOException(format("%s has no docker image, halting %s",
                                   workflowId, workflowInstance));
    }

    if (!dockerImageOpt.isPresent()) {
      throw new IOException(format("%s has no docker image, halting %s",
                                   workflowId, workflowInstance));
    }

    return ExecutionDescription.create(
        dockerImageOpt.get(),
        dockerArgsOpt.get(),
        workflow.schedule().secret(),
        workflowState.commitSha());
  }
}