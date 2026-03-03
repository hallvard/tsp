package no.hal.tsp.model;

import java.util.List;

public record Property(
    String name,
    Value value
) {
  public Property(String name) {
    this(name, null);
  }

  public record Value(
      String semanticType,
      String stringValue,
      List<String> stringValues
  ) {
    public Value(String semanticType, String stringValue) {
      this(semanticType, stringValue, null);
    }

    public Value(String semanticType, List<String> stringValues) {
      this(semanticType, null, stringValues);
    }
  }

  public Property withValue(String semanticType, String stringValue) {
    return new Property(name, new Value(semanticType, stringValue));
  }

  public Property withValue(String semanticType, List<String> stringValues) {
    return new Property(name, new Value(semanticType, stringValues));
  }
}
