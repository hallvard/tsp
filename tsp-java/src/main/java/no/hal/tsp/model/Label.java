package no.hal.tsp.model;

public record Label(
    String text
) {
  public static Label ofText(String text) {
    return new Label(text);
  }
}
