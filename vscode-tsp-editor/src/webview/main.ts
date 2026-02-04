/**
 * Webview script for the TSP tree editor.
 * This runs in the webview context and handles tree rendering.
 */

import { TreeNode, TreeProtocol, TreeProtocolMessage } from "./protocol";
import { TreeView } from "./tree-view";

declare function acquireVsCodeApi(): {
  postMessage(message: any): void;
  getState(): any;
  setState(state: any): void;
};

const vscode = acquireVsCodeApi();
let treeView: TreeView | null = null;

console.log('TSP Tree Editor webview script loaded');
customElements.whenDefined('vscode-tree').then(() => {
  treeView = new TreeView(document.getElementById('tree-view')!);
  console.log('Tree view element: ', treeView);

  console.log('Sending openResource request for document: ', (window as any).documentUri);
  submit<TreeNode[]>(TreeProtocol.openResource({ depth: 0 }))
      .then(nodes => {
        console.log('Received response for openResource or getChildren: ', JSON.stringify(nodes));
        treeView?.setTreeNodeItems(nodes);
      });
});

export const pendingRequests = new Map<number, { resolve: (result: any) => void; reject: (error: any) => void }>();

export function submit<T>(message: TreeProtocolMessage<string, any>): Promise<T> {
  return new Promise((resolve, reject) => {
    pendingRequests.set(message.id, { resolve, reject });
    vscode.postMessage(message);
  });
}

// Handle messages from extension (TSP protocol responses)
window.addEventListener('message', (event) => {
  const message = event.data;

  if (message.jsonrpc === '2.0' && message.id !== undefined) {
    const pending = pendingRequests.get(message.id);
    if (pending) {
      pendingRequests.delete(message.id);
      if (message.result !== undefined) {
        pending.resolve(message.result);
      } else if (message.error) {
        pending.reject(message.error);
      }
    }
  }
});
