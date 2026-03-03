/**
 * Webview script for the TSP tree editor.
 * This runs in the webview context and handles tree rendering.
 */

import { FormView } from "./form-view";
import { ProtocolMessage } from "./protocol";
import { TreeNode, TreeProtocol } from "./tree-protocol";
import { TreeView } from "./tree-view";
import "./styles.css";

declare function acquireVsCodeApi(): {
  postMessage(message: any): void;
  getState(): any;
  setState(state: any): void;
};

const vscode = acquireVsCodeApi();
let treeView: TreeView | null = null;
let formView: FormView | null = null;

console.log('TSP Tree Editor webview script loaded');
customElements.whenDefined('vscode-tree').then(() => {
  treeView = new TreeView(document.getElementById('tree-view')!);
  formView = new FormView(document.getElementById('form-view')!);

  treeView.setSelectionHandler((treeNodeId, isSelected) => {
    if (isSelected) {
      formView?.handleTreeNodeSelection(treeNodeId, isSelected);
    }
  });
  
  console.log('Sending openResource request for document: ', (window as any).documentUri);
  submit<void>(TreeProtocol.openDocument({ depth: 0 }))
      .then(() => {
        submit<TreeNode[]>(TreeProtocol.getChildren({ treeNodeId: null, depth: 0 }))
            .then(nodes => {
              console.log('Received response for openResource or getChildren: ', JSON.stringify(nodes));
              treeView?.setTreeNodeItems(nodes);
            });
      });
});

export const pendingRequests = new Map<number, { resolve: (result: any) => void; reject: (error: any) => void }>();

interface DocumentEditedNotification {
  documentUri?: string;
  kind?: 'NORMAL' | 'UNDO' | 'REDO';
  affectedObjectIds?: string[];
}

export function submit<T>(message: ProtocolMessage<string, any>): Promise<T> {
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
  } else if (message.jsonrpc === '2.0' && message.method === 'document/edited') {
    const params = (message.params ?? {}) as DocumentEditedNotification;
    treeView?.handleDocumentEdited(params.affectedObjectIds);
    formView?.handleDocumentEdited(params.affectedObjectIds);
  }
});
