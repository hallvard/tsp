import * as vscode from 'vscode';
import * as cp from 'child_process';
import * as path from 'path';
import * as rpc from 'vscode-jsonrpc/node';
import { DocumentProtocol, ServerProtocol } from './server-protocol';

// Shared TSP server connection
let tspConnection: rpc.MessageConnection | null = null;
let tspServerProcess: cp.ChildProcess | null = null;
const documentWebviews = new Map<string, vscode.WebviewPanel>();
const openDocuments = new Map<string, vscode.CustomDocument>();

export class TspEditorProvider implements vscode.CustomEditorProvider {

  private readonly _onDidChangeCustomDocument = new vscode.EventEmitter<vscode.CustomDocumentEditEvent<vscode.CustomDocument>>();

  public readonly onDidChangeCustomDocument = this._onDidChangeCustomDocument.event;

  private labelImagesUri: vscode.Uri;

  public static register(context: vscode.ExtensionContext): vscode.Disposable {
    const provider = new TspEditorProvider(context);
    const providerRegistration = vscode.window.registerCustomEditorProvider(
      'tsp-editor.emf',
      provider,
      {
        webviewOptions: {
          retainContextWhenHidden: true
        },
        supportsMultipleEditorsPerDocument: false
      }
    );

    // Clean up server on deactivation
    context.subscriptions.push({
      dispose: () => {
        if (tspConnection) {
          tspConnection.dispose();
          tspConnection = null;
        }
        if (tspServerProcess) {
          tspServerProcess.kill();
          tspServerProcess = null;
        }
      }
    });

    return providerRegistration;
  }

  constructor(private readonly context: vscode.ExtensionContext) {
    this.labelImagesUri = context.globalStorageUri
      ? vscode.Uri.joinPath(context.globalStorageUri, 'label-images')
      : vscode.Uri.joinPath(context.extensionUri, 'label-images');
  }

  public async openCustomDocument(
    uri: vscode.Uri,
    _openContext: vscode.CustomDocumentOpenContext,
    _token: vscode.CancellationToken
  ): Promise<vscode.CustomDocument> {
    // Ensure TSP server is running
    await this.ensureTspServerStarted();

    const document = {
      uri,
      dispose: () => {
        if (tspConnection) {
          const closeRequest = DocumentProtocol.closeDocument({
            documentUri: uri.toString(),
          });
          tspConnection.sendRequest(closeRequest.method, closeRequest.params).catch((error) => {
            console.log('Error closing document in TSP server:', String(error));
          });
        }
        openDocuments.delete(uri.toString());
      }
    };

    openDocuments.set(uri.toString(), document);

    return document;
  }

  public async resolveCustomEditor(
    document: vscode.CustomDocument,
    webviewPanel: vscode.WebviewPanel,
    _token: vscode.CancellationToken
  ): Promise<void> {
    await this.ensureTspServerStarted();

    // Setup webview options
    webviewPanel.webview.options = {
      enableScripts: true,
      localResourceRoots: [
        this.context.extensionUri,
        this.labelImagesUri,
      ],
    };

    await this.configureServerForWebview(webviewPanel.webview);

    // Set the webview's HTML content
    webviewPanel.webview.html = this.getHtmlForWebview(webviewPanel.webview, document.uri);

    // Track this webview panel by document URI
    const documentKey = document.uri.toString();
    documentWebviews.set(documentKey, webviewPanel);

    // Handle messages from webview to TSP server
    // Forward JSON-RPC requests through the connection, injecting documentUri
    webviewPanel.webview.onDidReceiveMessage(async message => {
      if (tspConnection && message.method) {
        try {
          // Add documentUri to params before forwarding to TSP server
          const paramsWithDocumentUri = {
            ...message.params,
            documentUri: document.uri.toString()
          };
          console.log('Forwarding request to TSP server:',
            message.method, " ", paramsWithDocumentUri.documentUri);
          const result = await tspConnection.sendRequest(message.method, paramsWithDocumentUri);
          // Send response back to webview
          console.log('Forwarding response to webview:',
            message.method, " ", JSON.stringify(result));
          webviewPanel.webview.postMessage({
            jsonrpc: '2.0',
            id: message.id,
            result: result
          });
        } catch (error) {
          console.log('Error forwarding request to TSP server:', String(error));
          webviewPanel.webview.postMessage({
            jsonrpc: '2.0',
            id: message.id,
            error: { message: String(error) }
          });
        }
      }
    });

    // Clean up when webview is closed
    webviewPanel.onDidDispose(() => {
      documentWebviews.delete(documentKey);
    });
  }

