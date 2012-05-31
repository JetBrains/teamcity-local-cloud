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

import jetbrains.buildServer.clouds.CloudClientFactory;
import jetbrains.buildServer.clouds.CloudClientParameters;
import jetbrains.buildServer.clouds.CloudRegistrar;
import jetbrains.buildServer.clouds.CloudState;
import jetbrains.buildServer.serverSide.AgentDescription;
import jetbrains.buildServer.serverSide.InvalidProperty;
import jetbrains.buildServer.serverSide.PropertiesProcessor;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

public class LocalCloudClientFactory implements CloudClientFactory {
  @NotNull private final String myJspPath;
  
  public LocalCloudClientFactory(@NotNull final CloudRegistrar cloudRegistrar,
                                 @NotNull final PluginDescriptor pluginDescriptor) {
    myJspPath = pluginDescriptor.getPluginResourcesPath("profile-settings.jsp");
    cloudRegistrar.registerCloudFactory(this);
  }

  @NotNull
  public String getCloudCode() {
    return LocalCloudConstants.TYPE;
  }

  @NotNull
  public String getDisplayName() {
    return "Local Cloud";
  }

  @Nullable
  public String getEditProfileUrl() {
    return myJspPath;
  }

  @NotNull
  public Map<String, String> getInitialParameterValues() {
    return Collections.emptyMap();
  }

  @NotNull
  public PropertiesProcessor getPropertiesProcessor() {
    return new PropertiesProcessor() {
      @NotNull
      public Collection<InvalidProperty> process(@NotNull final Map<String, String> properties) {
        return Collections.emptyList();
      }
    };
  }

  public boolean canBeAgentOfType(@NotNull final AgentDescription agentDescription) {
    final Map<String, String> configParams = agentDescription.getConfigurationParameters();
    return configParams.containsKey(LocalCloudConstants.IMAGE_ID_PARAM_NAME) && configParams.containsKey(LocalCloudConstants.INSTANCE_ID_PARAM_NAME);
  }

  @NotNull
  public LocalCloudClient createNewClient(@NotNull final CloudState state, @NotNull final CloudClientParameters params) {
    return new LocalCloudClient(params);
  }
}

