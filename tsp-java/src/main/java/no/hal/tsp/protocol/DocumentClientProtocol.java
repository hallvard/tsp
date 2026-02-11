package no.hal.tsp.protocol;

import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;

/**
 * Document Server Protocol interface.
 * Defines the protocol methods for document-based editors.
 */
public interface DocumentClientProtocol {

  public interface Consumer {
    void setDocumentClient(DocumentClientProtocol documentClient);
  }

  @JsonNotification("document/edited")
  void documentEdited(DocumentServerProtocol.DocumentEditedParams params);
}
