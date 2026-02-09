package no.hal.tsp.protocol;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;

/**
 * Document Server Protocol interface.
 * Defines the protocol methods for document-based editors.
 */
public interface DocumentServerProtocol {

  public interface DocumentParams {
    String documentUri();
  }

  /**
   * Parameters for openDocument request.
   */
  record OpenDocumentParams(
      String documentUri
  ) implements DocumentParams {}

  /**
   * Opens the resource given a URI.
   * 
   * @param params Parameters containing the document URI
   * @return A future containing the array of root tree nodes
   */
  @JsonRequest("document/openDocument")
  CompletableFuture<Void> openDocument(OpenDocumentParams params);

  /**
   * Parameters for documentEdited notification.
   */
  record DocumentEditedParams(
      String documentUri
  ) implements DocumentParams {
  }

  @JsonNotification("document/edited")
  void documentEdited(DocumentEditedParams params);

  /**
   * Parameters for undoEdits request.
   */
  record UndoEditsParams(
      String documentUri,
      int count
  ) implements DocumentParams {}

  /**
   * Undoes a number of edits on a resource.
   * 
   * @param params Parameters containing the document URI and the count of edits to undo
   * @return A future indicating the completion of the undo operation
   */
  @JsonRequest("document/undoEdits")
  CompletableFuture<Void> undoEdits(UndoEditsParams params);

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
  CompletableFuture<Void> redoEdits(RedoEditsParams params);

  /**
   * Parameters for saveDocument request.
   * Supports save, saveAs and saveBackup operations based on NewUriOptions.
   */
  record SaveDocumentParams(
      String documentUri,
      Optional<NewUriOptions> newUri
  ) implements DocumentParams {}

  record NewUriOptions(
      String newUri,
      boolean useNewUri
  ) {}

  /**
   * Retrieve the root nodes of the tree for a given document.
   * 
   * @param params Parameters containing the document URI
   * @return A future containing the array of root tree nodes
   */
  @JsonRequest("document/saveDocument")
  CompletableFuture<Void> saveDocument(SaveDocumentParams params);

  /**
   * Parameters for closeDocument request.
   */
  record CloseDocumentParams(
      String documentUri
  ) implements DocumentParams {}

  /**
   * Retrieve the root nodes of the tree for a given document.
   * 
   * @param params Parameters containing the document URI
   * @return A future containing the array of root tree nodes
   */
  @JsonRequest("document/closeDocument")
  CompletableFuture<Void> closeDocument(CloseDocumentParams params);
}
