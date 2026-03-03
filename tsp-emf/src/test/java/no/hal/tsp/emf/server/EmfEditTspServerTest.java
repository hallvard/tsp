package no.hal.tsp.emf.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import no.hal.tsp.model.Label;
import no.hal.tsp.model.MenuItem;
import no.hal.tsp.model.Property;
import no.hal.tsp.model.TreeNode;
import no.hal.tsp.protocol.FormApi.CommitTreeNodeFormParams;
import no.hal.tsp.protocol.DocumentApi.OpenDocumentParams;
import no.hal.tsp.protocol.FormApi.GetTreeNodeFormParams;
import no.hal.tsp.protocol.TreeServerProtocol;
import no.hal.tsp.protocol.TreeEditApi.DoCommandParams;
import no.hal.tsp.protocol.TreeEditApi.GetCommandMenuParams;
import no.hal.tsp.protocol.TreeStructureApi.GetChildrenParams;
import no.hal.tsp.protocol.UndoRedoApi.EditKind;
import no.hal.tsp.protocol.UndoRedoApi.UndoEditsParams;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Test for the TSP Server Launcher.
 */
class EmfEditTspServerTest {

  private static List<MenuItem.Command> commandsIn(MenuItem.Menu menu) {
    List<MenuItem.Command> commands = new java.util.ArrayList<>();
    for (var item : menu.items()) {
      if (item instanceof MenuItem.Command command) {
        commands.add(command);
      } else if (item instanceof MenuItem.Menu submenu) {
        commands.addAll(commandsIn(submenu));
      }
    }
    return commands;
  }

  private static List<MenuItem.Command> commandsOfKind(MenuItem.Menu menu, String kindPrefix) {
    return commandsIn(menu).stream()
        .filter(command -> command.id().startsWith(kindPrefix))
        .toList();
  }

  private TreeServerProtocol tsp;
  private OpenDocumentParams openDocumentParams;

  @BeforeEach
  void setUp() throws Exception {
    tsp = new EmfEditTspServer();
    var documentUri = getClass().getResource("/models/Tournament.ecore").toString();
    openDocumentParams = new OpenDocumentParams(documentUri);
    tsp.openDocument(openDocumentParams).get(5, TimeUnit.SECONDS);
  }

  @AfterEach
  void tearDown() throws Exception {
  }

  private TreeNode checkEPackageRootNode() throws Exception {
    var getChildrenParams = new GetChildrenParams(openDocumentParams.documentUri(), null, 0);
    TreeNode[] rootNodes = tsp.getChildren(getChildrenParams).get(5, TimeUnit.SECONDS);

    // Verify we got root nodes
    assertNotNull(rootNodes);
    assertEquals(1, rootNodes.length, "Should have one root node");

    TreeNode root = rootNodes[0];
    assertEquals("object", root.type());
    assertEquals("ecore:EPackage", root.semanticType());
    assertEquals(Label.ofText("tournament"), root.label());

    return root;
  }

  private <T> T get(CompletableFuture<T> future) throws Exception {
    return future.get(5, TimeUnit.SECONDS);
  }

  @Test
  void testOpenResourceAndGetChildren() throws Exception {
    TreeNode root = checkEPackageRootNode();

    // Call getChildren on the root with depth = 0
    GetChildrenParams childrenParams = new GetChildrenParams(openDocumentParams.documentUri(), root.id(), 0);
    CompletableFuture<TreeNode[]> childrenFuture = tsp.getChildren(childrenParams);

    TreeNode[] children = get(childrenFuture);

    // Verify we got children
    assertNotNull(children);
    // two EAnnotation instances and six EClassifier instances
    assertEquals(8, children.length, "EPackage tournament should have 8 children");
  }

  @Test
  void testOpenResourceAndGetEPackageForm() throws Exception {
    TreeNode root = checkEPackageRootNode();

    var treeFormParams = new GetTreeNodeFormParams(openDocumentParams.documentUri(), root.id());
    var ePackageForm = get(tsp.getTreeNodeForm(treeFormParams));

    var expectedPropertyNames = List.of("name", "nsURI", "nsPrefix");
    for (var formItem : ePackageForm.items()) {
      var propertyName = formItem.property().name();
      assertTrue(expectedPropertyNames.contains(propertyName),
          "Property name not one of " + expectedPropertyNames);
    }
  }

