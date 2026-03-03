package no.hal.tsp.emf.server;

import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import no.hal.tsp.launcher.ServerProtocolLauncher;
import no.hal.tsp.model.Form;
import no.hal.tsp.model.Form.Validation;
import no.hal.tsp.model.Label;
import no.hal.tsp.model.MenuItem.Command;
import no.hal.tsp.model.MenuItem.Menu;
import no.hal.tsp.model.MenuItem;
import no.hal.tsp.model.Property;
import no.hal.tsp.protocol.TreeServerProtocol;
import org.eclipse.emf.common.command.CompoundCommand;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EDataType;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.provider.EcoreItemProviderAdapterFactory;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.edit.command.AddCommand;
import org.eclipse.emf.edit.command.CommandParameter;
import org.eclipse.emf.edit.command.DeleteCommand;
import org.eclipse.emf.edit.command.SetCommand;
import org.eclipse.emf.edit.domain.AdapterFactoryEditingDomain;
import org.eclipse.emf.edit.domain.EditingDomain;
import org.eclipse.emf.edit.provider.ComposedAdapterFactory;
import org.eclipse.emf.edit.provider.IItemLabelProvider;
import org.eclipse.emf.edit.provider.IItemPropertyDescriptor;
import org.eclipse.emf.edit.provider.IItemPropertySource;
import org.eclipse.emf.edit.provider.ReflectiveItemProviderAdapterFactory;
import org.eclipse.emf.edit.tree.provider.TreeItemProviderAdapterFactory;

/**
 * Implementation of the Tree Structure Protocol server.
 */
public class EmfEditTspServer extends AbstractTspServerImpl {

  private ComposedAdapterFactory adapterFactory = new ComposedAdapterFactory(
    ComposedAdapterFactory.Descriptor.Registry.INSTANCE
  );

  private ImageSupport imageSupport = new ImageSupport(new ImageSupport.Options(null, null));

  private final Map<Resource, EditingDomain> editingDomains = new HashMap<>();

  public EmfEditTspServer() {
    super();
    registerProviders(adapterFactory);
  }
  
  protected void registerProviders(ComposedAdapterFactory adapterFactory) {
    adapterFactory.addAdapterFactory(new TreeItemProviderAdapterFactory());
    adapterFactory.addAdapterFactory(new ReflectiveItemProviderAdapterFactory());
    adapterFactory.addAdapterFactory(new EcoreItemProviderAdapterFactory());
  }

  protected <T> T adapt(Object o, Class<T> type) {
    return type.cast(adapterFactory.adapt(o, type));
  }

  protected EditingDomain getEditingDomain(Resource resource) {
    return editingDomains.get(resource);
  }

  @Override
  public CompletableFuture<Void> openDocument(OpenDocumentParams params) {
    var result = super.openDocument(params);
    var resource = getResource(params.documentUri());
    var commandStack = getCommandStack(resource);
    editingDomains.put(resource,
        new AdapterFactoryEditingDomain(adapterFactory, commandStack, resource.getResourceSet()));
    return result;
  }

  @Override
  public CompletableFuture<Void> closeDocument(CloseDocumentParams params) {
    var resource = getResource(params.documentUri());
    if (resource != null) {
      editingDomains.remove(resource);
    }
    return super.closeDocument(params);
  }

  @Override
  protected void updateSettings(Map<String, String> newSettings) {
    super.updateSettings(newSettings);
    imageSupport.setImagesOptions(new ImageSupport.Options(
        Path.of(setting("tsp.label.images.dir", "tsp-images")),
        setting("tsp.label.images.uri", null)
    ));
  }

  @Override
  protected Label labelFor(Object o) {
    var labelProvider = adapt(o, IItemLabelProvider.class);
    if (labelProvider == null) {
      return super.labelFor(o);
    }
    var label = labelProvider.getText(o);
    var imageUri = imageSupport.imageUriFor(labelProvider.getImage(o), o);
    return label != null
        ? Label.ofText(label).withImage(imageUri)
        : super.labelFor(o);
  }

