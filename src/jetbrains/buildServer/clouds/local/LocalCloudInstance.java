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

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.util.SystemInfo;
import jetbrains.buildServer.clouds.CloudErrorInfo;
import jetbrains.buildServer.clouds.CloudInstance;
import jetbrains.buildServer.clouds.CloudInstanceUserData;
import jetbrains.buildServer.clouds.InstanceStatus;
import jetbrains.buildServer.serverSide.AgentDescription;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.PropertiesUtil;
import jetbrains.buildServer.util.WaitFor;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.*;

public class LocalCloudInstance implements CloudInstance {
  @NotNull private static final Logger LOG = Logger.getLogger(LocalCloudInstance.class);
  private static final int STATUS_WAITING_TIMEOUT = 30 * 1000;

  @NotNull private final String myId;
  @NotNull private final LocalCloudImage myImage;
  @NotNull private final CloudInstanceUserData myData;
  @NotNull private final Date myStartDate;
  @NotNull private final File myBaseDir;

  @NotNull private volatile InstanceStatus myStatus;
  @Nullable private volatile CloudErrorInfo myErrorInfo;

  @NotNull private static final Set<String> ourDirsToNotToCopy = new HashSet<String>() {{
    Collections.addAll(this, "work", "temp", "system", "contrib");
  }}; 

  LocalCloudInstance(@NotNull final String instanceId,
                     @NotNull final LocalCloudImage image,
                     @NotNull final CloudInstanceUserData data) {
    myId = instanceId;
    myImage = image;
    myData = data;
    myStartDate = new Date();
    myStatus = InstanceStatus.SCHEDULED_TO_START;
    myBaseDir = createBaseDir(); // can set status to ERROR, so must be after "myStatus = ..." line
    myBaseDir.deleteOnExit();
  }

  @NotNull
  private File createBaseDir() {
    try {
      return FileUtil.createTempDirectory("tc_buildAgent_", "");
    }
    catch (final IOException e) {
      processError(e);
      return new File("");
    }
  }

  @NotNull
  public String getInstanceId() {
    return myId;
  }

  @NotNull
  public String getName() {
    return LocalCloudClient.generateAgentName(myImage, myId) + " (" + myBaseDir.getAbsolutePath() + ")";
  }

  @NotNull
  public String getImageId() {
    return myImage.getId();
  }

  @NotNull
  public LocalCloudImage getImage() {
    return myImage;
  }

  @NotNull
  public Date getStartedTime() {
    return myStartDate;
  }

  public String getNetworkIdentity() {
    return "cloud.local." + getImageId() + "." + myId;
  }

  @NotNull
  public InstanceStatus getStatus() {
    return myStatus;
  }

  @Nullable
  public CloudErrorInfo getErrorInfo() {
    return myErrorInfo;
  }

  public boolean containsAgent(@NotNull final AgentDescription agentDescription) {
    final Map<String, String> configParams = agentDescription.getConfigurationParameters();
    return myId.equals(configParams.get(LocalCloudConstants.INSTANCE_ID_PARAM_NAME)) &&
           getImageId().equals(configParams.get(LocalCloudConstants.IMAGE_ID_PARAM_NAME));
  }

  void setAgentRegistered(final boolean registered) {
    if (registered) {
      myStatus = InstanceStatus.RUNNING;
    }
    else {
      if (myStatus != InstanceStatus.RESTARTING) {
        myStatus = InstanceStatus.STOPPED;
      }
    }
  }

  void start() {
    myStatus = InstanceStatus.STARTING;

    try {
      final File agentHomeDir = myImage.getAgentHomeDir();
      FileUtil.copyDir(agentHomeDir, myBaseDir, new FileFilter() {
        public boolean accept(@NotNull final File file) {
          return !file.isDirectory() || !file.getParentFile().equals(agentHomeDir) || !ourDirsToNotToCopy.contains(file.getName());
        }
      });

      File inConfigFile = new File(new File(myBaseDir, "conf"), "buildAgent.properties"), outConfigFile = inConfigFile;
      if (!inConfigFile.isFile()) {
        inConfigFile = new File(new File(myBaseDir, "conf"), "buildAgent.dist.properties");
        if (!inConfigFile.isFile()) {
          inConfigFile = null;
        }
      }
      final Properties config = PropertiesUtil.loadProperties(inConfigFile);
      config.put("name", myData.getAgentName());
      config.put("serverUrl", myData.getServerAddress());
      config.put("workDir", "../work");
      config.put("tempDir", "../temp");
      config.put("systemDir", "../system");
      config.put("authorizationToken", myData.getAuthToken());
      for (final Map.Entry<String, String> param : myData.getCustomAgentConfigurationParameters().entrySet()) {
        config.put(param.getKey(), param.getValue());
      }
      config.put(LocalCloudConstants.IMAGE_ID_PARAM_NAME, getImageId());
      config.put(LocalCloudConstants.INSTANCE_ID_PARAM_NAME, myId);
      PropertiesUtil.storeProperties(config, outConfigFile, null);

      doStart();
    }
    catch (final Exception e) {
      processError(e);
    }
  }

  void restart() {
    waitForStatus(InstanceStatus.RUNNING);
    myStatus = InstanceStatus.RESTARTING;
    try {
      doStop();
      Thread.sleep(3000);
      doStart();
    }
    catch (final Exception e) {
      processError(e);
    }
  }

  void terminate() {
    waitForStatus(InstanceStatus.RUNNING);
    myStatus = InstanceStatus.STOPPING;
    try {
      doStop();
      waitForStatus(InstanceStatus.STOPPED);
      FileUtil.delete(myBaseDir);
    }
    catch (final Exception e) {
      processError(e);
    }
  }

  private void waitForStatus(@NotNull final InstanceStatus status) {
    new WaitFor(STATUS_WAITING_TIMEOUT) {
      @Override
      protected boolean condition() {
        return myStatus == status;
      }
    };
  }

  private void processError(@NotNull final Exception e) {
    final String message = e.getMessage();
    LOG.error(message, e);
    myErrorInfo = new CloudErrorInfo(message, message, e);
    myStatus = InstanceStatus.ERROR;
  }

  private void doStart() throws ExecutionException {
    exec("start");
  }

  private void doStop() throws ExecutionException {
    exec("stop", "force");
  }

  private void exec(@NotNull final String... params) throws ExecutionException {
    final GeneralCommandLine cmd = new GeneralCommandLine();
    final File workDir = new File(myBaseDir, "bin");
    cmd.setWorkDirectory(workDir.getAbsolutePath());
    cmd.setExePath(new File(workDir, SystemInfo.isWindows ? "agent.bat" : "agent.sh").getAbsolutePath());
    cmd.addParameters(params);
    cmd.createProcess();
  }
}
