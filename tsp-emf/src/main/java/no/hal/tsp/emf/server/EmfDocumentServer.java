package no.hal.tsp.emf.server;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import no.hal.tsp.launcher.ServerProtocolLauncher;
import no.hal.tsp.protocol.DocumentServerProtocol;
import org.eclipse.emf.common.command.BasicCommandStack;
import org.eclipse.emf.common.command.CommandStack;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;

/**
 * Implementation of the Document Server Protocol using EMF resources.
 */
public class EmfDocumentServer implements DocumentServerProtocol {

  private Map<String, Resource> openResources = new HashMap<>();
  private Map<Resource, CommandStack> commandStacks = new HashMap<>();

  protected Resource getResource(String documentUri) {
    return openResources.get(documentUri);
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
    commandStacks.put(resource, commandStack);
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public void documentEdited(DocumentEditedParams params) {
  }

  @Override
  public CompletableFuture<Void> undoEdits(UndoEditsParams params) {
    var commandStack = commandStacks.get(getResource(params.documentUri()));
    commandStack.undo();
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public CompletableFuture<Void> redoEdits(RedoEditsParams params) {
    var commandStack = commandStacks.get(getResource(params.documentUri()));
    commandStack.redo();
    return CompletableFuture.completedFuture(null);
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
    openResources.remove(params.documentUri());
    return CompletableFuture.completedFuture(null);
  }
  
  //

  public static void main(String[] args) {
    new ServerProtocolLauncher<DocumentServerProtocol>(DocumentServerProtocol.class, new EmfDocumentServer())
        .startServer();
  }
}
