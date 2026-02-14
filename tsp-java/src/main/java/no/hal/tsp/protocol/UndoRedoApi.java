package no.hal.tsp.protocol;

import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;

/**
 * Document Server Protocol interface.
 * Defines the protocol methods for document-based editors.
 */
public interface UndoRedoApi {

  /**
   * Parameters for undoEdits request.
   */
  record UndoEditsParams(
      String documentUri,
      int count
  ) implements DocumentParams {}

  /**
   * Parameters for documentEdited notification.
   */
  record DocumentEditedParams(
      String documentUri
  ) implements DocumentParams {
  }

  /**
   * Undoes a number of edits on a resource.
   * 
   * @param params Parameters containing the document URI and the count of edits to undo
   * @return A future indicating the completion of the undo operation
   */
  @JsonRequest("document/undoEdits")
  CompletableFuture<DocumentEditedParams> undoEdits(UndoEditsParams params);

  /**
   * Parameters for undoEdits request.
   */
  record RedoEditsParams(
      String documentUri,
      int count
  ) implements DocumentParams {}

  /**
   * Redoes a number of edits on a resource.
   * 
   * @param params Parameters containing the document URI and the count of edits to redo
   * @return A future indicating the completion of the redo operation
   */
  @JsonRequest("document/redoEdits")
  CompletableFuture<DocumentEditedParams> redoEdits(RedoEditsParams params);
}
