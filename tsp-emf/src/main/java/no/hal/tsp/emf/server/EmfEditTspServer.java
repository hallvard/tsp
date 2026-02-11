package no.hal.tsp.emf.server;

import no.hal.tsp.protocol.TreeServerProtocol;
import no.hal.tsp.launcher.ServerProtocolLauncher;
import org.eclipse.emf.ecore.provider.EcoreItemProviderAdapterFactory;
import org.eclipse.emf.edit.provider.ComposedAdapterFactory;
import org.eclipse.emf.edit.provider.IItemLabelProvider;
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
  protected String labelFor(Object o) {
    IItemLabelProvider labelProvider = (IItemLabelProvider) adapterFactory.adapt(o, IItemLabelProvider.class);
    var label = labelProvider.getText(o);
    return label != null ? label : super.labelFor(o);
  }

  public static void main(String[] args) {
    ServerProtocolLauncher.main(new String[]{
      TreeServerProtocol.class.getName(),
      EmfEditTspServer.class.getName()
    });
  }
}
