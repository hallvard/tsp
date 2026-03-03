package no.hal.tsp.protocol;

/**
 * Tree Structure Protocol interface.
 * Defines the protocol methods for tree-based editors.
 */
public interface TreeServerProtocol extends
    DocumentServerProtocol,
    ServerConfigApi,
    TreeStructureApi,
    TreeEditApi,
    FormApi {
}
