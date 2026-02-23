package no.hal.tsp.model;

public record Form(
    String id,
    FormItem[] items
) {

  public sealed interface FormItem permits TextField {
    String id();
    Label label();
    String kind();
    boolean editable();
  }

  public record TextField(
      String id,
      Label label,
      String value,
      boolean editable
  ) implements FormItem {

    @Override
    public String kind() {
      return "text";
    }
  }
}