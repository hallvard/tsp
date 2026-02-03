package no.hal.tsp.model;

/**
 * Represents a node in the tree structure.
 */
public record TreeNode(
    String id,
    String type,
    String semanticType,
    String label,
    TreeNode[] children
) {
    public TreeNode(String id, String type, String semanticType, String label) {
        this(id, type, semanticType, label, null);
    }
}
