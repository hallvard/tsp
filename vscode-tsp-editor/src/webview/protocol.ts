/**
 * TypeScript protocol definitions matching the Java TSP protocol.
 */

export interface Label {
    text: string;
}

export interface TreeNode {
    id: string;
    type: string;
    semanticType: string;
    label: string | Label;
    children?: TreeNode[];
}

export interface GetChildrenParams {
    treeNodeId: string | null;
    depth: number;
}

export interface Form {
    id: string;
    items: FormItem[];
}

export interface BaseFormItem {
    id: string;
    label: string | Label;
    kind: string;
    editable: boolean;
}

export interface TextFieldFormItem extends BaseFormItem {
    kind: 'text';
    value: string;
}

export type FormItem = TextFieldFormItem;

export interface GetFormParams {
    treeNodeId: string;
}

export type TreeProtocolMessage<M extends string, P> = {
    jsonrpc: '2.0';
    method: M;
    params: P;
    id: number;
};

export namespace TreeProtocol {
    export function getChildren(params: GetChildrenParams): TreeProtocolMessage<'tree/getChildren', GetChildrenParams> {
        return createTreeProtocolMessage<'tree/getChildren', GetChildrenParams>('tree/getChildren', params);
    }
    export function getForm(params: GetFormParams): TreeProtocolMessage<'tree/getForm', GetFormParams> {
        return createTreeProtocolMessage<'tree/getForm', GetFormParams>('tree/getForm', params);
    }
}

let nextRequestId = Date.now();

function createTreeProtocolMessage<M extends string, P>(method: M, params: P): TreeProtocolMessage<M, P> {
  return {
    jsonrpc: '2.0',
    method,
    params,
    id: nextRequestId++, // Simple ID generation
  };
}
