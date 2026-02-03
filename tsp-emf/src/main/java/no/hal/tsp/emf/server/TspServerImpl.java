package no.hal.tsp.emf.server;

import java.util.concurrent.CompletableFuture;
import no.hal.tsp.model.TreeNode;
import no.hal.tsp.protocol.TreeStructureProtocol;

/**
 * Implementation of the Tree Structure Protocol server.
 */
public class TspServerImpl implements TreeStructureProtocol {

  TreeNode root = new TreeNode(
      "root-1",
      "container",
      "ecore:EPackage",
      "Example Package");

  TreeNode child1 = new TreeNode(
      "node-1",
      "container",
      "ecore:EClass",
      "ExampleClass");

  TreeNode child2 = new TreeNode(
      "node-2",
      "leaf",
      "ecore:EAttribute",
      "exampleAttribute");

  @Override
  public CompletableFuture<TreeNode[]> openResource(OpenResourceParams params) {
    return CompletableFuture.completedFuture(new TreeNode[] { root });
  }

  @Override
  public CompletableFuture<TreeNode[]> getChildren(GetChildrenParams params) {
    return CompletableFuture.completedFuture(getChildrenN(params.treeNodeId(), params.depth()));
  }

  private TreeNode[] getChildrenN(String treeNodeId, int depth) {
    var children = getChildren1(treeNodeId);
    if (depth > 0) {
      for (int i = 0; i < children.length; i++) {
        var child = children[i];
        var childChildren = getChildrenN(child.id(), depth - 1);
        children[i] = new TreeNode(child.id(), child.type(), child.semanticType(), child.label(), childChildren);
      }
    }
    return children;
  }

  private TreeNode[] getChildren1(String treeNodeId) {
    return switch (treeNodeId) {
      case null -> new TreeNode[] { root };
      case "root-1" -> new TreeNode[]{ child1, child2 };
      default -> new TreeNode[0];
    };
  }
}
