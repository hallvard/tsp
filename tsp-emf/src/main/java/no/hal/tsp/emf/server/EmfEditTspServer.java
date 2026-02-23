package no.hal.tsp.emf.server;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import no.hal.tsp.protocol.TreeServerProtocol;
import no.hal.tsp.launcher.ServerProtocolLauncher;
import no.hal.tsp.model.Form;
import no.hal.tsp.model.Label;
import org.eclipse.emf.edit.provider.IItemLabelProvider;
import org.eclipse.emf.edit.provider.IItemPropertyDescriptor;
import org.eclipse.emf.edit.provider.IItemPropertySource;
import org.eclipse.emf.ecore.provider.EcoreItemProviderAdapterFactory;
import org.eclipse.emf.edit.provider.ComposedAdapterFactory;
import org.eclipse.emf.edit.provider.ReflectiveItemProviderAdapterFactory;
import org.eclipse.emf.edit.tree.provider.TreeItemProviderAdapterFactory;

/**
 * Implementation of the Tree Structure Protocol server.
 */
public class EmfEditTspServer extends AbstractTspServerImpl {

  private ComposedAdapterFactory adapterFactory = new ComposedAdapterFactory(
    ComposedAdapterFactory.Descriptor.Registry.INSTANCE
  );

  public EmfEditTspServer() {
    super();
    registerProviders(adapterFactory);
  }
  
  protected void registerProviders(ComposedAdapterFactory adapterFactory) {
    adapterFactory.addAdapterFactory(new TreeItemProviderAdapterFactory());
    adapterFactory.addAdapterFactory(new ReflectiveItemProviderAdapterFactory());
    adapterFactory.addAdapterFactory(new EcoreItemProviderAdapterFactory());
  }

  @Override
  protected Label labelFor(Object o) {
    IItemLabelProvider labelProvider = (IItemLabelProvider) adapterFactory.adapt(o, IItemLabelProvider.class);
    var label = labelProvider.getText(o);
    return label != null ? Label.ofText(label) : super.labelFor(o);
  }

  @Override
  public CompletableFuture<Form> getForm(GetFormParams params) {
    var resource = getResource(params.documentUri());
    if (resource == null) {
      throw new IllegalArgumentException("Resource not found: " + params.documentUri());
    }
    Object object = params.treeNodeId() == null || params.treeNodeId().isEmpty()
        ? resource
        : resource.getEObject(params.treeNodeId());
    if (object == null) {
      throw new IllegalArgumentException("EObject not found: " + params.treeNodeId());
    }
    IItemPropertySource itemPropertySource = (IItemPropertySource) adapterFactory.adapt(object, IItemPropertySource.class);
    if (itemPropertySource == null) {
      return CompletableFuture.completedFuture(new Form(params.treeNodeId(), new Form.FormItem[0]));
    }

    List<Form.FormItem> items = new ArrayList<>();
    List<IItemPropertyDescriptor> descriptors = itemPropertySource.getPropertyDescriptors(object);
    if (descriptors != null) {
      for (IItemPropertyDescriptor descriptor : descriptors) {
        String id = String.valueOf(descriptor.getId(object));
        String fieldLabel = descriptor.getDisplayName(object);
        Object propertyValue = descriptor.getPropertyValue(object);
        Object unwrappedValue = unwrapPropertyValue(propertyValue);
        String textValue = toTextValue(descriptor, object, unwrappedValue);
        boolean editable = descriptor.canSetProperty(object);
        items.add(new Form.TextField(id, Label.ofText(fieldLabel), textValue, editable));
      }
    }

    return CompletableFuture.completedFuture(new Form(params.treeNodeId(), items.toArray(new Form.FormItem[0])));
  }

  private Object unwrapPropertyValue(Object value) {
    if (value instanceof IItemPropertySource itemPropertySource) {
      return itemPropertySource.getEditableValue(value);
    }
    return value;
  }

  private String toTextValue(IItemPropertyDescriptor descriptor, Object owner, Object value) {
    if (value == null) {
      return "";
    }
    IItemLabelProvider itemLabelProvider = descriptor.getLabelProvider(owner);
    if (itemLabelProvider != null) {
      String text = itemLabelProvider.getText(value);
      if (text != null) {
        return text;
      }
    }
    return String.valueOf(value);
  }

  public static void main(String[] args) {
    ServerProtocolLauncher.main(new String[]{
      TreeServerProtocol.class.getName(),
      EmfEditTspServer.class.getName()
    });
  }
}
