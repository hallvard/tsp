package no.hal.tsp.model;

public sealed interface MenuItem {

  Label label();
  
  public record Menu(Label label, MenuItem... items) implements MenuItem {
  }
  public record Command(String id, Label label) implements MenuItem {
  }
}
