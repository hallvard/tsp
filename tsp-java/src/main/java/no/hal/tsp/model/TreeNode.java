package no.hal.tsp.model;

/**
 * Represents a node in the tree structure.
 */
public record TreeNode(
    String id,
    String type,
    String semanticType,
    Label label,
    TreeNode[] children
) {
    public TreeNode(String id, String type, String semanticType, Label label) {
        this(id, type, semanticType, label, null);
    }
}
