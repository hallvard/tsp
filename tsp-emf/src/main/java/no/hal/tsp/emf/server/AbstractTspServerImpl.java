package no.hal.tsp.emf.server;

import java.util.Map;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import no.hal.tsp.model.Label;
import no.hal.tsp.model.TreeNode;
import no.hal.tsp.protocol.TreeServerProtocol;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;

/**
 * Implementation of the Tree Structure Protocol server.
 */
public abstract class AbstractTspServerImpl extends EmfDocumentServer implements TreeServerProtocol {

  private final Map<String, String> settings = new ConcurrentHashMap<>();

  protected void updateSettings(Map<String, String> newSettings) {
    settings.clear();
    if (newSettings != null) {
      settings.putAll(newSettings);
    }
  }

  @Override
  public CompletableFuture<Void> configure(ConfigureParams params) {
    updateSettings(params.settings());
    return CompletableFuture.completedFuture(null);
  }

  protected String setting(String key, String defaultValue) {
    return settings.getOrDefault(key, defaultValue);
  }

  @Override
  public CompletableFuture<TreeNode[]> getChildren(GetChildrenParams params) {
    Resource resource = getResource(params.documentUri());
    if (resource == null) {
      throw new IllegalArgumentException("Resource not found: " + params.documentUri());
    }
    Object o = params.treeNodeId() == null || params.treeNodeId().isEmpty()
        ? resource
        : objectForId(params.treeNodeId(), resource);
    if (o == null) {
      throw new IllegalArgumentException("EObject not found: " + params.treeNodeId());
    }
    return CompletableFuture.completedFuture(getChildrenN(o, params.depth()));
  }

  protected Label labelFor(Object o) {
    if (o instanceof EObject eObject) {
      return Label.ofText(eObject.eClass().getName());
    }
    return o != null ? Label.ofText(String.valueOf(o)) : null;
  }

  protected String semanticTypeForType(EClassifier eClassifier) {
    return eClassifier.getEPackage().getName() + ":" + eClassifier.getName();
  }

  protected String semanticTypeFor(Object o) {
    if (o instanceof EObject eObject) {
      return semanticTypeForType(eObject.eClass());
    } else if (o != null) {
      return o.getClass().getName();
    }
    return null;
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
}