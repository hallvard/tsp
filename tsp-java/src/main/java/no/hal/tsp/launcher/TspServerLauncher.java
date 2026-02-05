package no.hal.tsp.launcher;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import no.hal.tsp.protocol.TreeServerProtocol;
import org.eclipse.lsp4j.jsonrpc.Launcher;

/**
 * Launcher for the Tree Structure Protocol server.
 * Communicates via stdin/stdout using JSON-RPC.
 */
public class TspServerLauncher {

  private static final Logger LOG = Logger.getLogger(TspServerLauncher.class.getName());

  private final TreeServerProtocol server;

  public TspServerLauncher(TreeServerProtocol server) {
    this.server = server;
  }

  public void startServer(InputStream in, OutputStream out) {
    Launcher<TreeServerProtocol> launcher = new Launcher.Builder<TreeServerProtocol>()
        .setLocalService(server)
        .setRemoteInterface(TreeServerProtocol.class)
        .setInput(in)
        .setOutput(out)
        .create();

    launcher.startListening();
    try {
      launcher.getRemoteProxy();
      Thread.currentThread().join();
    } catch (InterruptedException e) {
      LOG.log(Level.SEVERE, "Server interrupted", e);
    }
  }

  public void startServer() {
    startServer(System.in, System.out);
  }

  public static void main(String[] args) {
    // Configure logging
    LogManager.getLogManager()
        .getLogger("")
        .setLevel(Level.SEVERE);
    LOG.setLevel(Level.INFO);

    if (args.length == 0) {
      LOG.info("TSP Server Implementation class missing as arg[0]");
    }
    try {
      var tspImplClass = Class.forName(args[0]);
      if (! TreeServerProtocol.class.isAssignableFrom(tspImplClass)) {
        LOG.severe("TSP Server Implementation must implement TreeServerProtocol");
        return;
      }
      new TspServerLauncher((TreeServerProtocol) tspImplClass.getDeclaredConstructor().newInstance())
          .startServer();
    } catch (Exception e) {
      LOG.log(Level.SEVERE, "Failed to instantiate TSP Server Implementation", e);
      e.printStackTrace(System.err);
    }
  }
}
