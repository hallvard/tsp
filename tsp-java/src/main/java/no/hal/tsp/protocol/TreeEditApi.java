package no.hal.tsp.protocol;

import java.util.concurrent.CompletableFuture;
import no.hal.tsp.model.Form;
import no.hal.tsp.model.MenuItem.Menu;
import no.hal.tsp.protocol.UndoRedoApi.DocumentEditedParams;
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
   * Parameters for getForm request.
   */
  record GetFormParams(
      String documentUri,
      String treeNodeId
  ) implements TreeNodeParams {}

  /**
   * Retrieve the form for a given tree node.
   * 
   * @param params Parameters containing a tree node reference
   * @return A future containing the form for the specified tree node
   */
  @JsonRequest("tree/getForm")
  CompletableFuture<Form> getForm(GetFormParams params);

  /**
   * Parameters for doCommand request.
   */
  record DoCommandParams(
      String documentUri,
      String treeNodeId,
      String commandId
  ) implements TreeNodeParams {}

  /**
   * Retrieve the command menu for a given tree node.
   * 
   * @param params Parameters containing a tree node reference
   * @return A future containing the command menu for the specified tree node
   */
  @JsonRequest("tree/doCommand")
  CompletableFuture<DocumentEditedParams> doCommand(DoCommandParams params);
}
