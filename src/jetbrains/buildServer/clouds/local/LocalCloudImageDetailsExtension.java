

package jetbrains.buildServer.clouds.local;

import jetbrains.buildServer.clouds.web.CloudImageDetailsExtensionBase;
import jetbrains.buildServer.web.openapi.PagePlaces;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.jetbrains.annotations.NotNull;

public class LocalCloudImageDetailsExtension extends CloudImageDetailsExtensionBase<LocalCloudImage> {
  public LocalCloudImageDetailsExtension(@NotNull final PagePlaces pagePlaces, @NotNull final PluginDescriptor pluginDescriptor) {
    super(LocalCloudImage.class, pagePlaces, pluginDescriptor, "image-details.jsp");
    register();
  }
}