  private async ensureTspServerStarted(): Promise<void> {
    if (tspConnection) {
      return; // Already running
    }

    await vscode.workspace.fs.createDirectory(this.labelImagesUri);

    const javaPath = 'java'; // Could be configured in settings
    const tspServerDir = path.join(
      this.context.extensionPath,
      'tsp-server'
    );
    const tspServerJar = path.join(
      tspServerDir,
      'tsp-emf-1.0.0-SNAPSHOT-standalone.jar'
    );

    tspServerProcess = cp.spawn(
      javaPath,
      ['-jar', tspServerJar],
      { cwd: tspServerDir }
    );

    // Create JSON-RPC connection
    const reader = new rpc.StreamMessageReader(tspServerProcess.stdout!);
    const writer = new rpc.StreamMessageWriter(tspServerProcess.stdin!);
    tspConnection = rpc.createMessageConnection(reader, writer);

    // Log stderr
    tspServerProcess.stderr?.on('data', (data) => {
      console.error('TSP server log output:', data.toString());
    });

    tspServerProcess.on('exit', (code) => {
      console.log(`TSP server exited with code ${code}`);
      tspConnection = null;
      tspServerProcess = null;
    });

    // Start listening
    tspConnection.listen();

    tspConnection.onNotification('document/edited', (params: {
      documentUri?: string,
      kind?: 'NORMAL' | 'UNDO' | 'REDO',
      affectedObjectIds?: string[]
    }) => {
      console.log(`document/edited ${JSON.stringify(params)}`);
      if (!params.documentUri) {
        return;
      }
      const kind = params.kind ?? 'NORMAL';
      documentWebviews.get(params.documentUri)?.webview.postMessage({
        jsonrpc: '2.0',
        method: 'document/edited',
        params: {
          documentUri: params.documentUri,
          kind,
          affectedObjectIds: params.affectedObjectIds ?? []
        }
      });
      if (kind !== 'NORMAL') {
        return;
      }
      const document = openDocuments.get(params.documentUri);
      if (document) {
        this._onDidChangeCustomDocument.fire({
          document,
          label: 'TSP Edit',
          undo: async () => {
            await this.performUndo(document);
          },
          redo: async () => {
            await this.performRedo(document);
          }
        });
      }
    });

    console.log('TSP server started');
  }

  private async configureServerForWebview(webview: vscode.Webview): Promise<void> {
    if (!tspConnection) {
      return;
    }
    const labelImagesUri = webview.asWebviewUri(this.labelImagesUri).toString();
    const configureRequest = ServerProtocol.configure({
      settings: {
        'tsp.label.images.dir': this.labelImagesUri.fsPath,
        'tsp.label.images.uri': labelImagesUri,
      }
    });
    await tspConnection.sendRequest(configureRequest.method, configureRequest.params);
  }

  // Save support - called when user saves or auto-saves
  public async saveCustomDocument(
    document: vscode.CustomDocument,
    _cancellation: vscode.CancellationToken
  ): Promise<void> {
    if (!tspConnection) {
      throw new Error('TSP server connection is not available');
    }
    const saveRequest = DocumentProtocol.saveDocument({
      documentUri: document.uri.toString(),
    });
    await tspConnection.sendRequest(saveRequest.method, saveRequest.params);
    console.log('Saved document:', document.uri.fsPath);
  }

