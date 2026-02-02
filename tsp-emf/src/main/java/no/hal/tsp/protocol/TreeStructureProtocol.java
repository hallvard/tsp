package no.hal.tsp.protocol;

import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import java.util.concurrent.CompletableFuture;
import no.hal.tsp.model.GetChildrenParams;
import no.hal.tsp.model.OpenResourceParams;
import no.hal.tsp.model.TreeNode;

/**
 * Tree Structure Protocol interface.
 * Defines the protocol methods for tree-based editors.
 */
public interface TreeStructureProtocol {

  /**
   * Retrieve the root nodes of the tree for a given document.
   * 
   * @param params Parameters containing the document URI
   * @return A future containing the array of root tree nodes
   */
  @JsonRequest("tree/openResource")
  CompletableFuture<TreeNode[]> openResource(OpenResourceParams params);

  /**
   * Retrieve the root nodes of the tree for a given document.
   * 
   * @param params Parameters containing the document URI
   * @return A future containing the array of root tree nodes
   */
  @JsonRequest("tree/getChildren")
  CompletableFuture<TreeNode[]> getChildren(GetChildrenParams params);
}
