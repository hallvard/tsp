package no.hal.tsp.emf.server;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import org.eclipse.emf.edit.provider.ComposedImage;

public class ImageSupport {
  
  public record Options(
    Path imagesDir,
    String imagesClientUriBase
  ) {
  }

  private Options options;
  
  public ImageSupport(Options options) {
    this.options = options;
  }

  public void setImagesOptions(Options options) {
    this.options = options;
  }

  String imageUriFor(Object image, Object context) {
    if (options.imagesDir() == null) {
      return null;
    }
    URL imageUrl = imageUrlFor(image, context);
    if (imageUrl == null) {
      return null;
    }
    try {
      byte[] bytes = imageUrl.openStream().readAllBytes();
      String fileName = sha1(bytes) + "." + extensionFor(imageUrl.getPath());
      Path imageFile = options.imagesDir().resolve(fileName);
      if (! Files.exists(imageFile)) {
        Files.createDirectories(options.imagesDir());
        Files.write(imageFile, bytes);
      }
      String uriBase = options.imagesClientUriBase();
      if (uriBase != null && !uriBase.isBlank()) {
        return uriBase.endsWith("/") ? uriBase + fileName : uriBase + "/" + fileName;
      }
      return imageFile.toUri().toString();
    } catch (IOException e) {
      return null;
    }
  }

  private URL imageUrlFor(Object image, Object context) {
    try {
      return switch (image) {
        case URL url -> url;
        case URI uri -> uri.toURL();
        case ComposedImage composedImage -> composedImage.getImages().stream()
           .map(i -> imageUrlFor(i, context))
           .filter(Objects::nonNull)
           .findFirst()
           .orElse(null);
        case String imageString -> {
          if (imageString.contains("://")) {
            yield URI.create(imageString).toURL();
          }
          // URL contextUrl = context.getClass().getClassLoader().getResource(imageString);
          URL contextUrl = context.getClass().getResource(imageString);
          if (contextUrl != null) {
            yield contextUrl;
          }
          yield null;
        }
        default -> null;
      };
    } catch (Exception e) {
      return null;
    }
  }

  private static String extensionFor(String path) {
    if (path == null || path.isBlank()) {
      return "png";
    }
    int dot = path.lastIndexOf('.');
    if (dot > -1 && dot < path.length() - 1) {
      String extension = path.substring(dot + 1).toLowerCase();
      if (extension.length() <= 5) {
        return extension;
      }
    }
    return "png";
  }

  private static String sha1(byte[] bytes) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-1");
      byte[] hash = digest.digest(bytes);
      StringBuilder builder = new StringBuilder(hash.length * 2);
      for (byte b : hash) {
        builder.append(String.format("%02x", b));
      }
      return builder.toString();
    } catch (NoSuchAlgorithmException e) {
      return Integer.toHexString(java.util.Arrays.hashCode(bytes));
    }
  }
}
