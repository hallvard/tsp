/**
 * Webview script for the TSP tree editor.
 * This runs in the webview context and handles tree rendering.
 */

import { TreeNode, TreeProtocol } from "./protocol";

declare function acquireVsCodeApi(): {
  postMessage(message: any): void;
  getState(): any;
  setState(state: any): void;
};

const vscode = acquireVsCodeApi();
let treeView: HTMLElement | null = null;

console.log('TSP Tree Editor webview script loaded');
// Wait for components to be defined
customElements.whenDefined('vscode-tree').then(() => {
  treeView = document.getElementById('tree-view');
  console.log('Tree view element: ' + treeView);

  // Send initial openResource request to load the tree
  const documentUri = (window as any).documentUri;
  console.log('Sending openResource request for document: ' + documentUri);
  if (documentUri) {
    sendOpenResource(documentUri);
  }
});

// Handle messages from extension (TSP protocol responses)
window.addEventListener('message', (event) => {
  const message = event.data;

  // Handle JSON-RPC responses
  if (message.jsonrpc === '2.0' && message.result) {
    // This is a response to our openResource or getChildren request
    console.log('Received response for openResource or getChildren: '
        + JSON.stringify(message));
    if (Array.isArray(message.result)) {
      displayTreeNodes(message.result);
    }
  } else if (message.method === 'tree/update') {
    // Handle update notifications
    updateTree(message.params);
  } else if (message.method === 'tree/refresh') {
    updateTree(message.params);
  }
});

function sendOpenResource(documentUri: string) {
  vscode.postMessage(TreeProtocol.openResource({
    depth: 0
  }));
}

function displayTreeNodes(nodes: TreeNode[]) {
  if (!treeView) {
    return;
  }

  treeView.innerHTML = '';
  nodes.forEach(node => buildTreeFromData(node, treeView!));
}

function updateTree(treeData: any) {
  if (!treeView) {
    return;
  }

  treeView.innerHTML = '';
  if (treeData && treeData.root) {
    buildTreeFromData(treeData.root, treeView);
  }
}

function buildTreeFromData(node: TreeNode, parent: HTMLElement) {
  const treeItem = document.createElement('vscode-tree-item');

  // Set the text from TSP node data
  treeItem.setAttribute('text', node.label || 'Unnamed');

  // Add icon if provided
  if ((node as any).icon) {
    treeItem.setAttribute('icon', (node as any).icon);
  }

  // Add children
  if (node.children && node.children.length > 0) {
    treeItem.setAttribute('open', '');
    node.children.forEach(child => buildTreeFromData(child, treeItem));
  }

  parent.appendChild(treeItem);
}
