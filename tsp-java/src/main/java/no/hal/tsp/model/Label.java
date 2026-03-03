package no.hal.tsp.model;

public record Label(
    String text,
    String description,
    String imageUri
) {
  public static Label ofText(String text) {
    return new Label(text, null, null);
  }

  public Label withDescription(String description) {
    return new Label(this.text, description, this.imageUri);
  }
  public Label withImage(String imageUri) {
    return new Label(this.text, this.description, imageUri);
  }
}
