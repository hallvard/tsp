# TSP Server

Tree Structure Protocol server implementation using LSP4J.

## Build

```bash
mvn clean package
```

This creates `target/tsp-server-1.0.0-SNAPSHOT-jar-with-dependencies.jar`

## Run

```bash
java -jar target/tsp-server-1.0.0-SNAPSHOT-jar-with-dependencies.jar <path-to-model-file>
```

## Protocol

The server implements the Tree Structure Protocol (TSP) using JSON-RPC over stdin/stdout.

### Methods

#### `tree/getRootNodes`

Returns the root nodes of the tree structure.

**Request:**
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "tree/getRootNodes",
  "params": {
    "documentUri": "file:///path/to/model.ecore"
  }
}
```

**Response:**
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": [
    {
      "id": "root-1",
      "type": "container",
      "semanticType": "ecore:EPackage",
      "label": "Example Package",
      "children": [...]
    }
  ]
}
```

## Node Structure

Each tree node has:
- `id`: Unique identifier
- `type`: Visual type (e.g., "container", "class", "attribute")
- `semanticType`: Semantic/domain type (e.g., "ecore:EPackage", "ecore:EClass")
- `label`: Display label
- `children`: Array of child nodes (optional)
