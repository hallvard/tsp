/**
 * TypeScript protocol definitions matching the Java TSP protocol.
 */

import { createProtocolMessage, Label, ProtocolMessage } from "./protocol";

export interface TreeNode {
  id: string;
  type: string;
  semanticType: string;
  label: Label;
  children?: TreeNode[];
}

export interface OpenDocumentParams {
  depth: number;
}

export interface TreeNodeParams {
  treeNodeId: string | null;
}

export interface GetChildrenParams extends TreeNodeParams {
  depth: number;
}

export interface TreeCommand {
  id: string;
  label: Label;
}

export interface TreeCommandMenu {
  label: Label;
  items: Array<TreeCommandMenu | TreeCommand>;
}

export interface GetCommandMenuParams extends TreeNodeParams {
}

export interface DoCommandParams extends TreeNodeParams {
  commandId: string;
}

export namespace TreeProtocol {
  export function openDocument(params: OpenDocumentParams): ProtocolMessage<'document/openDocument', OpenDocumentParams> {
    return createProtocolMessage<'document/openDocument', OpenDocumentParams>('document/openDocument', params);
  }
  export function getChildren(params: GetChildrenParams): ProtocolMessage<'tree/getChildren', GetChildrenParams> {
    return createProtocolMessage<'tree/getChildren', GetChildrenParams>('tree/getChildren', params);
  }

  export function getCommandMenu(params: GetCommandMenuParams): ProtocolMessage<'tree/getCommandMenu', GetCommandMenuParams> {
    return createProtocolMessage<'tree/getCommandMenu', GetCommandMenuParams>('tree/getCommandMenu', params);
  }

  export function doCommand(params: DoCommandParams): ProtocolMessage<'tree/doCommand', DoCommandParams> {
    return createProtocolMessage<'tree/doCommand', DoCommandParams>('tree/doCommand', params);
  }
}
