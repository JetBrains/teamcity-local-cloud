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
    FileUtil.delete(getBaseDir());
  }

  @Override
  public void start(@NotNull CloudInstanceUserData data) {
    data.setAgentRemovePolicy(CloudConstants.AgentRemovePolicyValue.RemoveAgent);
    super.start(data);
  }
}
