package no.hal.tsp.emf.server;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import no.hal.tsp.protocol.TreeStructureProtocol;
import org.eclipse.lsp4j.jsonrpc.Launcher;

/**
 * Launcher for the Tree Structure Protocol server.
 * Communicates via stdin/stdout using JSON-RPC.
 */
public class TspServerLauncher {
    
    private static final Logger LOG = Logger.getLogger(TspServerLauncher.class.getName());

    public static void main(String[] args) {
        // Configure logging
        LogManager.getLogManager()
            .getLogger("")
            .setLevel(Level.SEVERE);
        LOG.setLevel(Level.INFO);
        
        if (args.length > 0) {
            LOG.info("TSP Server started for document: " + args[0]);
        }
        
        startServer(System.in, System.out);
    }

    public static void startServer(InputStream in, OutputStream out) {
        // Create the server implementation
        TspServerImpl server = new TspServerImpl();
        
        // Create the JSON-RPC launcher
        Launcher<TreeStructureProtocol> launcher = new Launcher.Builder<TreeStructureProtocol>()
            .setLocalService(server)
            .setRemoteInterface(TreeStructureProtocol.class)
            .setInput(in)
            .setOutput(out)
            .create();
        
        // Start listening
        launcher.startListening();
        
        LOG.info("TSP Server is now listening on stdin/stdout");
        
        // Keep the server running
        try {
            launcher.getRemoteProxy();
            // Wait indefinitely
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            LOG.log(Level.SEVERE, "Server interrupted", e);
        }
    }
}
