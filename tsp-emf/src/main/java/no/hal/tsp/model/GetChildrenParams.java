package no.hal.tsp.model;

/**
 * Parameters for getChildren request.
 */
public class GetChildrenParams {

  private String treeNodeId;
  private int depth;

  public GetChildrenParams() {
  }

  public GetChildrenParams(String treeNodeId, int depth) {
      this.treeNodeId = treeNodeId;
      this.depth = depth;
  }

  public String getTreeNodeId() {
      return treeNodeId;
  }

  public void setTreeNodeId(String treeNodeId) {
      this.treeNodeId = treeNodeId;
  }

  public int getDepth() {
      return depth;
  }

  public void setDepth(int depth) {
      this.depth = depth;
  }
}
