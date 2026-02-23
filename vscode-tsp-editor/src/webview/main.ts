/**
 * Webview script for the TSP tree editor.
 * This runs in the webview context and handles tree rendering.
 */

import { Form, FormItem, TreeNode, TreeProtocol, TreeProtocolMessage } from "./protocol";
import { TreeView } from "./tree-view";

declare function acquireVsCodeApi(): {
  postMessage(message: any): void;
  getState(): any;
  setState(state: any): void;
};

const vscode = acquireVsCodeApi();
let treeView: TreeView | null = null;
let formView: HTMLElement | null = null;

console.log('TSP Tree Editor webview script loaded');
customElements.whenDefined('vscode-tree').then(() => {
  treeView = new TreeView(document.getElementById('tree-view')!, (treeNodeId) => {
    submit<Form>(TreeProtocol.getForm({ treeNodeId }))
        .then(form => renderForm(form));
  });
  formView = document.getElementById('form-view');
  console.log('Tree view element: ', treeView);

  console.log('Sending getChildren request for document: ', (window as any).documentUri);
  submit<TreeNode[]>(TreeProtocol.getChildren({ treeNodeId: null, depth: 0 }))
      .then(nodes => {
        console.log('Received response for getChildren: ', JSON.stringify(nodes));
        treeView?.setTreeNodeItems(nodes);
      });
});

function labelText(label: string | { text: string }): string {
  return typeof label === 'string' ? label : (label?.text ?? '');
}

function renderForm(form: Form): void {
  if (!formView) {
    return;
  }
  formView.innerHTML = '';

  const formElement = document.createElement('form');
  formElement.setAttribute('data-form-id', form.id);

  for (const item of form.items) {
    formElement.appendChild(renderFormItem(item));
  }

  formView.appendChild(formElement);
}

function renderFormItem(item: FormItem): HTMLElement {
  const row = document.createElement('div');
  row.className = 'form-row';

  const labelElement = document.createElement('label');
  labelElement.textContent = labelText(item.label);
  labelElement.htmlFor = item.id;
  row.appendChild(labelElement);

  if (item.kind === 'text') {
    const input = document.createElement('input');
    input.type = 'text';
    input.id = item.id;
    input.value = item.value ?? '';
    input.disabled = !item.editable;
    row.appendChild(input);
  }

  return row;
}

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
