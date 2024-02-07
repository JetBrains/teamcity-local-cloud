

package jetbrains.buildServer.clouds.local;

import jetbrains.buildServer.clouds.*;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.serverSide.AgentDescription;
import jetbrains.buildServer.serverSide.BuildServerAdapter;
import jetbrains.buildServer.util.NamedDeamonThreadFactory;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class LocalCloudClient extends BuildServerAdapter implements CloudClientEx {
  @NotNull private final List<LocalCloudImage> myImages = new ArrayList<LocalCloudImage>();
  @Nullable private final CloudErrorInfo myErrorInfo;
  @NotNull private final ScheduledExecutorService myExecutor = Executors.newSingleThreadScheduledExecutor(new NamedDeamonThreadFactory("local-cloud-image"));

  public LocalCloudClient(@NotNull final CloudClientParameters params) {
    final String images = params.getParameter(LocalCloudConstants.IMAGES_PROFILE_SETTING);
    if (images == null || images.trim().length() == 0) {
      myErrorInfo = new CloudErrorInfo("No images specified");
      return;
    }

    final IdGenerator imageIdGenerator = new IdGenerator();

    final StringBuilder error = new StringBuilder();
    final String[] allLines = StringUtil.splitByLines(images.trim());

    for (String imageInfo : allLines) {
      imageInfo = imageInfo.trim();
      if (imageInfo.isEmpty() || imageInfo.startsWith("@@")) continue;

      final int atPos = imageInfo.indexOf('@');
      if (atPos < 0) {
        error.append(" Failed to parse image info: \"").append(imageInfo).append("\".");
        continue;
      }

      final String imageName = imageInfo.substring(0, atPos).trim();
      final String agentHomePath = imageInfo.substring(atPos + 1).trim();
      final LocalCloudImage image = new LocalCloudImage(imageIdGenerator.next(), imageName, agentHomePath, myExecutor);

      for (String line : allLines) {
        String prefix = "@@" + imageName + ":";
        if (!line.startsWith(prefix)) continue;
        line = line.substring(prefix.length()).trim();

        if (line.contains("reuse")) image.setIsReusable(true);
        if (line.contains("delay")) image.setIsEternalStarting(true);
        if (line.contains("agentPoolId:")) {
          String poolIdStr = line.substring("agentPoolId:".length());
          try {
            Integer poolId = Integer.parseInt(poolIdStr);
            image.setAgentPoolId(poolId);
          } catch (NumberFormatException e) {
            Loggers.SERVER.warnAndDebugDetails("Could not parse local cloud image pool id parameter", e);
          }
        }
        if (!line.startsWith("prop:")) continue;
        String[] kv = line.substring(5).trim().split("=", 2);
        if (kv.length == 2) {
          image.addExtraProperty(kv[0].trim(), kv[1].trim());
        }
      }

      myImages.add(image);
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
    for (final LocalCloudImage image : myImages) {
      image.dispose();
    }
    myImages.clear();
    myExecutor.shutdown();
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