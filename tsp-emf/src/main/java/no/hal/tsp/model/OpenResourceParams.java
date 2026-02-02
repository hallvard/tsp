package no.hal.tsp.model;

/**
 * Parameters for openResource request.
 */
public class OpenResourceParams {

  private String documentUri;
  private int depth;
  public OpenResourceParams() {
  }

  public OpenResourceParams(String documentUri, int depth) {
      this.documentUri = documentUri;
      this.depth = depth;
  }

  public String getDocumentUri() {
      return documentUri;
  }

  public void setDocumentUri(String documentUri) {
      this.documentUri = documentUri;
  }

  public int getDepth() {
      return depth;
  }

  public void setDepth(int depth) {
      this.depth = depth;
  }
}

