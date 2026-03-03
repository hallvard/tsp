package no.hal.tsp.protocol;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;

/**
 * Server configuration API.
 */
public interface ServerConfigApi {

  record ConfigureParams(
      Map<String, String> settings
  ) {
  }

  @JsonRequest("server/configure")
  CompletableFuture<Void> configure(ConfigureParams params);
}
