import * as vscode from 'vscode';
import * as cp from 'child_process';
import * as path from 'path';
import * as rpc from 'vscode-jsonrpc/node';

// Shared TSP server connection
let tspConnection: rpc.MessageConnection | null = null;
let tspServerProcess: cp.ChildProcess | null = null;
const documentWebviews = new Map<string, vscode.WebviewPanel>();

export class TspEditorProvider implements vscode.CustomEditorProvider {

  private readonly _onDidChangeCustomDocument = new vscode.EventEmitter<vscode.CustomDocumentEditEvent>();

  public readonly onDidChangeCustomDocument = this._onDidChangeCustomDocument.event;

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

  constructor(private readonly context: vscode.ExtensionContext) { }

  public async openCustomDocument(
    uri: vscode.Uri,
    _openContext: vscode.CustomDocumentOpenContext,
    _token: vscode.CancellationToken
  ): Promise<vscode.CustomDocument> {
    // Ensure TSP server is running
    await this.ensureTspServerStarted();

    return {
      uri,
      dispose: () => {
        // Optionally send closeResource to TSP server
      }
    };
  }

  public async resolveCustomEditor(
    document: vscode.CustomDocument,
    webviewPanel: vscode.WebviewPanel,
    _token: vscode.CancellationToken
  ): Promise<void> {
    // Setup webview options
    webviewPanel.webview.options = {
      enableScripts: true,
    };

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

    const javaPath = 'java'; // Could be configured in settings
    const tspServerJar = path.join(
      this.context.extensionPath,
      'tsp-server',
      'tsp-emf-1.0.0-SNAPSHOT-jar-with-dependencies.jar'
    );

    tspServerProcess = cp.spawn(javaPath, ['-jar', tspServerJar]);

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

    console.log('TSP server started');
  }

  // Save support - called when user saves or auto-saves
  public async saveCustomDocument(
    document: vscode.CustomDocument,
    _cancellation: vscode.CancellationToken
  ): Promise<void> {
    // TSP server will handle persistence
    // Send save command to server or mark as saved
    console.log('Saving document:', document.uri.fsPath);
  }

  public async saveCustomDocumentAs(
    document: vscode.CustomDocument,
    destination: vscode.Uri,
    _cancellation: vscode.CancellationToken
  ): Promise<void> {
    // Handle "Save As" operation
    console.log('Saving document as:', destination.fsPath);
  }

  public async revertCustomDocument(
    document: vscode.CustomDocument,
    _cancellation: vscode.CancellationToken
  ): Promise<void> {
    // Reload document from disk
    console.log('Reverting document:', document.uri.fsPath);
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

    // Use a nonce for security
    const nonce = getNonce();

    return `<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src ${webview.cspSource} 'unsafe-inline'; script-src 'nonce-${nonce}';">
    <title>TSP Editor</title>
    <style>
        body {
            padding: 10px;
        }
        h1 {
            font-size: 1.2em;
            margin-bottom: 10px;
        }
    </style>
</head>
<body>
    <vscode-tree id="tree-view">
        <vscode-tree-item branch="false">Loading...</vscode-tree-item>
    </vscode-tree>
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
