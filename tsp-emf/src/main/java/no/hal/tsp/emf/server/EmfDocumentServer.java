package no.hal.tsp.emf.server;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import no.hal.tsp.launcher.ServerProtocolLauncher;
import no.hal.tsp.protocol.DocumentClientProtocol;
import no.hal.tsp.protocol.DocumentServerProtocol;
import org.eclipse.emf.common.command.BasicCommandStack;
import org.eclipse.emf.common.command.Command;
import org.eclipse.emf.common.command.CommandStack;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;

/**
 * Implementation of the Document Server Protocol using EMF resources.
 */
public class EmfDocumentServer implements DocumentServerProtocol, DocumentClientProtocol.Consumer {

  private Map<String, Resource> openResources = new HashMap<>();
  private Map<Resource, CommandStack> commandStacks = new HashMap<>();
  private final ThreadLocal<EditKind> currentEditKind = ThreadLocal.withInitial(() -> EditKind.NORMAL);

  // for notifications of edits
  private DocumentClientProtocol documentClient;

  protected Resource getResource(String documentUri) {
    return openResources.get(documentUri);
  }

  protected CommandStack getCommandStack(Resource resource) {
    return commandStacks.get(resource);
  }

  @Override
  public void setDocumentClient(DocumentClientProtocol documentClient) {
    this.documentClient = documentClient;
  }

  @Override
  public CompletableFuture<Void> openDocument(OpenDocumentParams params) {
    URI uri = URI.createURI(params.documentUri());
    ResourceSet resourceSet = new ResourceSetImpl();
    resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap()
        .put("*", new org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl());
    System.err.println("Loading resource: " + uri);
    Resource resource = resourceSet.getResource(uri, true);
    openResources.put(params.documentUri(), resource);
    var commandStack = new BasicCommandStack();
    commandStack.addCommandStackListener(event -> {
      System.err.println("Command stack changed: " + event);
      notifyDocumentEdited(params.documentUri(), currentEditKind.get(),
        affectedObjectIds(commandStack.getMostRecentCommand()));
    });
    commandStacks.put(resource, commandStack);
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public CompletableFuture<DocumentEditedParams> undoEdits(UndoEditsParams params) {
    var resource = getResource(params.documentUri());
    var commandStack = commandStacks.get(resource);
    var affectedObjectIds = new LinkedHashSet<String>();
    int count = Math.max(0, params.count());
    while (count > 0 && commandStack.canUndo()) {
      withEditKind(EditKind.UNDO, () -> {
        commandStack.undo();
        return null;
      });
      if (commandStack instanceof BasicCommandStack basicCommandStack) {
        for (var affectedObjectId : affectedObjectIds(basicCommandStack.getMostRecentCommand())) {
          affectedObjectIds.add(affectedObjectId);
        }
      }
      count--;
    }
    return CompletableFuture.completedFuture(new DocumentEditedParams(params.documentUri(),
        EditKind.UNDO.name(), affectedObjectIds));
  }

  @Override
  public CompletableFuture<DocumentEditedParams> redoEdits(RedoEditsParams params) {
    var resource = getResource(params.documentUri());
    var commandStack = commandStacks.get(resource);
    var affectedObjectIds = new LinkedHashSet<String>();
    int count = Math.max(0, params.count());
    while (count > 0 && commandStack.canRedo()) {
      withEditKind(EditKind.REDO, () -> {
        commandStack.redo();
        return null;
      });
      if (commandStack instanceof BasicCommandStack basicCommandStack) {
        for (var affectedObjectId : affectedObjectIds(basicCommandStack.getMostRecentCommand())) {
          affectedObjectIds.add(affectedObjectId);
        }
      }
      count--;
    }
    return CompletableFuture.completedFuture(new DocumentEditedParams(params.documentUri(),
        EditKind.REDO.name(), affectedObjectIds));
  }

  protected DocumentEditedParams doCommand(Command command, Resource resource) {
    var commandStack = commandStacks.get(resource);
    withEditKind(EditKind.NORMAL, () -> {
      System.err.println("Executing " + command.getLabel() + ": " + command.getDescription());
      commandStack.execute(command);
      return null;
    });
    return new DocumentEditedParams(resource.getURI().toString(), EditKind.NORMAL.name(),
        affectedObjectIds(command));
  }

  protected void notifyDocumentEdited(String documentUri, EditKind kind, Collection<String> affectedObjectIds) {
    if (documentClient != null) {
      System.err.println(kind + " command in " + documentUri + " affected " + String.join(", ", affectedObjectIds));
      documentClient.documentEdited(new DocumentEditedParams(documentUri, kind.name(), affectedObjectIds));
    }
  }

  protected String objectId(Object o) {
    if (o instanceof EObject eObject) {
      return eObject.eResource().getURIFragment(eObject);
    }
    return String.valueOf(o.hashCode());
  }

  protected EObject objectForId(String id, Object context) {
    Resource resource = null;
    if (context instanceof Resource r) {
      resource = r;
    } else if (context instanceof EObject eObject) {
      resource = eObject.eResource();
    }
    var parts = id.split(",");
    return resource.getEObject(parts[0]);
  }

  protected Collection<String> affectedObjectIds(Command command) {
    var affectedObjectIds = new LinkedHashSet<String>();
    for (var affectedObject : command.getAffectedObjects()) {
      affectedObjectIds.add(objectId(affectedObject));
    }
    return affectedObjectIds;
  }

  private <T> T withEditKind(EditKind kind, Supplier<T> action) {
    var previousKind = currentEditKind.get();
    currentEditKind.set(kind);
    try {
      return action.get();
    } finally {
      currentEditKind.set(previousKind);
    }
  }

  @Override
  public CompletableFuture<Void> saveDocument(SaveDocumentParams params) {
    var resource = getResource(params.documentUri());
    // TODO: handle NewUriOptions
    try {
      resource.save(null);
    } catch (Exception e) {
      e.printStackTrace(System.err);
    }
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public CompletableFuture<Void> closeDocument(CloseDocumentParams params) {
    var resource = getResource(params.documentUri());
    resource.getResourceSet().getResources().forEach(res -> res.unload());
    commandStacks.remove(resource);
    openResources.remove(params.documentUri());
    return CompletableFuture.completedFuture(null);
  }
  
  //

  public static void main(String[] args) {
    new ServerProtocolLauncher<DocumentServerProtocol>(DocumentServerProtocol.class, new EmfDocumentServer())
        .startServer(System.in, System.out);
  }
}
