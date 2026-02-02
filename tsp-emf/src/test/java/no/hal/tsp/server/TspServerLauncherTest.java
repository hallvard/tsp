package no.hal.tsp.server;

import static org.junit.jupiter.api.Assertions.*;

import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import no.hal.tsp.model.GetChildrenParams;
import no.hal.tsp.model.OpenResourceParams;
import no.hal.tsp.model.TreeNode;
import no.hal.tsp.protocol.TreeStructureProtocol;

/**
 * Test for the TSP Server Launcher.
 */
class TspServerLauncherTest {

    private Thread serverThread;
    private TreeStructureProtocol client;
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
        serverThread = new Thread(() -> {
            TspServerLauncher.startServer(serverInput, serverOutput);
        });
        serverThread.start();

        // Give server time to start
        Thread.sleep(100);

        // Create client launcher
        Launcher<TreeStructureProtocol> clientLauncher = new Launcher.Builder<TreeStructureProtocol>()
            .setLocalService(new Object()) // No local service needed for client
            .setRemoteInterface(TreeStructureProtocol.class)
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
        OpenResourceParams openParams = new OpenResourceParams("file:///test/model.ecore", 0);
        CompletableFuture<TreeNode[]> openFuture = client.openResource(openParams);
        
        TreeNode[] rootNodes = openFuture.get(5, TimeUnit.SECONDS);
        
        // Verify we got root nodes
        assertNotNull(rootNodes);
        assertTrue(rootNodes.length > 0, "Should have at least one root node");
        
        TreeNode root = rootNodes[0];
        assertEquals("root-1", root.getId());
        assertEquals("container", root.getType());
        assertEquals("ecore:EPackage", root.getSemanticType());
        assertEquals("Example Package", root.getLabel());
        
        // Call getChildren on the root with depth = 0
        GetChildrenParams childrenParams = new GetChildrenParams(root.getId(), 0);
        CompletableFuture<TreeNode[]> childrenFuture = client.getChildren(childrenParams);
        
        TreeNode[] children = childrenFuture.get(5, TimeUnit.SECONDS);
        
        // Verify we got children
        assertNotNull(children);
        assertEquals(2, children.length, "Root should have 2 children");
        
        // Verify first child
        assertEquals("node-1", children[0].getId());
        assertEquals("class", children[0].getType());
        assertEquals("ecore:EClass", children[0].getSemanticType());
        assertEquals("ExampleClass", children[0].getLabel());
        
        // Verify second child
        assertEquals("node-2", children[1].getId());
        assertEquals("attribute", children[1].getType());
        assertEquals("ecore:EAttribute", children[1].getSemanticType());
        assertEquals("exampleAttribute", children[1].getLabel());
    }
}
