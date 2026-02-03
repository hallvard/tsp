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

export type TreeProtocolRequest<M extends string, P> = {
    jsonrpc: '2.0';
    method: M;
    params: P;
    id: number;
};

export namespace TreeProtocol {
    export function openResource(params: OpenResourceParams): TreeProtocolRequest<'tree/openResource', OpenResourceParams> {
        return createTreeProtocolRequest<'tree/openResource', OpenResourceParams>('tree/openResource', params);
    }
    export function getChildren(params: GetChildrenParams): TreeProtocolRequest<'tree/getChildren', GetChildrenParams> {
        return createTreeProtocolRequest<'tree/getChildren', GetChildrenParams>('tree/getChildren', params);
    }
}

export function createTreeProtocolRequest<M extends string, P>(method: M, params: P): TreeProtocolRequest<M, P> {
  return {
    jsonrpc: '2.0',
    method,
    params,
    id: Date.now(), // Simple ID generation
  };
}