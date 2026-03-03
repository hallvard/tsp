import { submit } from "./main";
import { TreeCommand, TreeCommandMenu, TreeNode, TreeProtocol } from "./tree-protocol";

export class TreeView {

  private tree : HTMLElement;
  private selectionHandler: ((treeNodeId: string, isSelected: boolean) => void) | undefined;
  private contextMenu: HTMLElement | null = null;

  private observer = new MutationObserver((mutations) => {
    mutations.forEach((mutation) => {
      if (mutation.target instanceof HTMLElement && mutation.target.tagName.toLowerCase() === 'vscode-tree-item') {
        const treeItem = mutation.target as HTMLElement;
        const treeNodeId = treeItem.getAttribute('treeNodeId');
        if (treeNodeId) {
          if ('open' === mutation.attributeName) {
            this.handleTreeItemExpansion(treeItem, treeItem.hasAttribute('open'));
          } else if ('selected' === mutation.attributeName) {
            this.selectionHandler?.(treeNodeId, treeItem.hasAttribute('selected'));
          }
        }
      }
    });
  });

  private handleTreeItemExpansion(treeItem: HTMLElement, isOpen: boolean): void {
    const nodeId = treeItem.getAttribute('treeNodeId');
    if (isOpen) {
      submit<TreeNode[]>(TreeProtocol.getChildren({ treeNodeId: nodeId!, depth: 0 }))
          .then(children => this.setTreeNodeItems(children, treeItem));
    }
  }

  constructor(tree: HTMLElement) {
    this.tree = tree;
    this.observer.observe(tree, {
      attributes: true,
      attributeFilter: ['open', 'selected'],
      subtree: true
    });
    this.tree.addEventListener('contextmenu', (event) => {
      this.handleTreeContextMenu(event);
    });
  }

  public setSelectionHandler(handler: (treeNodeId: string, isSelected: boolean) => void): void {
    this.selectionHandler = handler;
  }

  public handleDocumentEdited(affectedObjectIds?: string[]): void {
    this.refreshEditedTreeNodes(affectedObjectIds);
  }

  private async refreshEditedTreeNodes(affectedObjectIds?: string[]): Promise<void> {
    if (!affectedObjectIds || affectedObjectIds.length === 0) {
      await this.reloadRootNodes();
      return;
    }

    const refreshNodeIds = new Set<string>();
    for (const affectedObjectId of affectedObjectIds) {
      refreshNodeIds.add(affectedObjectId);
      const parentObjectId = this.parentTreeNodeId(affectedObjectId);
      if (parentObjectId) {
        refreshNodeIds.add(parentObjectId);
      }
    }

    let refreshedAny = false;
    for (const treeNodeId of refreshNodeIds) {
      const treeItem = this.tree.querySelector(`vscode-tree-item[treeNodeId="${treeNodeId}"]`);
      if (treeItem instanceof HTMLElement) {
        const children = await submit<TreeNode[]>(TreeProtocol.getChildren({ treeNodeId, depth: 0 }));
        this.setTreeNodeItems(children, treeItem);
        refreshedAny = true;
      }
    }

    if (!refreshedAny) {
      await this.reloadRootNodes();
    }
  }

  private parentTreeNodeId(treeNodeId: string): string | null {
    const separatorIndex = treeNodeId.lastIndexOf('/');
    if (separatorIndex <= 0) {
      return null;
    }
    return treeNodeId.substring(0, separatorIndex);
  }

  private async reloadRootNodes(): Promise<void> {
    const rootNodes = await submit<TreeNode[]>(TreeProtocol.getChildren({ treeNodeId: null, depth: 0 }));
    this.setTreeNodeItems(rootNodes);
  }

  private async handleTreeContextMenu(event: MouseEvent): Promise<void> {
    const target = event.target as HTMLElement | null;
    const treeItem = target?.closest('vscode-tree-item') as HTMLElement | null;
    const treeNodeId = treeItem?.getAttribute('treeNodeId');
    if (!treeItem || !treeNodeId) {
      return;
    }
    event.preventDefault();
    this.removeContextMenu();

    const commandMenu = await submit<TreeCommandMenu>(TreeProtocol.getCommandMenu({ treeNodeId }));
    if (!commandMenu.items || commandMenu.items.length === 0) {
      return;
    }
    this.showContextMenu(event.clientX, event.clientY, commandMenu.items, treeNodeId);
  }

