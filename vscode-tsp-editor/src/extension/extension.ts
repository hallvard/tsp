import * as vscode from 'vscode';
import { TspEditorProvider } from './tspEditorProvider';

export function activate(context: vscode.ExtensionContext) {
	console.log('TSP Editor extension is now active!');

	// Register the custom editor provider
	context.subscriptions.push(TspEditorProvider.register(context));
}

export function deactivate() {}
