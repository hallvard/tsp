package no.hal.tsp.protocol;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;

/**
 * Document API.
 * Defines the protocol methods for document-based editors.
 */
public interface DocumentApi {

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