  @Override
  public CompletableFuture<Menu> getCommandMenu(GetCommandMenuParams params) {
    var resource = getResource(params.documentUri());
    if (resource == null) {
      return CompletableFuture.completedFuture(null);
    }
    if (!(objectForId(params.treeNodeId(), resource) instanceof EObject parentEObject)) {
      return CompletableFuture.completedFuture(null);
    }
    var createCommands = collectCreateCommands(parentEObject, resource);
    var menuItems = new ArrayList<MenuItem>();

    var deleteCommand = deleteCommandFor(parentEObject, resource);
    if (deleteCommand != null && deleteCommand.canExecute()) {
      menuItems.add(new Command(encodeDeleteCommandId(parentEObject), Label.ofText("Delete")));
    }

    MenuItem[] newMenuItems = createCommands.stream()
        .map(c -> new Command(c.id(), Label.ofText(c.label())))
        .toArray(MenuItem[]::new);
    if (newMenuItems.length > 0) {
      menuItems.add(new Menu(Label.ofText("New..."), newMenuItems));
    }

    return CompletableFuture.completedFuture(new Menu(Label.ofText("Commands"),
        menuItems.toArray(MenuItem[]::new)));
  }

  @Override
  public CompletableFuture<TreeEditedParams> doCommand(DoCommandParams params) {
    var resource = getResource(params.documentUri());
    if (resource == null) {
      throw new IllegalArgumentException("Resource not found: " + params.documentUri());
    }
    var parent = objectForId(params.treeNodeId(), resource);
    if (!(parent instanceof EObject parentEObject)) {
      throw new IllegalArgumentException("EObject not found: " + params.treeNodeId());
    }
    var command = commandForId(parentEObject, resource, params.commandId());
    if (command == null || !command.canExecute()) {
      throw new IllegalArgumentException("Unknown command: " + params.commandId());
    }
    doCommand(command, resource);
    var edited = new TreeEditedParams(params.documentUri(), new String[]{params.treeNodeId()});
    return CompletableFuture.completedFuture(edited);
  }

  private org.eclipse.emf.common.command.Command commandForId(EObject parent, Resource resource, String commandId) {
    var decodedCommandId = decodeCreateCommandId(commandId);
    var kind = decodedCommandId.kind();
    return switch (kind) {
      case "new" -> createCommandForId(parent, resource, decodedCommandId);
      case "delete" -> deleteCommandFor(parent, resource);
      default -> null;
    };
  }

  private org.eclipse.emf.common.command.Command deleteCommandFor(EObject object, Resource resource) {
    var editingDomain = getEditingDomain(resource);
    if (editingDomain == null) {
      editingDomain = AdapterFactoryEditingDomain.getEditingDomainFor(object);
    }
    if (editingDomain == null) {
      return null;
    }
    return DeleteCommand.create(editingDomain, object);
  }

  private org.eclipse.emf.common.command.Command createCommandForId(
      EObject parent,
      Resource resource,
      DecodedCreateCommandId decodedCommandId
  ) {
    var editingDomain = getEditingDomain(resource);
    if (editingDomain == null) {
      editingDomain = AdapterFactoryEditingDomain.getEditingDomainFor(parent);
    }
    if (editingDomain == null) {
      return null;
    }
    var descriptors = editingDomain.getNewChildDescriptors(parent, null);
    for (var descriptor : descriptors) {
      if (descriptor instanceof CommandParameter commandParameter && matchesDecodedCommandId(commandParameter, decodedCommandId)) {
        return createCommandFor(editingDomain, parent, commandParameter);
      }
    }
    return null;
  }

  private boolean matchesDecodedCommandId(CommandParameter commandParameter, DecodedCreateCommandId decodedCommandId) {
    Object feature = commandParameter.getFeature();
    Object value = commandParameter.getValue();
    String featureName = feature instanceof EStructuralFeature structuralFeature
        ? structuralFeature.getName()
        : "";
    String packageNsUri = "";
    String className = "";
    if (value instanceof EObject eObject) {
      packageNsUri = eObject.eClass().getEPackage().getNsURI();
      className = eObject.eClass().getName();
    }
    return Objects.equals(decodedCommandId.featureName(), featureName)
        && Objects.equals(decodedCommandId.packageNsUri(), packageNsUri)
        && Objects.equals(decodedCommandId.className(), className);
  }

  private List<CreateCommandEntry> collectCreateCommands(EObject parent, Resource resource) {
    var editingDomain = getEditingDomain(resource);
    if (editingDomain == null) {
      editingDomain = AdapterFactoryEditingDomain.getEditingDomainFor(parent);
    }
    if (editingDomain == null) {
      return List.of();
    }
    var descriptors = editingDomain.getNewChildDescriptors(parent, null);
    List<CreateCommandEntry> createCommands = new ArrayList<>();
    for (var descriptor : descriptors) {
      if (!(descriptor instanceof CommandParameter commandParameter)) {
        continue;
      }
      var createCommand = createCommandFor(editingDomain, parent, commandParameter);
      if (createCommand == null || !createCommand.canExecute()) {
        continue;
      }
      var commandId = encodeCreateCommandId(commandParameter);
      createCommands.add(new CreateCommandEntry(commandId, createLabelFor(commandParameter), createCommand));
    }
    return createCommands;
  }

