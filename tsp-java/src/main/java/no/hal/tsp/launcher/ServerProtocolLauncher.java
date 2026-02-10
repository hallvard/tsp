package no.hal.tsp.launcher;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import no.hal.tsp.protocol.DocumentServerProtocol;
import org.eclipse.lsp4j.jsonrpc.Launcher;

/**
 * Launcher for the Tree Structure Protocol server.
 * Communicates via stdin/stdout using JSON-RPC.
 */
public class ServerProtocolLauncher<SP extends DocumentServerProtocol> {

  private static final Logger LOG = Logger.getLogger(ServerProtocolLauncher.class.getName());

  private final Class<SP> protocolClass;
  private final SP server;

  public ServerProtocolLauncher(Class<SP> protocolClass, SP server) {
    this.protocolClass = protocolClass;
    this.server = server;
  }

  public void startServer(InputStream in, OutputStream out) {
    Launcher<SP> launcher = new Launcher.Builder<SP>()
        .setLocalService(server)
        .setRemoteInterface(protocolClass)
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

    if (args.length < 2) {
      LOG.info("DSP Server protocol and implementation classes missing in args");
      return;
    }
    try {
      var dspClass = Class.forName(args[0]);
      var dspImplClass = Class.forName(args[1]);
      if (! DocumentServerProtocol.class.isAssignableFrom(dspClass)) {
        LOG.severe("DSP Server protocol must extend DocumentServerProtocol");
        return;
      }
      if (! DocumentServerProtocol.class.isAssignableFrom(dspImplClass)) {
        LOG.severe("DSP Server Implementation must implement DocumentServerProtocol");
        return;
      }
      var dsp = (DocumentServerProtocol) dspImplClass.getDeclaredConstructor().newInstance();
      new ServerProtocolLauncher((Class<DocumentServerProtocol>) dspClass, dsp)
          .startServer();
    } catch (Exception e) {
      LOG.log(Level.SEVERE, "Failed to instantiate DSP Server Implementation", e);
      e.printStackTrace(System.err);
    }
  }
}
