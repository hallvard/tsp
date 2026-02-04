/**
 * TypeScript protocol definitions matching the Java TSP protocol.
 */

export interface TreeNode {
    id: string;
    type: string;
    semanticType: string;
    label: string;
    children?: TreeNode[];
}

export interface OpenResourceParams {
    depth: number;
}

export interface GetChildrenParams {
    treeNodeId: string;
    depth: number;
}

export type TreeProtocolMessage<M extends string, P> = {
    jsonrpc: '2.0';
    method: M;
    params: P;
    id: number;
};

export namespace TreeProtocol {
    export function openResource(params: OpenResourceParams): TreeProtocolMessage<'tree/openResource', OpenResourceParams> {
        return createTreeProtocolMessage<'tree/openResource', OpenResourceParams>('tree/openResource', params);
    }
    export function getChildren(params: GetChildrenParams): TreeProtocolMessage<'tree/getChildren', GetChildrenParams> {
        return createTreeProtocolMessage<'tree/getChildren', GetChildrenParams>('tree/getChildren', params);
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