  public async saveCustomDocumentAs(
    document: vscode.CustomDocument,
    destination: vscode.Uri,
    _cancellation: vscode.CancellationToken
  ): Promise<void> {
    if (!tspConnection) {
      throw new Error('TSP server connection is not available');
    }
    const saveAsRequest = DocumentProtocol.saveDocument({
      documentUri: document.uri.toString(),
      newUri: {
        newUri: destination.toString(),
        useNewUri: true,
      }
    });
    await tspConnection.sendRequest(saveAsRequest.method, saveAsRequest.params);
    console.log('Saved document as:', destination.fsPath);
  }

  public async revertCustomDocument(
    document: vscode.CustomDocument,
    _cancellation: vscode.CancellationToken
  ): Promise<void> {
    // Reload document from disk
    console.log('Reverting document:', document.uri.fsPath);
  }

  public async undoCustomDocument(
    document: vscode.CustomDocument,
    _context: vscode.CustomDocumentEditEvent,
    _cancellation: vscode.CancellationToken
  ): Promise<void> {
    await this.performUndo(document);
  }

  public async redoCustomDocument(
    document: vscode.CustomDocument,
    _context: vscode.CustomDocumentEditEvent,
    _cancellation: vscode.CancellationToken
  ): Promise<void> {
    await this.performRedo(document);
  }

  public async backupCustomDocument(
    document: vscode.CustomDocument,
    context: vscode.CustomDocumentBackupContext,
    _cancellation: vscode.CancellationToken
  ): Promise<vscode.CustomDocumentBackup> {
    // Create backup for hot exit
    return {
      id: context.destination.toString(),
      delete: () => { }
    };
  }

  private async performUndo(document: vscode.CustomDocument): Promise<void> {
    if (!tspConnection) {
      throw new Error('TSP server connection is not available');
    }
    const documentUri = document.uri.toString();
    const undoRequest = DocumentProtocol.undoEdits({
      documentUri,
      count: 1,
    });
    await tspConnection.sendRequest(undoRequest.method, undoRequest.params);
  }

  private async performRedo(document: vscode.CustomDocument): Promise<void> {
    if (!tspConnection) {
      throw new Error('TSP server connection is not available');
    }
    const documentUri = document.uri.toString();
    const redoRequest = DocumentProtocol.redoEdits({
      documentUri,
      count: 1,
    });
    await tspConnection.sendRequest(redoRequest.method, redoRequest.params);
  }

  private getHtmlForWebview(webview: vscode.Webview, documentUri: vscode.Uri): string {
    // Get URIs for resources
    const elementsUri = webview.asWebviewUri(
      vscode.Uri.joinPath(
        this.context.extensionUri,
        'node_modules',
        '@vscode-elements',
        'elements',
        'dist',
        'bundled.js'
      )
    );

    const scriptUri = webview.asWebviewUri(
      vscode.Uri.joinPath(this.context.extensionUri, 'dist', 'webview.js')
    );

    const styleUri = webview.asWebviewUri(
      vscode.Uri.joinPath(this.context.extensionUri, 'dist', 'webview.css')
    );

    // Use a nonce for security
    const nonce = getNonce();

    return `<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src ${webview.cspSource}; img-src ${webview.cspSource} data:; script-src 'nonce-${nonce}';">
    <title>TSP Editor</title>
    <link rel="stylesheet" href="${styleUri}">
</head>
<body>
    <vscode-split-layout initialHandlePosition="30%" resetOnDblClick="true">
        <vscode-tree id="tree-view" slot="start">
            <vscode-tree-item branch="false">Loading...</vscode-tree-item>
        </vscode-tree>
        <form id="form-view" slot="end">
            <h3>No properties</h3>
        </form>
    </vscode-split-layout>

    <script type="module" nonce="${nonce}" src="${elementsUri}"></script>
    <script nonce="${nonce}">
        window.documentUri = ${JSON.stringify(documentUri.toString())};
    </script>
    <script nonce="${nonce}" src="${scriptUri}"></script>
</body>
</html>`;
  }
}

function getNonce() {
  let text = '';
  const possible = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
  for (let i = 0; i < 32; i++) {
    text += possible.charAt(Math.floor(Math.random() * possible.length));
  }
  return text;
}
