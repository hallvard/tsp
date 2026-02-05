package no.hal.tsp.emf.server;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import no.hal.tsp.launcher.TspServerLauncher;
import no.hal.tsp.model.TreeNode;
import no.hal.tsp.protocol.TreeServerProtocol;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;

/**
 * Implementation of the Tree Structure Protocol server.
 */
public class TspServerImpl implements TreeServerProtocol {

  private Map<String, Resource> openResources = new HashMap<>();

  @Override
  public CompletableFuture<TreeNode[]> openResource(OpenResourceParams params) {
    URI uri = URI.createURI(params.documentUri());
    ResourceSet resourceSet = new ResourceSetImpl();
    resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap()
        .put("*", new org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl());
    System.err.println("Loading resource: " + uri);
    Resource resource = resourceSet.getResource(uri, true);
    openResources.put(params.documentUri(), resource);
    System.err.println("Getting root children for resource: " + resource);
    return CompletableFuture.completedFuture(getChildren1(resource));
  }

  @Override
  public CompletableFuture<TreeNode[]> getChildren(GetChildrenParams params) {
    Resource resource = openResources.get(params.documentUri());
    if (resource == null) {
      throw new IllegalArgumentException("Resource not found: " + params.documentUri());
    }
    EObject eObject = resource.getEObject(params.treeNodeId());
    if (eObject == null) {
      throw new IllegalArgumentException("EObject not found: " + params.treeNodeId());
    }
    return CompletableFuture.completedFuture(getChildrenN(eObject, params.depth()));
  }

  private String eObjectId(EObject eObject) {
    return eObject.eResource().getURIFragment(eObject);
  }

  private EObject eObjectForId(String id, EObject context) {
    return context.eResource().getEObject(id);
  }

  private TreeNode treeNodeFor(EObject eObject, TreeNode[] children) {
    String semanticType = eObject.eClass().getEPackage().getName() + ":" + eObject.eClass().getName();
    String label = eObject.eClass().getName();
    return new TreeNode(eObjectId(eObject), "eObject", semanticType, label, children);
  }

  private TreeNode[] getChildren1(Resource resource) {
    TreeNode[] children = new TreeNode[resource.getContents().size()];
    for (int i = 0; i < resource.getContents().size(); i++) {
      children[i] = treeNodeFor(resource.getContents().get(i), new TreeNode[0]);
    }
    return children;
  }

  private TreeNode[] getChildren1(EObject eObject) {
    TreeNode[] children = new TreeNode[eObject.eContents().size()];
    // TODO: iterate over features and generate tree nodes with the feature name as label, and
    // the value(s) as its child node(s)
    for (int i = 0; i < eObject.eContents().size(); i++) {
      children[i] = treeNodeFor(eObject.eContents().get(i), new TreeNode[0]);
    }
    return children;
  }

  private TreeNode[] getChildrenN(EObject eObject, int depth) {
    var children = getChildren1(eObject);
    if (depth > 0) {
      for (int i = 0; i < children.length; i++) {
        var child = children[i];
        var childEObject = eObjectForId(child.id(), eObject);
        var childChildren = getChildrenN(childEObject, depth - 1);
        // replace node with one with children
        children[i] = new TreeNode(child.id(), child.type(), child.semanticType(), child.label(), childChildren);
      }
    }
    return children;
  }

  public static void main(String[] args) {
    new TspServerLauncher(new TspServerImpl()).startServer();
  }
}
