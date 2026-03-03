/**
 * TypeScript protocol definitions matching the Java TSP protocol.
 */

export interface Label {
  text: string;
  description: string;
  imageUri?: string;
}

export type ProtocolMessage<M extends string, P> = {
    jsonrpc: '2.0';
    method: M;
    params: P;
    id: number;
};

let nextRequestId = Date.now();

export function createProtocolMessage<M extends string, P>(method: M, params: P): ProtocolMessage<M, P> {
  return {
    jsonrpc: '2.0',
    method,
    params,
    id: nextRequestId++, // Simple ID generation
  };
}