  private showContextMenu(x: number, y: number, menuItems: Array<TreeCommandMenu | TreeCommand>, treeNodeId: string): void {
    const menuElement = document.createElement('vscode-context-menu') as HTMLElement & {
      data?: Array<{ label: string; value: string }>;
      show?: boolean;
    };
    const selectableItems = menuItems.map((item, index) => {
      if (this.isTreeCommand(item)) {
        return { label: item.label.text, value: `command:${item.id}` };
      }
      return { label: `${item.label.text}`, value: `submenu:${index}` };
    });
    menuElement.data = selectableItems;
    menuElement.style.position = 'fixed';
    menuElement.style.left = `${x}px`;
    menuElement.style.top = `${y}px`;
    menuElement.style.zIndex = '1000';

    const onSelect = async (event: Event) => {
      const customEvent = event as CustomEvent<{ value?: string }>;
      const selectedValue = customEvent.detail?.value;
      if (!selectedValue) {
        this.removeContextMenu();
        return;
      }
      if (selectedValue.startsWith('command:')) {
        const commandId = selectedValue.substring('command:'.length);
        await submit(TreeProtocol.doCommand({ treeNodeId, commandId }));
        this.removeContextMenu();
        return;
      }
      if (selectedValue.startsWith('submenu:')) {
        const submenuIndex = Number(selectedValue.substring('submenu:'.length));
        const selectedItem = menuItems[submenuIndex];
        if (selectedItem && this.isTreeCommandMenu(selectedItem)) {
          this.showContextMenu(x + 180, y + submenuIndex * 24, selectedItem.items ?? [], treeNodeId);
        }
        return;
      }
      this.removeContextMenu();
    };
    menuElement.addEventListener('vsc-menu-select', onSelect as EventListener);
    menuElement.addEventListener('vsc-context-menu-select', onSelect as EventListener);

    document.body.appendChild(menuElement);
    menuElement.show = true;
    this.contextMenu = menuElement;
  }

  private isTreeCommand(item: TreeCommandMenu | TreeCommand): item is TreeCommand {
    return (item as TreeCommand).id !== undefined;
  }

  private isTreeCommandMenu(item: TreeCommandMenu | TreeCommand): item is TreeCommandMenu {
    return (item as TreeCommandMenu).items !== undefined;
  }

  private removeContextMenu(): void {
    if (!this.contextMenu) {
      return;
    }
    this.contextMenu.remove();
    this.contextMenu = null;
  }

  public updateTreeItem(treeNode: TreeNode) {
    this.tree.querySelector(`vscode-tree-item[treeNodeId="${treeNode.id}"]`)
        ?.replaceWith(this.toTreeItem(treeNode));
  }

  public toTreeItem(treeNode: TreeNode): HTMLElement {
    const treeItem = document.createElement('vscode-tree-item');
    treeItem.setAttribute('treeNodeId', treeNode.id);
    treeItem.setAttribute('branch', String(treeNode.children !== undefined));
    this.addTreeItemLabel(treeItem, treeNode.label.text, treeNode.label.imageUri);
    this.addTreeNodeItems(treeNode.children, treeItem);
    return treeItem;
  }

  private addTreeItemLabel(container: Element, label: string, imageUri?: string): void {
    if (imageUri) {
      const image = document.createElement('img');
      image.src = imageUri;
      image.className = 'tree-item-icon';
      image.alt = '';
      container.appendChild(image);
    }
    const labelNode = document.createTextNode(label);
    container.appendChild(labelNode);
  }

  private getLabelNodes(container: Element): Element[] {
    const labels: Element[] = [];
    // loop until the first vscode-tree-item node
    for (let i = 0; i < container.childNodes.length; i++) {
      const child = container.childNodes[i];
      if (child.nodeType === Node.ELEMENT_NODE) {
        const childElement = child as Element;
        if (childElement.tagName.toLowerCase() === 'vscode-tree-item') {
          break;
        } else {
          labels.push(childElement);
        }
      } else if (child.nodeType === Node.TEXT_NODE) {
        labels.push(child as Element);
      }
    }
    return labels;
  }

  private getTreeItemNodes(container: Element): Element[] {
    const treeItems: Element[] = [];
    // collect all vscode-tree-item nodes
    for (let i = 0; i < container.childNodes.length; i++) {
      const child = container.childNodes[i];
      if (child.nodeType === Node.ELEMENT_NODE) {
        const childElement = child as Element;
        if (childElement.tagName.toLowerCase() === 'vscode-tree-item') {
          treeItems.push(childElement);
        }
      }
    }
    return treeItems;
  }

  private identity = (child : Element) => child;

  private addChildNodes(container: Element, children: Element[] | undefined): void {
    this.addChildren<Element>(container, children, (child : Element) => child);
  }

  private addChildren<T>(container: Element, children: T[] | undefined, mapper: (child: T) => Element): void {
    children?.forEach(child => container.appendChild(mapper(child)));
  }

  public setTreeItemLabel(label?: string, container: Element = this.tree): void {
    const treeItemNodes = this.getTreeItemNodes(container);
    container.innerHTML = '';
    this.addTreeItemLabel(container, label ?? '');
    this.addChildNodes(container, treeItemNodes);
  }

  public setTreeNodeItems(treeNodes?: TreeNode[], container: Element = this.tree): void {
    const labelNodes = this.getLabelNodes(container);
    container.innerHTML = '';
    this.addChildNodes(container, labelNodes);
    this.addTreeNodeItems(treeNodes, container);
  }

  public addTreeNodeItems(treeNodes?: TreeNode[], container: Element = this.tree): void {
    this.addChildren<TreeNode>(container, treeNodes, (treeNode) => this.toTreeItem(treeNode));
  }
}