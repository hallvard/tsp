package no.hal.tsp.model;

/**
 * Represents a node in the tree structure.
 */
public class TreeNode {
    private String id;
    private String type;
    private String semanticType;
    private String label;
    private TreeNode[] children;

    public TreeNode() {
    }

    public TreeNode(String id, String type, String semanticType, String label) {
        this.id = id;
        this.type = type;
        this.semanticType = semanticType;
        this.label = label;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getSemanticType() {
        return semanticType;
    }

    public void setSemanticType(String semanticType) {
        this.semanticType = semanticType;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public TreeNode[] getChildren() {
        return children;
    }

    public void setChildren(TreeNode[] children) {
        this.children = children;
    }
}
