import { Form, FormItem, FormProtocol, PropertyValue } from "./form-protocol";
import { submit } from "./main";

export class FormView {

  private form : HTMLElement;
  private selectedTreeNodeId: string | null = null;
  private currentForm: Form | null = null;
  private initialFormValues: string[] = [];

  constructor(form: HTMLElement) {
    this.form = form;
    this.form.addEventListener('input', () => this.updateActionButtons());
    this.form.addEventListener('change', () => this.updateActionButtons());
  }

  public handleTreeNodeSelection = (treeNodeId: string, isSelected: boolean) => {
    console.log(`Tree node selection changed: ${treeNodeId}, selected: ${isSelected}`);
    this.selectedTreeNodeId = isSelected ? treeNodeId : null;
    this.reloadForm();
  };

  public handleDocumentEdited(affectedObjectIds?: string[]): void {
    if (!this.selectedTreeNodeId) {
      return;
    }
    if (!affectedObjectIds || affectedObjectIds.length === 0 || affectedObjectIds.includes(this.selectedTreeNodeId)) {
      this.reloadForm();
    }
  }

  private reloadForm(): void {
    if (!this.selectedTreeNodeId) {
      return;
    }
    submit<Form>(FormProtocol.getTreeNodeForm({ treeNodeId: this.selectedTreeNodeId }))
        .then(form => this.updateForm(form));
  }

  private updateForm(form: Form): void {
    console.log('Updating form with data: ', JSON.stringify(form));
    this.form.innerHTML = '';
    this.currentForm = form;
    this.initialFormValues = form.items.map((item) => this.propertyValueToString(item.property.value));

    const formHeader = document.createElement('div');
    formHeader.className = 'form-header';

    const title = document.createElement('h3');

    if (form.items.length === 0) {
      title.textContent = 'No properties';
      formHeader.appendChild(title);
      this.form.appendChild(formHeader);
      return;
    }

    title.textContent = 'Properties';
    formHeader.appendChild(title);
    formHeader.appendChild(this.createFormActions());
    this.form.appendChild(formHeader);

    form.items.forEach((item, index) => {
      this.form.appendChild(this.createFormItemElement(item, index));
    });
  }

  private createFormItemElement(item: FormItem, index: number): HTMLElement {
    const itemContainer = document.createElement('div');
    itemContainer.style.marginBottom = '12px';

    const controlId = `form-item-${index}`;

    const label = document.createElement('vscode-label');
    label.setAttribute('for', controlId);
    label.textContent = item.label.text;
    itemContainer.appendChild(label);

    if (item.valueOptions && item.valueOptions.length > 0) {
      itemContainer.appendChild(this.createSelectInput(item, controlId));
    } else {
      itemContainer.appendChild(this.createTextInput(item, controlId));
    }
    if (item.label.description) {
      const helper = document.createElement('vscode-form-helper');
      helper.textContent = item.label.description;
      itemContainer.appendChild(helper);
    }

    return itemContainer;
  }

  private createFormActions(): HTMLElement {
    const actions = document.createElement('div');
    actions.className = 'form-actions';

    const submitButton = document.createElement('vscode-button');
    submitButton.id = 'form-submit-button';
    submitButton.textContent = '✓';
    submitButton.setAttribute('title', 'Submit changes');
    submitButton.setAttribute('appearance', 'primary');
    submitButton.setAttribute('disabled', '');
    submitButton.addEventListener('click', () => {
      this.submitForm();
    });

    const cancelButton = document.createElement('vscode-button');
    cancelButton.id = 'form-cancel-button';
    cancelButton.textContent = '✕';
    cancelButton.setAttribute('title', 'Cancel changes');
    cancelButton.setAttribute('disabled', '');
    cancelButton.addEventListener('click', () => {
      this.reloadForm();
    });

    actions.appendChild(submitButton);
    actions.appendChild(cancelButton);

    return actions;
  }

  private async submitForm(): Promise<void> {
    if (!this.selectedTreeNodeId || !this.currentForm) {
      return;
    }

    const formProperties = this.currentForm.items.map((item, index) => ({
      name: item.property.name,
      value: {
        semanticType: item.property.value.semanticType,
        stringValue: this.controlValue(index),
        stringValues: undefined,
      },
    }));

    await submit(FormProtocol.commitTreeNodeForm({
      treeNodeId: this.selectedTreeNodeId,
      formProperties,
    }));

    this.reloadForm();
  }

  private updateActionButtons(): void {
    const isChanged = this.isFormChanged();
    const submitButton = this.form.querySelector('#form-submit-button');
    const cancelButton = this.form.querySelector('#form-cancel-button');

    if (submitButton) {
      submitButton.toggleAttribute('disabled', !isChanged);
    }
    if (cancelButton) {
      cancelButton.toggleAttribute('disabled', !isChanged);
    }
  }

  private isFormChanged(): boolean {
    if (!this.currentForm) {
      return false;
    }
    for (let i = 0; i < this.currentForm.items.length; i++) {
      if (this.controlValue(i) !== this.initialFormValues[i]) {
        return true;
      }
    }
    return false;
  }

  private controlValue(index: number): string {
    const controlId = `form-item-${index}`;
    const field = this.form.querySelector(`#${controlId}`) as (HTMLElement & { value?: string }) | null;
    return field?.value ?? '';
  }

  private createTextInput(item: FormItem, controlId: string): HTMLElement {
    const input = document.createElement('vscode-textfield');
    input.id = controlId;
    input.setAttribute('name', item.property.name);
    input.setAttribute('value', this.propertyValueToString(item.property.value));
    if (! item.editable) {
      input.setAttribute('readonly', '');
    }
    return input;
  }

  private createSelectInput(item: FormItem, controlId: string): HTMLElement {
    const select = document.createElement('vscode-single-select');
    select.id = controlId;
    select.setAttribute('name', item.property.name);
    if (! item.editable) {
      select.setAttribute('disabled', '');
    }
    const selectedValue = this.propertyValueToString(item.property.value);

    item.valueOptions?.forEach((option, index) => {
      const optionElement = document.createElement('vscode-option');
      const optionValue = this.propertyValueToString(option) || String(index);
      optionElement.setAttribute('value', optionValue);
      optionElement.textContent = this.propertyValueToString(option);
      if (optionValue === selectedValue) {
        optionElement.setAttribute('selected', '');
      }
      select.appendChild(optionElement);
    });

    return select;
  }

  private propertyValueToString(value: PropertyValue): string {
    return value.stringValue ?? "?";
  }
}
