package no.hal.tsp.emf.server;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import no.hal.tsp.launcher.ServerProtocolLauncher;
import no.hal.tsp.model.TreeNode;
import no.hal.tsp.protocol.TreeServerProtocol;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;

/**
 * Implementation of the Tree Structure Protocol server.
 */
public class AbstractTspServerImpl extends EmfDocumentServer implements TreeServerProtocol {

  @Override
  public CompletableFuture<TreeNode[]> getChildren(GetChildrenParams params) {
    Resource resource = getResource(params.documentUri());
    if (resource == null) {
      throw new IllegalArgumentException("Resource not found: " + params.documentUri());
    }
    Object o = params.treeNodeId() == null || params.treeNodeId().isEmpty()
        ? resource
        : resource.getEObject(params.treeNodeId());
    if (o == null) {
      throw new IllegalArgumentException("EObject not found: " + params.treeNodeId());
    }
    return CompletableFuture.completedFuture(getChildrenN(o, params.depth()));
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
    return resource.getEObject(id);
  }

  protected String labelFor(Object o) {
    if (o instanceof EObject eObject) {
      return eObject.eClass().getName();
    }
    return String.valueOf(o);
  }

  protected String semanticTypeFor(Object o) {
    if (o instanceof EObject eObject) {
      return eObject.eClass().getEPackage().getName() + ":" + eObject.eClass().getName();
    }
    return o.getClass().getSimpleName();
  }

  protected List<?> childrenFor(Object o) {
    if (o instanceof Resource resource) {
      return resource.getContents();
    } else if (o instanceof EObject eObject) {
      return eObject.eContents();
    }
    return List.of();
  }

  protected TreeNode treeNodeFor(Object o, TreeNode[] children) {
    return new TreeNode(objectId(o), "object",
        semanticTypeFor(o),
        labelFor(o),
        children
    );
  }

  protected TreeNode[] getChildren1(Object o) {
    var children = childrenFor(o);
    TreeNode[] childNodes = new TreeNode[children.size()];
    for (int i = 0; i < children.size(); i++) {
      childNodes[i] = treeNodeFor(children.get(i), new TreeNode[0]);
    }
    return childNodes;
  }

  private TreeNode[] getChildrenN(Object o, int depth) {
    var children = getChildren1(o);
    if (depth > 0) {
      for (int i = 0; i < children.length; i++) {
        var child = children[i];
        var childEObject = objectForId(child.id(), o);
        var childChildren = getChildrenN(childEObject, depth - 1);
        // replace node with one with children
        children[i] = new TreeNode(child.id(), child.type(), child.semanticType(), child.label(), childChildren);
      }
    }
    return children;
  }

  public static void main(String[] args) {
    new ServerProtocolLauncher<TreeServerProtocol>(TreeServerProtocol.class, new AbstractTspServerImpl())
        .startServer();
  }
}
