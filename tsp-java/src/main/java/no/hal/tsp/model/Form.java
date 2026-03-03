package no.hal.tsp.model;

import java.util.List;

public record Form(
    List<Item> items
) {
  
  public record Item(
      Property property,
      Label label,
      List<Property.Value> valueOptions,
      boolean editable
  ) {
  }

  public record Validation(
      String propertyName,
      Status status,
      String message
  ) {
    public enum Status {
      OK, WARNING, ERROR
    }
  }
}
