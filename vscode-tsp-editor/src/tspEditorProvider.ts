import * as vscode from 'vscode';
import * as cp from 'child_process';
import * as path from 'path';

export class TspEditorProvider implements vscode.CustomTextEditorProvider {
    public static register(context: vscode.ExtensionContext): vscode.Disposable {
        const provider = new TspEditorProvider(context);
        const providerRegistration = vscode.window.registerCustomEditorProvider(
            'tsp-editor.emf',
            provider
        );
        return providerRegistration;
    }

    constructor(private readonly context: vscode.ExtensionContext) {}

    public async resolveCustomTextEditor(
        document: vscode.TextDocument,
        webviewPanel: vscode.WebviewPanel,
        _token: vscode.CancellationToken
    ): Promise<void> {
        // Setup webview options
        webviewPanel.webview.options = {
            enableScripts: true,
        };

        // Set the webview's HTML content
        webviewPanel.webview.html = this.getHtmlForWebview(webviewPanel.webview);

        // Start TSP server process
        const tspServer = this.startTspServer(document.uri.fsPath);

        // Handle messages from TSP server
        tspServer.stdout?.on('data', (data) => {
            try {
                const message = JSON.parse(data.toString());
                // Forward TSP messages to webview
                webviewPanel.webview.postMessage(message);
            } catch (e) {
                console.error('Failed to parse TSP message:', e);
            }
        });

        tspServer.stderr?.on('data', (data) => {
            console.error('TSP server error:', data.toString());
        });

        // Handle messages from webview to TSP server
        webviewPanel.webview.onDidReceiveMessage(message => {
            if (tspServer.stdin) {
                tspServer.stdin.write(JSON.stringify(message) + '\n');
            }
        });

        // Clean up
        webviewPanel.onDidDispose(() => {
            tspServer.kill();
        });
    }

    private startTspServer(documentPath: string): cp.ChildProcess {
        // TODO: Configure Java path and TSP server JAR location
        const javaPath = 'java'; // Could be configured in settings
        const tspServerJar = path.join(this.context.extensionPath, 'tsp-server', 'tsp-server.jar');
        
        const serverProcess = cp.spawn(javaPath, [
            '-jar',
            tspServerJar,
            documentPath
        ]);

        return serverProcess;
    }

    private getHtmlForWebview(webview: vscode.Webview): string {
        // Get the vscode-elements URIs
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
    <h1>EMF Model Tree</h1>
    <vscode-tree id="tree-view" arrows indent-guides>
        <vscode-tree-item text="Loading..."></vscode-tree-item>
    </vscode-tree>

    <script type="module" nonce="${nonce}" src="${elementsUri}"></script>
    <script nonce="${nonce}">
        const vscode = acquireVsCodeApi();
        let treeView;

        // Wait for components to be defined
        customElements.whenDefined('vscode-tree').then(() => {
            treeView = document.getElementById('tree-view');
        });

        // Handle messages from extension (TSP protocol messages)
        window.addEventListener('message', event => {
            const message = event.data;
            
            // Handle different TSP message types
            if (message.method === 'tree/update') {
                updateTree(message.params);
            } else if (message.method === 'tree/refresh') {
                updateTree(message.params);
            }
        });

        // Send initialization message to TSP server
        vscode.postMessage({
            jsonrpc: '2.0',
            method: 'tree/initialize',
            id: 1
        });

        function updateTree(treeData) {
            if (!treeView) return;
            
            treeView.innerHTML = '';
            if (treeData && treeData.root) {
                buildTreeFromData(treeData.root, treeView);
            }
        }

        function buildTreeFromData(node, parent) {
            const treeItem = document.createElement('vscode-tree-item');
            
            // Set the text from TSP node data
            treeItem.setAttribute('text', node.label || node.name || 'Unnamed');
            
            // Add icon if provided
            if (node.icon) {
                treeItem.setAttribute('icon', node.icon);
            }

            // Add children
            if (node.children && node.children.length > 0) {
                treeItem.setAttribute('open', '');
                node.children.forEach(child => buildTreeFromData(child, treeItem));
            }

            parent.appendChild(treeItem);
        }
    </script>
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
