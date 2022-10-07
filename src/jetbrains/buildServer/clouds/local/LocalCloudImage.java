/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.clouds.local;

import jetbrains.buildServer.clouds.*;
import jetbrains.buildServer.serverSide.agentPools.AgentPool;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;

public class LocalCloudImage implements CloudImage {
  @NotNull private final String myId;
  @NotNull private final String myName;
  @NotNull private final File myAgentHomeDir;
  @NotNull private final Map<String, LocalCloudInstance> myInstances = new ConcurrentHashMap<String, jetbrains.buildServer.clouds.local.LocalCloudInstance>();
  @NotNull private final IdGenerator myInstanceIdGenerator = new IdGenerator();
  @Nullable private final CloudErrorInfo myErrorInfo;
  private boolean myIsReusable;
  private boolean myIsEternalStarting;
  private Integer myAgentPoolId;
  private final Map<String, String> myExtraProperties = new HashMap<String, String>();
  @NotNull private final ScheduledExecutorService myExecutor;

  public LocalCloudImage(@NotNull final String imageId,
                         @NotNull final String imageName,
                         @NotNull final String agentHomePath,
                         @NotNull final ScheduledExecutorService executor) {
    myId = imageId;
    myName = imageName;
    myAgentHomeDir = new File(agentHomePath);
    myExecutor = executor;
    myErrorInfo = myAgentHomeDir.isDirectory() || (myAgentHomeDir.isFile() && myAgentHomeDir.getName().endsWith(".zip")) ? null
            : new CloudErrorInfo("\"" + agentHomePath + "\" is not a directory or a zip archive or does not exist.");
  }

  public boolean isReusable() {
    return myIsReusable;
  }

  public void setIsReusable(boolean isReusable) {
    myIsReusable = isReusable;
  }

  public boolean isEternalStarting() {
    return myIsEternalStarting;
  }

  public void setIsEternalStarting(boolean isEternalStarting) {
    myIsEternalStarting = isEternalStarting;
  }

  public void setAgentPoolId(int agentPoolId) {
    myAgentPoolId = agentPoolId;
  }

  public void addExtraProperty(@NotNull final String name, @NotNull final String value) {
    myExtraProperties.put(name, value);
  }

  @NotNull
  public Map<String, String> getExtraProperties() {
    return myExtraProperties;
  }

  @NotNull
  public String getId() {
    return myId;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @NotNull
  public File getAgentHomeDir() {
    return myAgentHomeDir;
  }

  @NotNull
  public Collection<? extends CloudInstance> getInstances() {
    return Collections.unmodifiableCollection(myInstances.values());
  }

  @Nullable
  public LocalCloudInstance findInstanceById(@NotNull final String instanceId) {
    return myInstances.get(instanceId);
  }

  @Nullable
  @Override
  public Integer getAgentPoolId() {
    return myAgentPoolId;
  }

  @Nullable
  public CloudErrorInfo getErrorInfo() {
    return myErrorInfo;
  }

  @NotNull
  public synchronized LocalCloudInstance startNewInstance(@NotNull final CloudInstanceUserData data) {
    for (Map.Entry<String, String> e : myExtraProperties.entrySet()) {
      data.addAgentConfigurationParameter(e.getKey(), e.getValue());
    }

    //check reusable instances
    for (LocalCloudInstance instance : myInstances.values()) {
      if (instance.getErrorInfo() == null && instance.getStatus() == InstanceStatus.STOPPED && instance.isRestartable()) {
        instance.start(data);
        return instance;
      }
    }

    final String instanceId = myInstanceIdGenerator.next();
    final LocalCloudInstance instance = createInstance(instanceId);
    myInstances.put(instanceId, instance);
    instance.start(data);
    return instance;
  }

  protected LocalCloudInstance createInstance(String instanceId) {
    if (isReusable()) {
      return new ReStartableInstance(instanceId, this, myExecutor);
    }
    return new OneUseLocalCloudInstance(instanceId, this, myExecutor);
  }

  void forgetInstance(@NotNull final LocalCloudInstance instance) {
    myInstances.remove(instance.getInstanceId());
  }

  void dispose() {
    for (final LocalCloudInstance instance : myInstances.values()) {
      instance.terminate();
    }
    myInstances.clear();
  }
}
