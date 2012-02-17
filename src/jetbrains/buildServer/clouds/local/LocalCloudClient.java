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

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import jetbrains.buildServer.clouds.*;
import jetbrains.buildServer.serverSide.AgentDescription;
import jetbrains.buildServer.serverSide.BuildServerAdapter;
import jetbrains.buildServer.serverSide.SBuildAgent;
import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LocalCloudClient extends BuildServerAdapter implements CloudClientEx {
  @NotNull private final SBuildServer myServer;
  @NotNull private final List<LocalCloudImage> myImages = new ArrayList<LocalCloudImage>();
  @Nullable private final CloudErrorInfo myErrorInfo;

  LocalCloudClient(@NotNull final SBuildServer server, @NotNull final CloudClientParameters params) {
    myServer = server;
    myServer.addListener(this);
    
    final String images = params.getParameter(LocalCloudConstants.IMAGES_PROFILE_SETTING);
    if (images == null || images.trim().length() == 0) {
      myErrorInfo = new CloudErrorInfo("No images specified");
      return;
    }

    final IdGenerator imageIdGenerator = new IdGenerator();

    final StringBuilder error = new StringBuilder();
    for (final  String imageInfo : StringUtil.splitByLines(images.trim())) {
      final int atPos = imageInfo.indexOf('@');
      if (atPos < 0) {
        error.append(" Failed to parse image info: \"" + imageInfo + "\".");
        continue;
      }

      final String imageName = imageInfo.substring(0, atPos).trim();
      final String agentHomePath = imageInfo.substring(atPos + 1).trim();
      myImages.add(new LocalCloudImage(imageIdGenerator.next(), imageName, agentHomePath));
    }
    
    myErrorInfo = error.length() == 0 ? null : new CloudErrorInfo(error.substring(1));
  }

  public boolean isInitialized() {
    return true;
  }

  @Nullable
  public LocalCloudImage findImageById(@NotNull final String imageId) throws CloudException {
    for (final LocalCloudImage image : myImages) {
      if (image.getId().equals(imageId)) {
        return image;
      }
    }
    return null;
  }

  @Nullable
  public LocalCloudInstance findInstanceByAgent(@NotNull final AgentDescription agentDescription) {
    final LocalCloudImage image = findImage(agentDescription);
    if (image == null) return null;

    final String instanceId = findInstanceId(agentDescription);
    if (instanceId == null) return null;

    return image.findInstanceById(instanceId);
  }

  @NotNull
  public Collection<? extends CloudImage> getImages() throws CloudException {
    return Collections.unmodifiableList(myImages);
  }

  @Nullable
  public CloudErrorInfo getErrorInfo() {
    return myErrorInfo;
  }

  public boolean canStartNewInstance(@NotNull final CloudImage image) {
    return true;
  }

  public String generateAgentName(@NotNull final AgentDescription agentDescription) {
    final LocalCloudImage image = findImage(agentDescription);
    if (image == null) return null;

    final String instanceId = findInstanceId(agentDescription);
    if (instanceId == null) return null;

    return generateAgentName(image, instanceId);
  }

  @NotNull
  public static String generateAgentName(@NotNull final LocalCloudImage image, @NotNull final String instanceId) {
    return "img-" + image.getName() + "-" + instanceId;
  }

  @Override
  public void agentRegistered(@NotNull final SBuildAgent agent, final long currentlyRunningBuildId) {
    final LocalCloudInstance instance = findInstanceByAgent(agent);
    if (instance != null) {
      instance.setAgentRegistered(true);
    }
  }

  @Override
  public void agentUnregistered(@NotNull final SBuildAgent agent) {
    final LocalCloudInstance instance = findInstanceByAgent(agent);
    if (instance != null) {
      instance.setAgentRegistered(false);
      instance.getImage().forgetInstance(instance);
    }
  }

  @NotNull
  public CloudInstance startNewInstance(@NotNull final CloudImage image, @NotNull final CloudInstanceUserData data) throws QuotaException {
    return ((LocalCloudImage)image).startNewInstance(data);
  }

  public void restartInstance(@NotNull final CloudInstance instance) {
    ((LocalCloudInstance)instance).restart();
  }

  public void terminateInstance(@NotNull final CloudInstance instance) {
    ((LocalCloudInstance)instance).terminate();
  }

  public void dispose() {
    myServer.removeListener(this);
    for (final LocalCloudImage image : myImages) {
      image.dispose();
    }
    myImages.clear();
  }

  @Nullable
  private LocalCloudImage findImage(@NotNull final AgentDescription agentDescription) {
    final String imageId = agentDescription.getConfigurationParameters().get(LocalCloudConstants.IMAGE_ID_PARAM_NAME);
    return imageId == null ? null : findImageById(imageId);
  }

  @Nullable
  private String findInstanceId(@NotNull final AgentDescription agentDescription) {
    return agentDescription.getConfigurationParameters().get(LocalCloudConstants.INSTANCE_ID_PARAM_NAME);
  }
}