package no.hal.tsp.server;

import java.util.concurrent.CompletableFuture;
import no.hal.tsp.model.GetChildrenParams;
import no.hal.tsp.model.OpenResourceParams;
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
      "class",
      "ecore:EClass",
      "ExampleClass");

  TreeNode child2 = new TreeNode(
      "node-2",
      "attribute",
      "ecore:EAttribute",
      "exampleAttribute");

  @Override
  public CompletableFuture<TreeNode[]> openResource(OpenResourceParams params) {
    return CompletableFuture.completedFuture(new TreeNode[] { root });
  }

  @Override
  public CompletableFuture<TreeNode[]> getChildren(GetChildrenParams params) {
    switch (params.getTreeNodeId()) {
      case "root-1":
        return CompletableFuture.completedFuture(new TreeNode[]{ child1, child2 });
      default:
        return CompletableFuture.completedFuture(new TreeNode[0]);
    }
  }
}
