import { createProtocolMessage, Label, ProtocolMessage } from "./protocol";
import { TreeNodeParams } from "./tree-protocol";

export interface Form {
  items: FormItem[];
}

export interface FormItem {
  property: Property;
  label: Label;
  valueOptions?: PropertyValue[];
  editable: boolean;
}

export interface ValidationItem {
  propertyName: string;
  status: ValidationStatus;
  message: string;
}

export type ValidationStatus = 'OK' | 'WARNING' | 'ERROR';

export interface Property {
  name: string;
  value: PropertyValue;
}

export interface PropertyValue {
  semanticType: string;
  stringValue?: string;
  stringValues?: string[];
}

/**
 * TypeScript protocol definitions matching the Java TSP protocol.
 */
export interface GetTreeNodeFormParams extends TreeNodeParams {
}

export interface CommitTreeNodeFormParams extends TreeNodeParams {
  formProperties: Property[];
}

export namespace FormProtocol {
  export function getTreeNodeForm(params: GetTreeNodeFormParams): ProtocolMessage<'form/getTreeNodeForm', GetTreeNodeFormParams> {
    return createProtocolMessage<'form/getTreeNodeForm', GetTreeNodeFormParams>('form/getTreeNodeForm', params);
  }

  export function commitTreeNodeForm(params: CommitTreeNodeFormParams): ProtocolMessage<'form/commitTreeNodeForm', CommitTreeNodeFormParams> {
    return createProtocolMessage<'form/commitTreeNodeForm', CommitTreeNodeFormParams>('form/commitTreeNodeForm', params);
  }
}
