package no.hal.tsp.protocol;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import no.hal.tsp.model.Form;
import no.hal.tsp.model.Property;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;

/**
 * Interface for form-based editing.
 */
public interface FormApi {
  
  /**
   * Parameters for getFormForTreeNode request.
   */
  public record GetTreeNodeFormParams(
      String documentUri,
      String treeNodeId
  ) implements TreeNodeParams {}

  /**
   * Request to get a form for a specific tree node.
   *
   * @param params reference to the treeNode
   * @return the form to be used for editing the tree node
   */
  @JsonRequest("form/getTreeNodeForm")
  CompletableFuture<Form> getTreeNodeForm(GetTreeNodeFormParams params);

  /**
   * Parameters for validateTreeNodeForm request.
   */
  record ValidateTreeNodeFormParams(
      String documentUri,
      String treeNodeId,
      List<Property.Value> formValues
  ) implements TreeNodeParams {}

  /**
   * Request to validate a form for a specific tree node.
   *
   * @param params reference to the treeNode and the property values to validate
   * @return the form with validation results
   */
  @JsonRequest("form/validateTreeNodeForm")
  CompletableFuture<List<Form.Validation>> validateTreeNodeForm(ValidateTreeNodeFormParams params);

  /**
   * Parameters for commitTreeNodeForm request.
   */
  record CommitTreeNodeFormParams(
      String documentUri,
      String treeNodeId,
      List<Property> formProperties
  ) implements TreeNodeParams {}

  /**
   * Request to commit a form for a specific tree node.
   *
   * @param params reference to the treeNode and the property values to commit
   * @return the form with validation results
   */
  @JsonRequest("form/commitTreeNodeForm")
  CompletableFuture<List<Form.Validation>> commitTreeNodeForm(CommitTreeNodeFormParams params);
}
