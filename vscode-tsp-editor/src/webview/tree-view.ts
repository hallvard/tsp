import { TreeNode, TreeProtocol } from "./protocol";
import { submit } from "./main";

export class TreeView {

  private tree : HTMLElement;
  private onTreeNodeSelected?: (treeNodeId: string) => void;

  private observer = new MutationObserver((mutations) => {
    mutations.forEach((mutation) => {
      if (mutation.type === 'attributes' && mutation.attributeName === 'open') {
        const treeItem = mutation.target as HTMLElement;
        const isOpen = treeItem.hasAttribute('open');
        const nodeId = treeItem.getAttribute('id');
        
        if (isOpen && nodeId) {
          this.handleTreeItemExpanded(treeItem);
        }
      }
    });
  });

  constructor(tree: HTMLElement, onTreeNodeSelected?: (treeNodeId: string) => void) {
    this.tree = tree;
    this.onTreeNodeSelected = onTreeNodeSelected;
    this.observer.observe(tree, {
      attributes: true,
      attributeFilter: ['open'],
      subtree: true
    });

    this.tree.addEventListener('click', (event) => {
      const target = event.target as HTMLElement | null;
      const treeItem = target?.closest('vscode-tree-item') as HTMLElement | null;
      const nodeId = treeItem?.getAttribute('id');
      if (nodeId) {
        this.onTreeNodeSelected?.(nodeId);
      }
    });
  }

  public updateTreeItem(treeNode: TreeNode) {
    this.tree.querySelector(`vscode-tree-item[id="${treeNode.id}"]`)
        ?.replaceWith(this.toTreeItem(treeNode));
  }

  public toTreeItem(treeNode: TreeNode): HTMLElement {
    const treeItem = document.createElement('vscode-tree-item');
    treeItem.setAttribute('id', treeNode.id);
    treeItem.setAttribute('branch', String(treeNode.children !== undefined));
    this.addTreeItemLabel(treeItem, this.labelText(treeNode.label));
    this.addTreeNodeItems(treeNode.children, treeItem);
    return treeItem;
  }

  private labelText(label: string | { text: string }): string {
    return typeof label === 'string' ? label : (label?.text ?? '');
  }

  private addTreeItemLabel(container: Element, label: string): void {
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

  private handleTreeItemExpanded(treeItem: HTMLElement): void {
    const nodeId = treeItem.getAttribute('id');
    if (nodeId) {
      submit<TreeNode[]>(TreeProtocol.getChildren({ treeNodeId: nodeId, depth: 0 }))
          .then(children => this.setTreeNodeItems(children, treeItem));
    }
  }
}