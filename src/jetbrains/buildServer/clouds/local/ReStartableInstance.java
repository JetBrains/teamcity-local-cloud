

package jetbrains.buildServer.clouds.local;

import jetbrains.buildServer.clouds.CloudConstants;
import jetbrains.buildServer.clouds.CloudInstanceUserData;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ScheduledExecutorService;

/**
 * @author Eugene Petrenko (eugene.petrenko@gmail.com)
 *         Date: 31.05.12 18:36
 */
public class ReStartableInstance extends LocalCloudInstance {
  public ReStartableInstance(@NotNull String instanceId,
                             @NotNull LocalCloudImage image,
                             @NotNull final ScheduledExecutorService executor) {
    super(image, instanceId, executor);
  }

  @Override
  public boolean isRestartable() {
    return true;
  }

  @Override
  protected void cleanupStoppedInstance() {
    //NOP, meaning it could start again
  }


  @Override
  public void start(@NotNull CloudInstanceUserData data) {
    data.setAgentRemovePolicy(CloudConstants.AgentRemovePolicyValue.Unauthorize);
    super.start(data);
  }
}