  @Test
  void testCommitEPackageNsPrefixUpdatesModelValue() throws Exception {
    TreeNode root = checkEPackageRootNode();
    var treeFormParams = new GetTreeNodeFormParams(openDocumentParams.documentUri(), root.id());

    var ePackageForm = get(tsp.getTreeNodeForm(treeFormParams));
    var nsPrefixProperty = ePackageForm.items().stream()
        .map(item -> item.property())
        .filter(property -> "nsPrefix".equals(property.name()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Missing nsPrefix property in form"));

    String updatedNsPrefix = nsPrefixProperty.value().stringValue() + "_updated";
    var commitParams = new CommitTreeNodeFormParams(
        openDocumentParams.documentUri(),
        root.id(),
        List.of(new Property("nsPrefix").withValue(nsPrefixProperty.value().semanticType(), updatedNsPrefix))
    );
    get(tsp.commitTreeNodeForm(commitParams));

    var updatedForm = get(tsp.getTreeNodeForm(treeFormParams));
    var updatedNsPrefixProperty = updatedForm.items().stream()
        .map(item -> item.property())
        .filter(property -> "nsPrefix".equals(property.name()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Missing nsPrefix property in updated form"));

    assertEquals(updatedNsPrefix, updatedNsPrefixProperty.value().stringValue());
  }

  @Test
  void testUndoCommitEPackageNsPrefixRestoresModelValueAndKind() throws Exception {
    TreeNode root = checkEPackageRootNode();
    var treeFormParams = new GetTreeNodeFormParams(openDocumentParams.documentUri(), root.id());

    var initialForm = get(tsp.getTreeNodeForm(treeFormParams));
    var nsPrefixProperty = initialForm.items().stream()
        .map(item -> item.property())
        .filter(property -> "nsPrefix".equals(property.name()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Missing nsPrefix property in form"));
    String originalNsPrefix = nsPrefixProperty.value().stringValue();

    String updatedNsPrefix = originalNsPrefix + "_updated";
    var commitParams = new CommitTreeNodeFormParams(
        openDocumentParams.documentUri(),
        root.id(),
        List.of(new Property("nsPrefix").withValue(nsPrefixProperty.value().semanticType(), updatedNsPrefix))
    );
    get(tsp.commitTreeNodeForm(commitParams));

    var updatedForm = get(tsp.getTreeNodeForm(treeFormParams));
    var updatedNsPrefixProperty = updatedForm.items().stream()
        .map(item -> item.property())
        .filter(property -> "nsPrefix".equals(property.name()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Missing nsPrefix property in updated form"));
    assertEquals(updatedNsPrefix, updatedNsPrefixProperty.value().stringValue());

    var undoResult = get(tsp.undoEdits(new UndoEditsParams(openDocumentParams.documentUri(), 1)));
    assertEquals(EditKind.UNDO.name(), undoResult.kind());
    assertEquals(openDocumentParams.documentUri(), undoResult.documentUri());
    assertTrue(undoResult.affectedObjectIds() != null && !undoResult.affectedObjectIds().isEmpty(),
      "Undo result should include affected object ids");

    var revertedForm = get(tsp.getTreeNodeForm(treeFormParams));
    var revertedNsPrefixProperty = revertedForm.items().stream()
        .map(item -> item.property())
        .filter(property -> "nsPrefix".equals(property.name()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Missing nsPrefix property in reverted form"));
    assertEquals(originalNsPrefix, revertedNsPrefixProperty.value().stringValue());
  }

  @Test
  void testGetCommandMenuContainsCreateCommands() throws Exception {
    TreeNode root = checkEPackageRootNode();
    var menu = get(tsp.getCommandMenu(new GetCommandMenuParams(openDocumentParams.documentUri(), root.id())));
    assertNotNull(menu);
    assertNotNull(menu.items());
    assertTrue(menu.items().length > 0, "Expected at least one menu item");
    assertTrue(java.util.Arrays.stream(menu.items()).anyMatch(item -> item instanceof MenuItem.Menu),
      "Expected nested submenu item (e.g. New...)");
    var createCommands = commandsOfKind(menu, "new|");
    assertTrue(!createCommands.isEmpty(), "Expected at least one create command");
    assertTrue(createCommands.stream().allMatch(command -> command.id().startsWith("new|")),
      "Expected generated command ids to use encoded 'new|...' format");
    assertTrue(createCommands.stream().anyMatch(command -> command.label().text().contains(" in ")),
      "Expected at least one create command label to include containment reference context");
  }

  @Test
  void testDoCreateCommandAddsChild() throws Exception {
    TreeNode root = checkEPackageRootNode();

    var initialChildren = get(tsp.getChildren(new GetChildrenParams(openDocumentParams.documentUri(), root.id(), 0)));
    int initialCount = initialChildren.length;

    var menu = get(tsp.getCommandMenu(new GetCommandMenuParams(openDocumentParams.documentUri(), root.id())));
    var createCommand = commandsOfKind(menu, "new|").stream()
        .findFirst()
        .orElseThrow(() -> new AssertionError("Expected at least one create command"));

    var edited = get(tsp.doCommand(new DoCommandParams(openDocumentParams.documentUri(), root.id(), createCommand.id())));
    assertEquals(openDocumentParams.documentUri(), edited.documentUri());
    assertTrue(edited.treeNodeIds().length > 0);

    var updatedChildren = get(tsp.getChildren(new GetChildrenParams(openDocumentParams.documentUri(), root.id(), 0)));
    assertTrue(updatedChildren.length > initialCount, "Expected create command to add a child node");
  }

  @Test
  void testUndoAfterCreateCommandRestoresChildCount() throws Exception {
    TreeNode root = checkEPackageRootNode();

    int initialCount = get(tsp.getChildren(new GetChildrenParams(openDocumentParams.documentUri(), root.id(), 0))).length;

    var menu = get(tsp.getCommandMenu(new GetCommandMenuParams(openDocumentParams.documentUri(), root.id())));
    var createCommand = commandsOfKind(menu, "new|").stream()
        .findFirst()
        .orElseThrow(() -> new AssertionError("Expected at least one create command"));

    get(tsp.doCommand(new DoCommandParams(openDocumentParams.documentUri(), root.id(), createCommand.id())));
    int createdCount = get(tsp.getChildren(new GetChildrenParams(openDocumentParams.documentUri(), root.id(), 0))).length;
    assertTrue(createdCount > initialCount, "Expected create command to add a child node");

    get(tsp.undoEdits(new UndoEditsParams(openDocumentParams.documentUri(), 1)));
    int undoneCount = get(tsp.getChildren(new GetChildrenParams(openDocumentParams.documentUri(), root.id(), 0))).length;
    assertEquals(initialCount, undoneCount, "Undo should revert child creation");
  }

  @Test
  void testDeleteCommandRemovesNodeAndUndoRestores() throws Exception {
    TreeNode root = checkEPackageRootNode();
    var children = get(tsp.getChildren(new GetChildrenParams(openDocumentParams.documentUri(), root.id(), 0)));
    assertTrue(children.length > 0, "Expected at least one child to delete");

    var childToDelete = children[0];
    var initialCount = children.length;

    var menu = get(tsp.getCommandMenu(new GetCommandMenuParams(openDocumentParams.documentUri(), childToDelete.id())));
    var deleteCommand = commandsOfKind(menu, "delete|").stream()
        .findFirst()
        .orElseThrow(() -> new AssertionError("Expected delete command"));

    get(tsp.doCommand(new DoCommandParams(openDocumentParams.documentUri(), childToDelete.id(), deleteCommand.id())));
    int deletedCount = get(tsp.getChildren(new GetChildrenParams(openDocumentParams.documentUri(), root.id(), 0))).length;
    assertEquals(initialCount - 1, deletedCount, "Delete should remove one child node");

    get(tsp.undoEdits(new UndoEditsParams(openDocumentParams.documentUri(), 1)));
    int restoredCount = get(tsp.getChildren(new GetChildrenParams(openDocumentParams.documentUri(), root.id(), 0))).length;
    assertEquals(initialCount, restoredCount, "Undo should restore deleted child node");
  }
}
