package no.hal.tsp.protocol;

import java.util.concurrent.CompletableFuture;
import no.hal.tsp.model.MenuItem.Menu;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;

/**
 * Tree Edit Protocol interface.
 * Defines the protocol methods for tree-based editors.
 */
public interface TreeEditApi {

  /**
   * Parameters for getCommandMenu request.
   */
  record GetCommandMenuParams(
      String documentUri,
      String treeNodeId
  ) implements TreeNodeParams {}

  /**
   * Retrieve the command menu for a given tree node.
   * 
   * @param params Parameters containing a tree node reference
   * @return A future containing the command menu for the specified tree node
   */
  @JsonRequest("tree/getCommandMenu")
  CompletableFuture<Menu> getCommandMenu(GetCommandMenuParams params);

  /**
   * Parameters for doCommand request.
   */
  record DoCommandParams(
      String documentUri,
      String treeNodeId,
      String commandId
  ) implements TreeNodeParams {}

  /**
   * Parameters for treeEdited notification.
   */
  record TreeEditedParams(
      String documentUri,
      String[] treeNodeIds
  ) implements TreeNodeParams {
    public String treeNodeId() {
      return treeNodeIds.length > 0 ? treeNodeIds[0] : null;
    }
  }

  /**
   * Retrieve the command menu for a given tree node.
   * 
   * @param params Parameters containing a tree node reference
   * @return A future containing the command menu for the specified tree node
   */
  @JsonRequest("tree/doCommand")
  CompletableFuture<TreeEditedParams> doCommand(DoCommandParams params);
}