  private String encodeCreateCommandId(CommandParameter commandParameter) {
    Object feature = commandParameter.getFeature();
    Object value = commandParameter.getValue();
    String featureName = feature instanceof EStructuralFeature structuralFeature
        ? structuralFeature.getName()
        : "";
    String packageNsUri = "";
    String className = "";
    if (value instanceof EObject eObject) {
      packageNsUri = eObject.eClass().getEPackage().getNsURI();
      className = eObject.eClass().getName();
    }
    return String.join("|",
        "new",
        encodeCommandPart(featureName),
        encodeCommandPart(packageNsUri),
        encodeCommandPart(className));
  }

  private String encodeDeleteCommandId(EObject object) {
    String packageNsUri = object.eClass().getEPackage().getNsURI();
    String className = object.eClass().getName();
    return String.join("|",
        "delete",
        encodeCommandPart(packageNsUri),
        encodeCommandPart(className));
  }

  private DecodedCreateCommandId decodeCreateCommandId(String commandId) {
    String[] parts = commandId.split("\\|", -1);
    String kind = parts.length > 0 ? decodeCommandPart(parts[0]) : "";
    String featureName = parts.length > 1 ? decodeCommandPart(parts[1]) : "";
    String packageNsUri = parts.length > 2 ? decodeCommandPart(parts[2]) : "";
    String className = parts.length > 3 ? decodeCommandPart(parts[3]) : "";
    return new DecodedCreateCommandId(kind, featureName, packageNsUri, className);
  }

  private String encodeCommandPart(String value) {
    return URLEncoder.encode(value != null ? value : "", StandardCharsets.UTF_8);
  }

  private String decodeCommandPart(String value) {
    return URLDecoder.decode(value != null ? value : "", StandardCharsets.UTF_8);
  }

  private record DecodedCreateCommandId(String kind, String featureName, String packageNsUri, String className) {
  }

  private org.eclipse.emf.common.command.Command createCommandFor(
      EditingDomain editingDomain,
      EObject parent,
      CommandParameter commandParameter
  ) {
    Object feature = commandParameter.getFeature();
    Object value = commandParameter.getValue();
    if (feature instanceof EStructuralFeature structuralFeature) {
      if (structuralFeature.isMany()) {
        return AddCommand.create(editingDomain, parent, structuralFeature, value);
      }
      return SetCommand.create(editingDomain, parent, structuralFeature, value);
    }
    return null;
  }

  private String createLabelFor(CommandParameter commandParameter) {
    Object value = commandParameter.getValue();
    String classLabel;
    if (value instanceof EObject eObject) {
      classLabel = eObject.eClass().getName();
    } else {
      var labelProvider = value != null ? adapt(value, IItemLabelProvider.class) : null;
      classLabel = labelProvider != null ? labelProvider.getText(value) : null;
      if (classLabel == null || classLabel.isBlank()) {
        classLabel = value != null ? semanticTypeFor(value) : "Object";
      }
    }
    Object feature = commandParameter.getFeature();
    String referenceLabel = feature instanceof EStructuralFeature structuralFeature
        ? structuralFeature.getName()
        : null;
    return referenceLabel != null && !referenceLabel.isBlank()
        ? classLabel + " in " + referenceLabel
        : classLabel;
  }

  private record CreateCommandEntry(String id, String label, org.eclipse.emf.common.command.Command command) {
  }

  public static void main(String[] args) {
    ServerProtocolLauncher.main(new String[]{
      TreeServerProtocol.class.getName(),
      EmfEditTspServer.class.getName()
    });
  }

