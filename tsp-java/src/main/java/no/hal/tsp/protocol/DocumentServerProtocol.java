package no.hal.tsp.protocol;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;

/**
 * Document Server Protocol interface.
 * Defines the protocol methods for document-based editors.
 */
public interface DocumentServerProtocol extends DocumentApi, UndoRedoApi {
}
