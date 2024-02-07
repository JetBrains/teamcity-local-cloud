

package jetbrains.buildServer.clouds.local;

import jetbrains.buildServer.clouds.CloudConstants;
import jetbrains.buildServer.clouds.CloudInstanceUserData;
import jetbrains.buildServer.util.FileUtil;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ScheduledExecutorService;

public class OneUseLocalCloudInstance extends LocalCloudInstance {

  public OneUseLocalCloudInstance(@NotNull final String instanceId,
                                  @NotNull final LocalCloudImage image,
                                  @NotNull final ScheduledExecutorService executor) {
    super(image, instanceId, executor);
  }

  @Override
  public boolean isRestartable() {
    return false;
  }

  @Override
  protected void cleanupStoppedInstance() {
    getImage().forgetInstance(this);
    FileUtil.symlinkAwareDelete(getBaseDir());
  }

  @Override
  public void start(@NotNull CloudInstanceUserData data) {
    data.setAgentRemovePolicy(CloudConstants.AgentRemovePolicyValue.RemoveAgent);
    super.start(data);
  }
}