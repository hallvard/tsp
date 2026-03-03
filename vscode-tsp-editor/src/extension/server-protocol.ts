export interface ConfigureParams {
  settings: Record<string, string>;
}

export interface NewUriOptions {
  newUri: string;
  useNewUri: boolean;
}

export interface SaveDocumentParams {
  documentUri: string;
  newUri?: NewUriOptions;
}

export interface CloseDocumentParams {
  documentUri: string;
}

export interface UndoRedoParams {
  documentUri: string;
  count: number;
}

export namespace ServerProtocol {
  export function configure(params: ConfigureParams): {
    method: 'server/configure';
    params: ConfigureParams;
  } {
    return {
      method: 'server/configure',
      params,
    };
  }
}

export namespace DocumentProtocol {
  export function saveDocument(params: SaveDocumentParams): {
    method: 'document/saveDocument';
    params: SaveDocumentParams;
  } {
    return {
      method: 'document/saveDocument',
      params,
    };
  }

  export function closeDocument(params: CloseDocumentParams): {
    method: 'document/closeDocument';
    params: CloseDocumentParams;
  } {
    return {
      method: 'document/closeDocument',
      params,
    };
  }

  export function undoEdits(params: UndoRedoParams): {
    method: 'document/undoEdits';
    params: UndoRedoParams;
  } {
    return {
      method: 'document/undoEdits',
      params,
    };
  }

  export function redoEdits(params: UndoRedoParams): {
    method: 'document/redoEdits';
    params: UndoRedoParams;
  } {
    return {
      method: 'document/redoEdits',
      params,
    };
  }
}
