package no.hal.tsp.emf.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import no.hal.tsp.launcher.ServerProtocolLauncher;
import no.hal.tsp.model.TreeNode;
import no.hal.tsp.protocol.TreeServerProtocol;
import no.hal.tsp.protocol.TreeServerProtocol.GetChildrenParams;
import no.hal.tsp.protocol.DocumentServerProtocol.OpenDocumentParams;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Test for the TSP Server Launcher.
 */
class TspServerLauncherTest {

  private Thread serverThread;
  private TreeServerProtocol client;
  private PipedOutputStream clientOutput;
  private PipedInputStream clientInput;

  @BeforeEach
  void setUp() throws Exception {
    // Create piped streams for client-server communication
    PipedInputStream serverInput = new PipedInputStream();
    PipedOutputStream serverOutput = new PipedOutputStream();

    clientOutput = new PipedOutputStream(serverInput);
    clientInput = new PipedInputStream(serverOutput);

    // Start server in a separate thread
    var launcher = new ServerProtocolLauncher<TreeServerProtocol>(TreeServerProtocol.class, new AbstractTspServerImpl());
    serverThread = new Thread(() -> {
      launcher.startServer(serverInput, serverOutput);
    });
    serverThread.start();

    // Give server time to start
    Thread.sleep(100);

    // Create client launcher
    Launcher<TreeServerProtocol> clientLauncher = new Launcher.Builder<TreeServerProtocol>()
        .setLocalService(new Object()) // No local service needed for client
        .setRemoteInterface(TreeServerProtocol.class)
        .setInput(clientInput)
        .setOutput(clientOutput)
        .create();

    clientLauncher.startListening();
    client = clientLauncher.getRemoteProxy();
  }

  @AfterEach
  void tearDown() throws Exception {
    if (clientOutput != null) {
      clientOutput.close();
    }
    if (clientInput != null) {
      clientInput.close();
    }
    if (serverThread != null) {
      serverThread.interrupt();
    }
  }

  @Test
  void testOpenResourceAndGetChildren() throws Exception {
    // Call openResource with depth = 0
    var documentUri = getClass().getResource("/models/Tournament.ecore").toString();
    var openParams = new OpenDocumentParams(documentUri);
    client.openDocument(openParams).get(5, TimeUnit.SECONDS);
    var getChildrenParams = new GetChildrenParams(documentUri, null, 0);
    TreeNode[] rootNodes = client.getChildren(getChildrenParams).get(5, TimeUnit.SECONDS);

    // Verify we got root nodes
    assertNotNull(rootNodes);
    assertEquals(1, rootNodes.length, "Should have one root node");

    TreeNode root = rootNodes[0];
    assertEquals("object", root.type());
    assertEquals("ecore:EPackage", root.semanticType());
    assertEquals("EPackage", root.label());

    // Call getChildren on the root with depth = 0
    GetChildrenParams childrenParams = new GetChildrenParams(openParams.documentUri(), root.id(), 0);
    CompletableFuture<TreeNode[]> childrenFuture = client.getChildren(childrenParams);

    TreeNode[] children = childrenFuture.get(5, TimeUnit.SECONDS);

    // Verify we got children
    assertNotNull(children);
    // two EAnnotation instances and six EClassifier instances
    assertEquals(8, children.length, "EPackage tournament should have 8 children");
  }
}