  @Override
  public CompletableFuture<Form> getTreeNodeForm(GetTreeNodeFormParams params) {
    var o = objectForId(params.treeNodeId(), getResource(params.documentUri()));
    IItemPropertySource propertySource = adapt(o, IItemPropertySource.class);

    if (propertySource == null) {
      return CompletableFuture.completedFuture(new Form(List.of()));
    }

    List<Form.Item> formItems = new ArrayList<>();      
    for (var property : collectProperties(o, propertySource)) {
      var descriptor = property.descriptor();
      String displayName = descriptor.getDisplayName(o);
      String description = descriptor.getDescription(o);
      Object value = descriptor.getPropertyValue(o);
      IItemLabelProvider itemLabelProvider = descriptor.getLabelProvider(o);
      String stringValue = itemLabelProvider != null
          ? itemLabelProvider.getText(value)
          : (value != null ? String.valueOf(value) : "");
      List<Property.Value> valueOptions = new ArrayList<>();
      var choices = descriptor.getChoiceOfValues(o);
      if (choices != null) {
        for (var val : choices) {
          String stringVal = itemLabelProvider != null
              ? itemLabelProvider.getText(val)
              : (val != null ? String.valueOf(val) : "");
          valueOptions.add(new Property.Value(property.semanticType(), stringVal));
        }
      }
      formItems.add(new Form.Item(
          new Property(descriptor.getId(o)).withValue(property.semanticType(), stringValue),
          Label.ofText(displayName).withDescription(description),
          valueOptions.isEmpty() ? null : valueOptions,
          descriptor.canSetProperty(o)
      ));
    }
    return CompletableFuture.completedFuture(new Form(formItems));
  }

  @Override
  public CompletableFuture<List<Validation>> validateTreeNodeForm(ValidateTreeNodeFormParams params) {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'validateTreeNodeForm'");
  }

  @Override
  public CompletableFuture<List<Validation>> commitTreeNodeForm(CommitTreeNodeFormParams params) {
    var resource = getResource(params.documentUri());
    var o = objectForId(params.treeNodeId(), resource);
    IItemPropertySource propertySource = adapt(o, IItemPropertySource.class);
    if (propertySource == null) {
      return CompletableFuture.completedFuture(List.of());
    }
    var properties = collectProperties(o, propertySource);
    var command = new CompoundCommand("Update form properties");
    for (var formProperty : params.formProperties()) {
      var descriptor = properties.stream()
          .filter(p -> p.descriptor().getId(o).equals(formProperty.name()))
          .map(TypedProperty::descriptor)
          .findFirst()
          .orElse(null);
      if (descriptor == null || !descriptor.canSetProperty(o)) {
        continue;
      }
      Object updatedValue = convertValue(formProperty.value(), descriptor, o);
      Object feature = descriptor.getFeature(o);
      if (!(o instanceof org.eclipse.emf.ecore.EObject eObject) || !(feature instanceof EStructuralFeature sf)) {
        continue;
      }
      Object currentValue = descriptor.getPropertyValue(o);
      if (! Objects.equals(currentValue, updatedValue)) {
        var editingDomain = getEditingDomain(resource);
        if (editingDomain == null) {
          editingDomain = AdapterFactoryEditingDomain.getEditingDomainFor(eObject);
        }
        var setCommand = SetCommand.create(editingDomain, eObject, sf, updatedValue);
        if (setCommand.canExecute()) {
          command.append(setCommand);
        }
      }
    }

    if (!command.isEmpty() && command.canExecute()) {
      doCommand(command, resource);
    }

    return CompletableFuture.completedFuture(List.of());
  }

  private Object convertValue(Property.Value value, IItemPropertyDescriptor descriptor, Object o) {
    if (value == null) {
      return null;
    }

    String stringValue = value.stringValue();
    var choices = descriptor.getChoiceOfValues(o);
    if (choices != null) {
      IItemLabelProvider itemLabelProvider = descriptor.getLabelProvider(o);
      if (itemLabelProvider != null) {
        for (var choice : choices) {
          String choiceLabel = itemLabelProvider.getText(choice);
          if (stringValue != null && stringValue.equals(choiceLabel)) {
            return choice;
          }
        }
      }
    }

    Object feature = descriptor.getFeature(o);
    if (feature instanceof EStructuralFeature sf && sf.getEType() instanceof EDataType dataType) {
      try {
        return EcoreUtil.createFromString(dataType, stringValue);
      } catch (Exception e) {
        return stringValue;
      }
    }
    return stringValue;
  }

  private record TypedProperty(IItemPropertyDescriptor descriptor, String semanticType) {
  }

  private List<TypedProperty> collectProperties(Object o, IItemPropertySource propertySource) {
    List<TypedProperty> properties = new ArrayList<>();
    for (var descriptor : propertySource.getPropertyDescriptors(o)) {
      Object feature = descriptor.getFeature(o);
      String semanticType = null;
      if (feature instanceof EStructuralFeature sf) {
        if (sf.isTransient() || sf.isMany()) {
          continue;
        }
        semanticType = semanticTypeForType(sf.getEType());
      } else {
        Object value = descriptor.getPropertyValue(o);
        semanticType = semanticTypeFor(value);
      }
      if (semanticType == null) {
        continue;
      }
      properties.add(new TypedProperty(descriptor, semanticType));
    }
    return properties;
  }
}
