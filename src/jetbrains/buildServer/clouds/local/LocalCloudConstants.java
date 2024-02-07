

package jetbrains.buildServer.clouds.local;

import org.jetbrains.annotations.NotNull;

public interface LocalCloudConstants {
  @NotNull String TYPE = "Local";
  @NotNull String IMAGES_PROFILE_SETTING = "images";
  @NotNull String IMAGE_ID_PARAM_NAME = "cloud.local.image.id";
  @NotNull String INSTANCE_ID_PARAM_NAME = "cloud.local.instance.id";
}