package org.purc.purcforms.client.view;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import org.purc.purcforms.client.Context;
import org.purc.purcforms.client.Toolbar;
import org.purc.purcforms.client.cmd.ChangedFieldCmd;
import org.purc.purcforms.client.cmd.DeleteFieldCmd;
import org.purc.purcforms.client.cmd.InsertFieldCmd;
import org.purc.purcforms.client.cmd.MoveFieldCmd;
import org.purc.purcforms.client.controller.IFormActionListener;
import org.purc.purcforms.client.controller.IFormChangeListener;
import org.purc.purcforms.client.controller.IFormDesignerListener;
import org.purc.purcforms.client.controller.IFormSelectionListener;
import org.purc.purcforms.client.locale.LocaleText;
import org.purc.purcforms.client.model.FormDef;
import org.purc.purcforms.client.model.GroupQtnsDef;
import org.purc.purcforms.client.model.ModelConstants;
import org.purc.purcforms.client.model.OptionDef;
import org.purc.purcforms.client.model.PageDef;
import org.purc.purcforms.client.model.QuestionDef;
import org.purc.purcforms.client.util.FormDesignerUtil;
import org.purc.purcforms.client.util.FormUtil;
import org.purc.purcforms.client.widget.CompositeTreeItem;
import org.purc.purcforms.client.widget.TreeItemWidget;

import com.google.gwt.event.dom.client.MouseDownEvent;
import com.google.gwt.event.dom.client.MouseDownHandler;
import com.google.gwt.event.dom.client.MouseUpEvent;
import com.google.gwt.event.dom.client.MouseUpHandler;
import com.google.gwt.event.logical.shared.SelectionEvent;
import com.google.gwt.event.logical.shared.SelectionHandler;
import com.google.gwt.resources.client.ImageResource;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.DeferredCommand;
import com.google.gwt.user.client.Element;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.MenuBar;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwt.user.client.ui.Tree;
import com.google.gwt.user.client.ui.TreeItem;


/**
 * Displays questions in a tree view.
 * 
 * @author daniel
 *
 */
public class FormsTreeView extends Composite implements SelectionHandler<TreeItem>,IFormChangeListener,IFormActionListener{

	/**
	 * Specifies the images that will be bundled for this Composite and specify
	 * that tree's images should also be included in the same bundle.
	 */
	public interface Images extends Toolbar.Images, Tree.Resources {
		ImageResource drafts();
		ImageResource markRead();
		ImageResource templates();
		ImageResource note();
		ImageResource lookup();
	}

	/** The main or root widget for displaying the list of forms and their contents
	 * in a tree view.
	 */
	private Tree tree;

	/** The tree images. */
	private final Images images;

	/** Pop up for displaying tree item context menu. */
	private PopupPanel popup;

	/** The item that has been copied to the clipboard. */
	private Object clipboardItem;
	private boolean inCutMode = false;

	/** The currently selected tree item. */
	private TreeItem item;

	/** Flag determining whether to set the form node as the root tree node. */
	private boolean showFormAsRoot;

	/** The currently selected form. */
	private FormDef formDef;

	/** List of form item selection listeners. */
	private List<IFormSelectionListener> formSelectionListeners = new ArrayList<IFormSelectionListener>();

	/** The next available form id. */
	private int nextFormId = 0;

	/** The next available page id. */
	private int nextPageId = 0;

	/** The next available question id. */
	private int nextQuestionId = 0;

	/** The next available question option id. */
	private int nextOptionId = 0;

	/** The listener to form designer global events. */
	private IFormDesignerListener formDesignerListener;


	/**
	 * Creates a new instance of the forms tree view widget.
	 * 
	 * @param images the tree images.
	 * @param formSelectionListener the form item selection events listener.
	 */
	public FormsTreeView(Images images,IFormSelectionListener formSelectionListener) {

		this.images = images;
		this.formSelectionListeners.add(formSelectionListener);

		tree = new Tree(images);

		initWidget(tree);
		FormUtil.maximizeWidget(tree);

		tree.addSelectionHandler(this);
		tree.ensureSelectedItemVisible();

		//This is just for solving a wiered behaviour when one changes a node text
		//and the click another node which gets the same text as the previously
		//selected text. Just comment it out and you will see what happens.
		tree.addMouseDownHandler(new MouseDownHandler(){
			public void onMouseDown(MouseDownEvent event){
				tree.setSelectedItem(tree.getSelectedItem());
				scrollToLeft();
			}
		});

		tree.addMouseUpHandler(new MouseUpHandler(){
			public void onMouseUp(MouseUpEvent event){
				scrollToLeft();
			}
		});

		initContextMenu();
	}


	private void scrollToLeft(){
		DeferredCommand.addCommand(new Command(){
			public void execute(){
				Element element = (Element)getParent().getParent().getParent().getElement().getChildNodes().getItem(0).getChildNodes().getItem(0);
				DOM.setElementPropertyInt(element, "scrollLeft", 0);
			}
		});
	}


	/**
	 * Sets the listener for form designer global events.
	 * 
	 * @param formDesignerListener the listener.
	 */
	public void setFormDesignerListener(IFormDesignerListener formDesignerListener){
		this.formDesignerListener = formDesignerListener;
	}

	/**
	 * Adds a listener to form item selection events.
	 * 
	 * @param formSelectionListener the listener to add.
	 */
	public void addFormSelectionListener(IFormSelectionListener formSelectionListener){
		this.formSelectionListeners.add(formSelectionListener);
	}

	public void showFormAsRoot(boolean showFormAsRoot){
		this.showFormAsRoot = showFormAsRoot;
	}

	/**
	 * Prepares the tree item context menu.
	 */
	private void initContextMenu(){
		popup = new PopupPanel(true,true);

		MenuBar menuBar = new MenuBar(true);

		boolean readOnly = FormDesignerUtil.inReadOnlyMode();

		if(!readOnly){
			menuBar.addItem(FormDesignerUtil.createHeaderHTML(images.add(),LocaleText.get("addNew")),true, new Command(){
				public void execute() {popup.hide(); addNewItem();}});

			menuBar.addSeparator();		  
			menuBar.addItem(FormDesignerUtil.createHeaderHTML(images.addchild(),LocaleText.get("addNewChild")),true, new Command(){
				public void execute() {popup.hide(); addNewChildItem();}});

			menuBar.addSeparator();		  
			menuBar.addItem(FormDesignerUtil.createHeaderHTML(images.delete(),LocaleText.get("deleteItem")),true,new Command(){
				public void execute() {popup.hide(); deleteSelectedItem();}});

			menuBar.addSeparator();		  
			menuBar.addItem(FormDesignerUtil.createHeaderHTML(images.moveup(),LocaleText.get("moveUp")),true, new Command(){
				public void execute() {popup.hide(); moveItemUp();}});

			menuBar.addItem(FormDesignerUtil.createHeaderHTML(images.movedown(),LocaleText.get("moveDown")),true, new Command(){
				public void execute() {popup.hide(); moveItemDown();}});

			menuBar.addSeparator();		  
			menuBar.addItem(FormDesignerUtil.createHeaderHTML(images.cut(),LocaleText.get("cut")),true,new Command(){
				public void execute() {popup.hide(); cutItem();}});

			menuBar.addItem(FormDesignerUtil.createHeaderHTML(images.copy(),LocaleText.get("copy")),true,new Command(){
				public void execute() {popup.hide(); copyItem();}});

			menuBar.addItem(FormDesignerUtil.createHeaderHTML(images.paste(),LocaleText.get("paste")),true,new Command(){
				public void execute() {popup.hide(); pasteItem();}});

			menuBar.addSeparator();	
		}

		menuBar.addItem(FormDesignerUtil.createHeaderHTML(images.save(),LocaleText.get("save")),true,new Command(){
			public void execute() {popup.hide(); saveItem();}});

		if(!readOnly){
			menuBar.addSeparator();		  
			menuBar.addItem(FormDesignerUtil.createHeaderHTML(images.refresh(),LocaleText.get("refresh")),true,new Command(){
				public void execute() {popup.hide(); refreshItem();}});
			
			menuBar.addSeparator();		  
			menuBar.addItem(FormDesignerUtil.createHeaderHTML(images.add(), LocaleText.get("designSurface")),true,new Command(){
				public void execute() {popup.hide(); addToDesignSurface();}});

			if(FormUtil.rebuildBindings()){
				menuBar.addSeparator();	
				menuBar.addItem(FormDesignerUtil.createHeaderHTML(images.templates(),LocaleText.get("rebuildBindings")),true,new Command(){
					public void execute() {popup.hide(); rebuildBindings();}});
			}
		}

		popup.setWidget(menuBar);
	}


	private TreeItem addImageItem(TreeItem root, String title,ImageResource imageProto, Object userObj,String helpText){
		return addImageItem(null, root, title, imageProto, userObj, helpText);
	}
	
	private TreeItem addImageItem(TreeItem root, String title,ImageResource imageProto, Object userObj,String helpText, boolean storeHistory) {
		return addImageItem(null, root, title, imageProto, userObj, helpText, storeHistory);
	}
	
	private TreeItem addImageItem(TreeItem inserAfterItem, TreeItem root, String title,ImageResource imageProto, Object userObj,String helpText) {
		return addImageItem(inserAfterItem, root, title, imageProto, userObj, helpText, false);
	}

	/**
	 * A helper method to simplify adding tree items that have attached images.
	 * {@link #addImageItem(TreeItem, String) code}
	 * 
	 * @param root the tree item to which the new item will be added.
	 * @param title the text associated with this item.
	 */
	private TreeItem addImageItem(TreeItem inserAfterItem, TreeItem root, String title,ImageResource imageProto, Object userObj,String helpText, boolean storeHistory) {
		TreeItem item = new CompositeTreeItem(new TreeItemWidget(imageProto, title, popup, this));
		item.setUserObject(userObj);
		item.setTitle(helpText);
		if(root != null){
			if(inserAfterItem != null)
				root.insertItem(item, inserAfterItem); //root.insertItem(root.getChildIndex(inserAfterItem) + 1, item);
			else
				root.addItem(item);
		}
		else
			tree.addItem(item);
		
		if(storeHistory)
			Context.getCommandHistory().add(new InsertFieldCmd(item, this));
		
		return item;
	}


	/**
	 * @see com.google.gwt.event.logical.shared.SelectionHandler#onSelection(SelectionEvent)
	 */
	public void onSelection(SelectionEvent<TreeItem> event){
		selectItem(event.getSelectedItem(), true);
	}
	
	private void selectItem(TreeItem item, boolean optimize){
		scrollToLeft();

		//Should not call this more than once for the same selected item.
		if(!optimize || item != this.item){
			Context.setSelectedItem(item.getUserObject());
			Context.setFormDef(FormDef.getFormDef(Context.getSelectedItem()));
			formDef = Context.getFormDef();

			fireFormItemSelected(item.getUserObject(), item);
			this.item = item;

			//Expand if has kids such that users do not have to click the plus
			//sign to expand. Besides, some are not even aware of that.
			//if(item.getChildCount() > 0)
			//	item.setState(true);
		}
	}

	/**
	 * Notifies all form item selection listeners about the currently
	 * selected form item.
	 * 
	 * @param formItem the selected form item.
	 */
	private void fireFormItemSelected(Object formItem, TreeItem treeItem){
		for(int i=0; i<formSelectionListeners.size(); i++)
			formSelectionListeners.get(i).onFormItemSelected(formItem, treeItem);
	}

	public void loadForm(FormDef formDef,boolean select, boolean langRefresh){
		if(formDef.getId() == ModelConstants.NULL_ID)
			formDef.setId(++nextFormId);

		if(!langRefresh){
			int count = formDef.getQuestionCount();
			if(nextQuestionId < count)
				nextQuestionId = count;

			count = formDef.getPageCount();
			if(nextPageId < count)
				nextPageId = count;

			this.formDef = formDef;

			if(formExists(formDef.getId()))
				return;

			//A temporary hack to ensure top level object is accessed.
			fireFormItemSelected(formDef, tree.getSelectedItem());
		}

		TreeItem formRoot = null;
		if(showFormAsRoot){
			formRoot = new CompositeTreeItem(new TreeItemWidget(images.note(), formDef.getName(),popup,this));
			formRoot.setUserObject(formDef);
			tree.addItem(formRoot);
		}

		if(formDef.getPages() != null){
			for(int currentPageNo =0; currentPageNo<formDef.getPages().size(); currentPageNo++){
				TreeItem pageRoot = loadPage((PageDef)formDef.getPages().elementAt(currentPageNo), null, formRoot);

				//We expand only the first page.
				if(currentPageNo == 0)
					pageRoot.setState(true);    
			}
		}

		if(select && formRoot != null){
			tree.setSelectedItem(formRoot);
			formRoot.setState(true);
		}

	}

	/**
	 * Check if a form with a given id is loaded.
	 * 
	 * @param formId the form id.
	 * @return true if it exists, else false.
	 */
	public boolean formExists(int formId){
		int count = tree.getItemCount();
		for(int index = 0; index < count; index++){
			TreeItem item = tree.getItem(index);
			if(((FormDef)item.getUserObject()).getId() == formId){
				tree.setSelectedItem(item);
				return true;
			}
		}

		return false;
	}

	public void refreshForm(FormDef formDef){
		//tree.clear();
		TreeItem item = tree.getSelectedItem();
		if(item != null){
			TreeItem root = getSelectedItemRoot(item);
			formDef.setId(((FormDef)root.getUserObject()).getId());

			tree.removeItem(root);
		}

		loadForm(formDef, true, false);
	}

	/**
	 * Gets the list of forms that have been loaded.
	 * 
	 * @return the form list.
	 */
	public List<FormDef> getForms(){
		List<FormDef> forms = new ArrayList<FormDef>();

		int count = tree.getItemCount();
		for(int index = 0; index < count; index++)
			forms.add((FormDef)tree.getItem(index).getUserObject());

		return forms;
	}

	/**
	 * Loads a list of forms and selects one of them.
	 * 
	 * @param forms the form list to load.
	 * @param selFormId the id of the form to select.
	 */
	public void loadForms(List<FormDef> forms, int selFormId){
		if(forms == null || forms.size() == 0)
			return;

		tree.clear();
		this.formDef = null;

		for(FormDef formDef : forms){
			loadForm(formDef,formDef.getId() == selFormId,true);

			if(formDef.getId() == selFormId){
				this.formDef = formDef;
				//A temporary hack to ensure top level object is accessed.
				fireFormItemSelected(this.formDef, tree.getSelectedItem());
			}
		}
	}

	private TreeItem loadPage(PageDef pageDef, TreeItem insertAfterItem, TreeItem formRoot){
		TreeItem pageRoot = addImageItem(insertAfterItem, formRoot, pageDef.getName(), images.drafts(),pageDef,null);
		loadQuestions(pageDef.getQuestions(),pageRoot);
		return pageRoot;
	}

	private void loadQuestions(Vector questions, TreeItem root){
		if(questions != null){
			for(int currentQtnNo=0; currentQtnNo<questions.size(); currentQtnNo++)
				loadQuestion((QuestionDef)questions.elementAt(currentQtnNo), null, root);
		}
	}

	private TreeItem loadQuestion(QuestionDef questionDef, TreeItem insertAfterItem, TreeItem root){
		TreeItem questionRoot = addImageItem(insertAfterItem, root, questionDef.getDisplayText(), images.lookup(),questionDef,questionDef.getHelpText());

		if(questionDef.getDataType() == QuestionDef.QTN_TYPE_LIST_EXCLUSIVE || 
				questionDef.getDataType() == QuestionDef.QTN_TYPE_LIST_MULTIPLE){
			List options = questionDef.getOptions();
			if(options != null){
				for(int currentOptionNo=0; currentOptionNo < options.size(); currentOptionNo++){
					OptionDef optionDef = (OptionDef)options.get(currentOptionNo);
					addImageItem(questionRoot, optionDef.getText(), images.markRead(),optionDef,null);
				}
			}
		}
		else if(questionDef.getDataType() == QuestionDef.QTN_TYPE_BOOLEAN){
			addImageItem(questionRoot, QuestionDef.TRUE_DISPLAY_VALUE, images.markRead(),null,null);
			addImageItem(questionRoot, QuestionDef.FALSE_DISPLAY_VALUE, images.markRead(),null,null);
		}
		else if(questionDef.isGroupQtnsDef())
			loadQuestions(questionDef.getGroupQtnsDef().getQuestions(),questionRoot);

		return questionRoot;
	}

	/**
	 * @see org.purc.purcforms.client.controller.IFormActionListener#deleteSelectedItem()
	 */
	public void deleteSelectedItem(){
		TreeItem item = tree.getSelectedItem();
		if(item == null){
			Window.alert(LocaleText.get("selectDeleteItem"));
			return;
		}

		if(inReadOnlyMode() && !(item.getUserObject() instanceof FormDef))
			return;

		if(!inCutMode && !Window.confirm(LocaleText.get("deleteTreeItemPrompt")))
			return;

		deleteItem(item);
	}
	
	/**
	 * Removes a given tree item from the tree widget.
	 * 
	 * @param item the tree item to delete.
	 */
	public void deleteItem(TreeItem item){
		deleteItem(item, item.getParentItem(), true);
	}

	/**
	 * Removes a given tree item from the tree widget.
	 * 
	 * @param item the tree item to delete.
	 * @param parent the parent of the tree item to delete.
	 */
	public void deleteItem(TreeItem item, TreeItem parent, boolean storeHistory){	
		DeleteFieldCmd deleteFieldCmd = null;
		int index;
		if(parent != null){
			index = parent.getChildIndex(item);

			//If last item is the one selected, the select the previous, else the next.
			if(index == parent.getChildCount()-1)
				index -= 1;

			if(storeHistory)
				deleteFieldCmd = new DeleteFieldCmd(item, parent, index, this);
			
			removeFormDefItem(item, parent);

			//Remove the selected item.
			item.remove();

			//If no more kids, then select the parent.
			if(parent.getChildCount() == 0)
				tree.setSelectedItem(parent);
			else
				tree.setSelectedItem(parent.getChild(index));

			//After deleting, the currently selected item is not highlighted and so this line fixes that problem.
			((CompositeTreeItem)tree.getSelectedItem()).addSelectionStyle();
		}
		else{ //Must be the form root
			index = getRootItemIndex(item);
			item.remove();

			int count = tree.getItemCount();

			//If we have any items left, select the one which was after
			//the one we have just removed.
			if(count > 0){

				//If we have deleted the last item, select the item which was before it.
				if(index == count)
					index--;

				tree.setSelectedItem(tree.getItem(index));
			}
		}
		
		if(storeHistory){
			if(deleteFieldCmd == null)
				deleteFieldCmd = new DeleteFieldCmd(item, parent, index, this);
			
			Context.getCommandHistory().add(deleteFieldCmd);
		}

		if(tree.getSelectedItem() == null){
			Context.setFormDef(null);
			formDef = null;
			
			//Because of command history, we do not want design surface widgets to be cleared is
			//why i have commented out the line below.
			//fireFormItemSelected(null);
			
			//Because of commenting out of the above line, i have replaces it with these
			//three calls such that we clear the xml tabs incase one wants to open
			//a file that requires a file open dialog box which will only come when the
			//xml tab is empty.
			Context.getCenterPanel().setXformsSource(null, false);
			Context.getCenterPanel().setLanguageXml(null, false);
			Context.getCenterPanel().setLayoutXml(null, false);

			if(tree.getItemCount() == 0){
				nextFormId = 0;
				nextOptionId = 0;
				nextPageId = 0;
				nextQuestionId = 0;
			}
		}
	}

	/**
	 * Gets the index of the tree item which is at the root level.
	 * 
	 * @param item the tree root item whose index we are to get.
	 * @return the index of the tree item.
	 */
	private int getRootItemIndex(TreeItem item){
		int count = tree.getItemCount();
		for(int index = 0; index < count; index++){
			if(item == tree.getItem(index))
				return index;
		}

		return 0;
	}

	private void removeFormDefItem(TreeItem item, TreeItem parent){
		Object userObj = item.getUserObject();
		Object parentUserObj = parent.getUserObject();

		if(userObj instanceof QuestionDef){
			if(parentUserObj instanceof QuestionDef)
				((QuestionDef)parentUserObj).getGroupQtnsDef().removeQuestion((QuestionDef)userObj,formDef);
			else
				((PageDef)parentUserObj).removeQuestion((QuestionDef)userObj,formDef);			
		}
		else if(userObj instanceof OptionDef){
			((QuestionDef)parentUserObj).removeOption((OptionDef)userObj);
		}
		else if(userObj instanceof PageDef)
			((FormDef)parentUserObj).removePage((PageDef)userObj);	
	}

	/**
	 * @see org.purc.purcforms.client.controller.IFormActionListener#addNewItem()
	 */
	public void addNewItem(){
		if(inReadOnlyMode())
			return;

		TreeItem item = tree.getSelectedItem();

		//Check if there is any selection.
		if(item != null){
			
			Object userObj = item.getUserObject();
			if(userObj instanceof QuestionDef){
				int id = ++nextQuestionId;
				String binding = FormDesignerUtil.getQtnBinding(id, getNextQuestionPos(item.getParentItem().getUserObject()));
				QuestionDef questionDef = new QuestionDef(id,LocaleText.get("question")+getNextQuestionPos(item.getParentItem().getUserObject()),QuestionDef.QTN_TYPE_TEXT, binding,item.getParentItem().getUserObject());
				item = addImageItem(item, item.getParentItem(), questionDef.getText(), images.lookup(),questionDef,questionDef.getHelpText(), true);
				addFormDefItem(questionDef, userObj, item.getParentItem()); //?????
				tree.setSelectedItem(item);
			}
			else if(userObj instanceof OptionDef){
				int id = ++nextOptionId;
				String binding = FormDesignerUtil.getOptnBinding(id, getNextOptionPos(item.getParentItem().getUserObject()));
				OptionDef optionDef = new OptionDef(id,LocaleText.get("option")+getNextOptionPos(item.getParentItem().getUserObject()), binding,(QuestionDef)item.getParentItem().getUserObject());
				item = addImageItem(item, item.getParentItem(), optionDef.getText(), images.markRead(),optionDef,null, true);
				addFormDefItem(optionDef, userObj, item.getParentItem());
				tree.setSelectedItem(item);	
			}
			else if(userObj instanceof PageDef){
				int id = ++nextPageId;
				PageDef pageDef = new PageDef(LocaleText.get("page")+id,id,null,(FormDef)item.getParentItem().getUserObject());
				item = addImageItem(item, item.getParentItem(), pageDef.getName(), images.drafts(),pageDef,null, true);
				addFormDefItem(pageDef, userObj, item.getParentItem());
				tree.setSelectedItem(item);
			}
			else if(userObj instanceof FormDef)
				addNewForm();
		}
		else
			addNewForm();
	}

	public void addNewQuestion(int dataType){
		if(inReadOnlyMode())
			return;

		TreeItem item = tree.getSelectedItem();

		//Check if there is any selection.
		if(item != null){
			Object userObj = item.getUserObject();
			if(userObj instanceof QuestionDef){
				int id = ++nextQuestionId;
				String binding = FormDesignerUtil.getQtnBinding(id, getNextQuestionPos(item.getParentItem().getUserObject()));
				QuestionDef questionDef = new QuestionDef(id,LocaleText.get("question")+getNextQuestionPos(item.getParentItem().getUserObject()),QuestionDef.QTN_TYPE_TEXT, binding,item.getParentItem().getUserObject());
				questionDef.setDataType(dataType);
				item = addImageItem(item.getParentItem(), questionDef.getText(), images.lookup(),questionDef,questionDef.getHelpText(), true);
				addFormDefItem(questionDef,item.getParentItem());

				if(dataType == QuestionDef.QTN_TYPE_LIST_EXCLUSIVE || dataType == QuestionDef.QTN_TYPE_LIST_MULTIPLE)
					addNewOptionDef(questionDef, item);

				tree.setSelectedItem(item);
			}
			else if(userObj instanceof OptionDef){
				int id = ++nextQuestionId;
				String binding = FormDesignerUtil.getQtnBinding(id, getNextQuestionPos(item.getParentItem().getParentItem().getUserObject()));
				QuestionDef questionDef = new QuestionDef(id,LocaleText.get("question")+getNextQuestionPos(item.getParentItem().getParentItem().getUserObject()),QuestionDef.QTN_TYPE_TEXT, binding,item.getParentItem().getParentItem().getUserObject());
				questionDef.setDataType(dataType);
				item = addImageItem(item.getParentItem().getParentItem(), questionDef.getText(), images.lookup(),questionDef,questionDef.getHelpText(), true);
				addFormDefItem(questionDef,item.getParentItem());

				if(dataType == QuestionDef.QTN_TYPE_LIST_EXCLUSIVE || dataType == QuestionDef.QTN_TYPE_LIST_MULTIPLE)
					addNewOptionDef(questionDef, item);

				tree.setSelectedItem(item);
			}
			else if(userObj instanceof PageDef){
				int id = ++nextQuestionId;
				String binding = FormDesignerUtil.getQtnBinding(id, getNextQuestionPos(item.getUserObject()));
				QuestionDef questionDef = new QuestionDef(id,LocaleText.get("question")+getNextQuestionPos(item.getUserObject()),QuestionDef.QTN_TYPE_TEXT, binding,item.getUserObject());
				questionDef.setDataType(dataType);
				item = addImageItem(item, questionDef.getText(), images.lookup(),questionDef,questionDef.getHelpText(), true);
				addFormDefItem(questionDef,item.getParentItem());

				if(dataType == QuestionDef.QTN_TYPE_LIST_EXCLUSIVE || dataType == QuestionDef.QTN_TYPE_LIST_MULTIPLE)
					addNewOptionDef(questionDef, item);

				tree.setSelectedItem(item);
			}
			else if(userObj instanceof FormDef){
				//addNewForm();

				//If not yet got pages, just quit.
				if(item.getChildCount() == 0)
					return;

				TreeItem parentItem = item.getChild(0);

				int id = ++nextQuestionId;
				String binding = FormDesignerUtil.getQtnBinding(id, getNextQuestionPos(parentItem.getUserObject()));
				QuestionDef questionDef = new QuestionDef(id,LocaleText.get("question")+getNextQuestionPos(parentItem.getUserObject()),QuestionDef.QTN_TYPE_TEXT, binding,parentItem.getUserObject());
				questionDef.setDataType(dataType);
				item = addImageItem(parentItem, questionDef.getText(), images.lookup(),questionDef,questionDef.getHelpText(), true);
				addFormDefItem(questionDef,item.getParentItem());

				if(dataType == QuestionDef.QTN_TYPE_LIST_EXCLUSIVE || dataType == QuestionDef.QTN_TYPE_LIST_MULTIPLE)
					addNewOptionDef(questionDef, item);

				tree.setSelectedItem(item);
			}
		}
		else{
			addNewForm();
			item = tree.getSelectedItem();
			QuestionDef questionDef = (QuestionDef)item.getUserObject();
			questionDef.setDataType(dataType);

			if(dataType == QuestionDef.QTN_TYPE_LIST_EXCLUSIVE || dataType == QuestionDef.QTN_TYPE_LIST_MULTIPLE)
				addNewOptionDef(questionDef, item);

			tree.setSelectedItem(item.getParentItem());
			tree.setSelectedItem(item);
		}
	}


	private void addNewOptionDef(QuestionDef questionDef, TreeItem parentItem){
		int id = ++nextOptionId;
		String binding = FormDesignerUtil.getOptnBinding(id, getNextOptionPos(questionDef));
		OptionDef optionDef = new OptionDef(id,LocaleText.get("option")+getNextOptionPos(questionDef), binding,questionDef);
		addImageItem(parentItem, optionDef.getText(), images.markRead(),optionDef,null, true);
		addFormDefItem(optionDef,parentItem);

		parentItem.setState(true);
	}

	private void addFormDefItem(Object obj, TreeItem parentItem){
		addFormDefItem(obj, null, parentItem);
	}

	public void addFormDefItem(Object obj, Object refObj, TreeItem parentItem){
		Object parentUserObj = parentItem.getUserObject();
		if(parentUserObj instanceof QuestionDef){
			if(obj instanceof OptionDef)
				((QuestionDef)parentUserObj).addOption((OptionDef)obj, (OptionDef)refObj);
			else
				((QuestionDef)parentUserObj).getGroupQtnsDef().addQuestion((QuestionDef)obj, (QuestionDef)refObj);
		}
		else if(parentUserObj instanceof PageDef)
			((PageDef)parentUserObj).addQuestion((QuestionDef)obj, (QuestionDef)refObj);
		else if(parentUserObj instanceof FormDef)
			((FormDef)parentUserObj).addPage((PageDef)obj, (PageDef)refObj);

	}

	public void addNewForm(){
		int id = ++nextFormId;
		addNewForm(LocaleText.get("newForm")+id,"new_form"+id,id);

		//Automatically add a new page
		addNewChildItem(false);

		//Automatically add a new question
		addNewChildItem(false);
	}

	public void addNewForm(String name, String varName, int formId){
		if(inReadOnlyMode())
			return;

		if(formExists(formId))
			return;

		FormDef formDef = new FormDef(formId,name,varName, varName,null,null,null,null,null,null);
		TreeItem item = new CompositeTreeItem(new TreeItemWidget(images.note(), formDef.getName(),popup,this));
		item.setUserObject(formDef);
		tree.addItem(item);
		tree.setSelectedItem(item);
		
		Context.getCommandHistory().add(new InsertFieldCmd(item, this));
	}

	/**
	 * @see org.purc.purcforms.client.controller.IFormActionListener#addNewChildItem()
	 */
	public void addNewChildItem(){
		addNewChildItem(true);
	}

	/**
	 * Adds a new child item.
	 */
	public void addNewChildItem(boolean addNewIfNoKids){
		if(inReadOnlyMode())
			return;

		TreeItem item = tree.getSelectedItem();

		//Check if there is any selection.
		if(item == null){
			if(addNewIfNoKids)
				addNewItem();
			return;
		}

		Object userObj = item.getUserObject();
		if(userObj instanceof PageDef || 
				(userObj instanceof QuestionDef && ((QuestionDef)userObj).isGroupQtnsDef()) ){

			int id = ++nextQuestionId;
			String binding = FormDesignerUtil.getQtnBinding(id, getNextQuestionPos(userObj));
			QuestionDef questionDef = new QuestionDef(id,LocaleText.get("question")+getNextQuestionPos(userObj),QuestionDef.QTN_TYPE_TEXT, binding,userObj);
			item = addImageItem(item, questionDef.getText(), images.lookup(),questionDef,questionDef.getHelpText(), true);
			addFormDefItem(questionDef,item.getParentItem());
			item.getParentItem().setState(true);
			tree.setSelectedItem(item);
		}
		else if(userObj instanceof QuestionDef && 
				( ((QuestionDef)userObj).getDataType() ==  QuestionDef.QTN_TYPE_LIST_EXCLUSIVE ||
						((QuestionDef)userObj).getDataType() ==  QuestionDef.QTN_TYPE_LIST_MULTIPLE ) ){

			int id = ++nextOptionId;
			String binding = FormDesignerUtil.getOptnBinding(id, getNextOptionPos(userObj));
			OptionDef optionDef = new OptionDef(id,LocaleText.get("option")+getNextOptionPos(userObj), binding,(QuestionDef)userObj);
			item = addImageItem(item, optionDef.getText(), images.markRead(),optionDef,null, true);
			addFormDefItem(optionDef,item.getParentItem());
			item.getParentItem().setState(true);
			tree.setSelectedItem(item);
		}
		else if(userObj instanceof FormDef){
			int id = ++nextPageId;
			PageDef pageDef = new PageDef(LocaleText.get("page")+id,id,null,(FormDef)userObj);
			item = addImageItem(item, pageDef.getName(), images.drafts(),pageDef,null, true);
			addFormDefItem(pageDef,item.getParentItem());
			item.getParentItem().setState(true);
			tree.setSelectedItem(item);
			
			//Automatically add a new question
			addNewChildItem(false);
		}
		else if(addNewIfNoKids)
			addNewItem();
	}

	/**
	 * @see org.purc.purcforms.client.controller.IFormActionListener#moveItemUp()
	 */
	public void moveItemUp() {
		moveItemUp(true);
	}

	public void moveItemUp(boolean storeCommandHistory) {
		if(inReadOnlyMode())
			return;

		TreeItem selectedItem = tree.getSelectedItem();

		//Check if there is any selection.
		if(selectedItem == null)
			return;

		TreeItem parent = selectedItem.getParentItem();

		//We don't move root node (which has no parent, that is the form itself, since we design one form at a time)
		if(parent == null)
			return;

		//One item can't move against itself.
		int count = parent.getChildCount();
		if(count == 1)
			return;

		//Get the index of the item to be moved.
		int selectedIndex = parent.getChildIndex(selectedItem);
		if(selectedIndex == 0)
			return; //Can't move any further upwards.

		//move the item in the form object model.
		moveFormItemUp(selectedItem,parent);

		TreeItem currentItem; // = parent.getChild(index - 1);
		List list = new ArrayList();

		//First of all remove the item (to be moved) from the tree.
		selectedItem.remove();

		//Remove all items below the one to be moved, while storing them in a new list for latter adding.
		while(parent.getChildCount() >= selectedIndex){
			currentItem = parent.getChild(selectedIndex-1);
			list.add(currentItem);
			currentItem.remove();
		}

		//Now add the item that is to be moved, back to the tree.
		parent.addItem(selectedItem);

		//Add the items which were below the move item. (They were stored in a temporary list)
		for(int i=0; i<list.size(); i++)
			parent.addItem((TreeItem)list.get(i));

		tree.setSelectedItem(selectedItem);
		
		if(storeCommandHistory)
			Context.getCommandHistory().add(new MoveFieldCmd(selectedItem, true, this));
	}
	
	private void moveFormItemUp(TreeItem item,TreeItem parent){
		Object userObj = item.getUserObject();
		Object parentObj = parent.getUserObject();

		//Normal question
		if(userObj instanceof QuestionDef && parentObj instanceof PageDef)
			((PageDef)parentObj).moveQuestionUp((QuestionDef)userObj);
		else if(userObj instanceof QuestionDef && parentObj instanceof QuestionDef)
			((QuestionDef)parentObj).getGroupQtnsDef().moveQuestionUp((QuestionDef)userObj);
		else if(userObj instanceof PageDef)
			((FormDef)parentObj).movePageUp((PageDef)userObj);
		else if(userObj instanceof OptionDef)
			((QuestionDef)parentObj).moveOptionUp((OptionDef)userObj);
	}

	private void moveFormItemDown(TreeItem item,TreeItem parent){
		Object userObj = item.getUserObject();
		Object parentObj = parent.getUserObject();

		//Normal question
		if(userObj instanceof QuestionDef && parentObj instanceof PageDef)
			((PageDef)parentObj).moveQuestionDown((QuestionDef)userObj);
		else if(userObj instanceof QuestionDef && parentObj instanceof QuestionDef)
			((QuestionDef)parentObj).getGroupQtnsDef().moveQuestionDown((QuestionDef)userObj);
		else if(userObj instanceof PageDef)
			((FormDef)parentObj).movePageDown((PageDef)userObj);
		else if(userObj instanceof OptionDef)
			((QuestionDef)parentObj).moveOptionDown((OptionDef)userObj);
	}

	/**
	 * @see org.purc.purcforms.client.controller.IFormActionListener#moveItemDown()
	 */
	public void moveItemDown(){
		moveItemDown(true);
	}
	
	public void moveItemDown(boolean storeCommandHistory){
		if(inReadOnlyMode())
			return;

		TreeItem item = tree.getSelectedItem();

		//Check if there is any selection.
		if(item == null)
			return;

		TreeItem parent = item.getParentItem();

		//We don't move root node (which has no parent, that is the form itself, since we design one form at a time)
		if(parent == null)
			return;

		//One item can't move against itself.
		int count = parent.getChildCount();
		if(count == 1)
			return;

		int index = parent.getChildIndex(item);
		if(index == count - 1)
			return; //Can't move any further downwards.

		//move the item in the form object model.
		moveFormItemDown(item,parent);

		TreeItem currentItem; // = parent.getChild(index - 1);
		List list = new ArrayList();

		item.remove();

		while(parent.getChildCount() > 0 && parent.getChildCount() > index){
			currentItem = parent.getChild(index);
			list.add(currentItem);
			currentItem.remove();
		}

		for(int i=0; i<list.size(); i++){
			if(i == 1)
				parent.addItem(item); //Add after the first item.
			parent.addItem((TreeItem)list.get(i));
		}

		if(list.size() == 1)
			parent.addItem(item);

		tree.setSelectedItem(item);
		
		if(storeCommandHistory)
			Context.getCommandHistory().add(new MoveFieldCmd(item, false, this));
	}

	/**
	 * @see org.purc.purcforms.client.controller.IFormChangeListener#onFormItemChanged(java.lang.Object)
	 */
	public Object onFormItemChanged(Object formItem, byte property, String oldValue, boolean changeComplete) {
		
		//Do it here and using this.item because we may return with if(item.getUserObject() != formItem)
		if(changeComplete)
			Context.getCommandHistory().add(new ChangedFieldCmd(this.item, property, oldValue, this));
		
		TreeItem item = tree.getSelectedItem();
		if(item == null)
			return formItem; //How can this happen?

		if(item.getUserObject() != formItem)
			return formItem;

		updateTreeItemText(formItem, item);
		
		return formItem;
	}
	
	private void updateTreeItemText(Object formItem, TreeItem item){
		if(formItem instanceof QuestionDef){
			QuestionDef questionDef = (QuestionDef)formItem;
			item.setWidget(new TreeItemWidget(images.lookup(), questionDef.getDisplayText(),popup,this));
			item.setTitle(questionDef.getHelpText());
		}
		else if(formItem instanceof OptionDef){
			OptionDef optionDef = (OptionDef)formItem;
			item.setWidget(new TreeItemWidget(images.markRead(), optionDef.getText(),popup,this));
		}
		else if(formItem instanceof PageDef){
			PageDef pageDef = (PageDef)formItem;
			item.setWidget(new TreeItemWidget(images.drafts(), pageDef.getName(),popup,this));
		}
		else if(formItem instanceof FormDef){
			FormDef formDef = (FormDef)formItem;
			item.setWidget(new TreeItemWidget(images.note(), formDef.getName(),popup,this));
		}

		//After editing, the currently selected item is not highlighted and so this line fixes that problem.
		((CompositeTreeItem)tree.getSelectedItem()).addSelectionStyle();
	}

	/**
	 * @see org.purc.purcforms.client.controller.IFormChangeListener#onDeleteChildren(Object)
	 */
	public void onDeleteChildren(Object formItem){
		TreeItem item = tree.getSelectedItem();
		if(item == null)
			return; //How can this happen?

		if(formItem instanceof QuestionDef){
			while(item.getChildCount() > 0)
				deleteItem(item.getChild(0));
		}
	}

	/**
	 * @see org.purc.purcforms.client.controller.IFormActionListener#cutItem()
	 */
	public void cutItem(){
		if(inReadOnlyMode())
			return;

		TreeItem item = tree.getSelectedItem();
		if(item == null)
			return;

		clipboardItem = item.getUserObject();  

		inCutMode = true;
		deleteSelectedItem();
		inCutMode = false;
	}

	/**
	 * @see org.purc.purcforms.client.controller.IFormActionListener#copyItem()
	 */
	public void copyItem() {
		TreeItem item = tree.getSelectedItem();
		if(item == null)
			return;

		clipboardItem = item.getUserObject();
	}

	/**
	 * @see org.purc.purcforms.client.controller.IFormActionListener#pasteItem()
	 */
	public void pasteItem(){
		if(inReadOnlyMode())
			return;

		//Check if we have anything in the clipboard.
		if(clipboardItem == null)
			return;

		TreeItem item = tree.getSelectedItem();
		if(item == null)
			return;

		/*int selectedIndex = -1;
		if(item.getParentItem() != null)
			selectedIndex = item.getParentItem().getChildIndex(item);

		boolean moveItems = false;*/

		Object userObj = item.getUserObject();

		if(clipboardItem instanceof QuestionDef){
			//Questions can be pasted only as kids of pages or repeat questions, or just next to each other.
			if(! ( (userObj instanceof PageDef) || 
					(userObj instanceof QuestionDef /*&& 
							((QuestionDef)userObj).getDataType() == QuestionDef.QTN_TYPE_REPEAT)*/ ))){
				return;
			}

			//create a copy of the clipboard question.
			QuestionDef questionDef = new QuestionDef((QuestionDef)clipboardItem,userObj);

			questionDef.setId(item.getChildCount()+1);

			boolean pasteNextToQuestion = (userObj instanceof QuestionDef && !((QuestionDef)userObj).isGroupQtnsDef());

			item = loadQuestion(questionDef, (pasteNextToQuestion ? item : null), (pasteNextToQuestion ? item.getParentItem() : item));

			item.getParentItem().setState(true);
			item.setState(true);
			tree.setSelectedItem(item);

			//Repeat question can only be child of a page but not another question.
			//if(questionDef.getDataType() == QuestionDef.QTN_TYPE_REPEAT && userObj instanceof QuestionDef)
			//	return;

			if(userObj instanceof PageDef)
				((PageDef)userObj).addQuestion(questionDef);
			else if(pasteNextToQuestion){
				((PageDef)((QuestionDef)userObj).getParent()).addQuestion(questionDef, (QuestionDef)userObj);
				//moveItems = true;
			}
			else if(((QuestionDef)userObj).isGroupQtnsDef())
				((QuestionDef)userObj).getGroupQtnsDef().addQuestion(questionDef);
		}
		else if(clipboardItem instanceof PageDef){		
			//Pages can be pasted only as kids of forms, or just next to each other.
			if(!(userObj instanceof FormDef || userObj instanceof PageDef))
				return;

			//create a copy of the clipboard page.
			PageDef pageDef = new PageDef((PageDef)clipboardItem, userObj instanceof FormDef ? (FormDef)userObj : ((PageDef)userObj).getParent());

			pageDef.setPageNo(item.getChildCount()+1);

			boolean insertAfterPage = (userObj instanceof PageDef);

			item = loadPage(pageDef, (insertAfterPage ? item : null), (insertAfterPage ? item.getParentItem() : item));

			item.getParentItem().setState(true);
			item.setState(true);
			tree.setSelectedItem(item);

			if(userObj instanceof FormDef)
				((FormDef)userObj).addPage(pageDef);
			else{
				((PageDef)userObj).getParent().addPage(pageDef, (PageDef)userObj);
				//moveItems = true;
			}

		}
		else if(clipboardItem instanceof OptionDef){
			//Question options can be pasted only as kids of single and multi select questions, or just next to each other.
			if(!( (userObj instanceof QuestionDef 
					&& (((QuestionDef)userObj).getDataType() == QuestionDef.QTN_TYPE_LIST_EXCLUSIVE ||
							((QuestionDef)userObj).getDataType() == QuestionDef.QTN_TYPE_LIST_MULTIPLE)) ||
							userObj instanceof OptionDef) )
				return;

			//			create a copy of the clipboard page.
			OptionDef optionDef = new OptionDef((OptionDef)clipboardItem, userObj instanceof QuestionDef ? (QuestionDef)userObj : ((OptionDef)userObj).getParent());
			optionDef.setId(item.getChildCount()+1);

			item = addImageItem((userObj instanceof OptionDef) ? item : null, (userObj instanceof OptionDef) ? item.getParentItem() : item, optionDef.getText(), images.markRead(),optionDef,null, true);

			item.getParentItem().setState(true);
			item.setState(true);
			tree.setSelectedItem(item);

			if(userObj instanceof QuestionDef)
				((QuestionDef)userObj).addOption(optionDef);
			else{
				((OptionDef)userObj).getParent().addOption(optionDef, (OptionDef)userObj);
				//moveItems = true;
			}
		}

		//Move the newly added item just immediately below the item which was previously selected.
		/*if(moveItems){
			int addedIndex = item.getParentItem().getChildIndex(item);
			int count = addedIndex - selectedIndex - 1;
			for(int index = 0; index < count; index++)
				moveItemUp();
		}*/
	}

	/**
	 * @see org.purc.purcforms.client.controller.IFormDesignerListener#refresh(Object)
	 */
	public void refreshItem(){
		if(inReadOnlyMode())
			return;

		formDesignerListener.refresh(this);
	}
	
	public void addToDesignSurface() {
		if(inReadOnlyMode())
			return;
		
		TreeItem item = tree.getSelectedItem();

		//Check if there is any selection.
		if(item == null)
			return;

		TreeItem parent = item.getParentItem();

		//We don't deal with root node
		if(parent == null)
			return;
		
		formDesignerListener.addToDesignSurface(item.getUserObject());
	}

	/**
	 * @see org.purc.purcforms.client.controller.IFormDesignerListener#saveForm()
	 */
	public void saveItem(){
		formDesignerListener.saveForm();
	}

	/**
	 * Gets the selected form.
	 * 
	 * @return the selected form.
	 */
	public FormDef getSelectedForm(){
		TreeItem  item = tree.getSelectedItem();
		if(item != null)
			return getSelectedForm(item);
		return null;
	}

	/**
	 * Gets the form to which the selected tree item belongs.
	 * 
	 * @param item the tree item.
	 * @return the form definition object.
	 */
	private FormDef getSelectedForm(TreeItem item){
		Object obj = item.getUserObject();
		if(obj instanceof FormDef)
			return (FormDef)obj;
		return getSelectedForm(item.getParentItem());
	}

	private TreeItem getSelectedItemRoot(TreeItem item){
		if(item == null)
			return null;

		if(item.getParentItem() == null)
			return item;
		return getSelectedItemRoot(item.getParentItem());
	}

	/**
	 * Removes all forms.
	 */
	public void clear(){
		tree.clear();
	}

	/**
	 * Checks if the selected form is valid for saving.
	 * 
	 * @return true if valid, else false.
	 */
	public boolean isValidForm(){
		TreeItem  parent = getSelectedItemRoot(tree.getSelectedItem());
		if(parent == null)
			return true;

		Map<String,String> pageNos = new HashMap<String,String>();
		Map<String,QuestionDef> bindings = new HashMap<String,QuestionDef>();
		int count = parent.getChildCount();
		for(int index = 0; index < count; index++){
			TreeItem child = parent.getChild(index);
			PageDef pageDef = (PageDef)child.getUserObject();
			String pageNo = String.valueOf(pageDef.getPageNo());
			if(pageNos.containsKey(pageNo)){
				tree.setSelectedItem(child);
				tree.ensureSelectedItemVisible();
				Window.alert(LocaleText.get("selectedPage") + pageDef.getName() +LocaleText.get("shouldNotSharePageBinding") + pageNos.get(pageNo)+ "]");
				return false;
			}
			else
				pageNos.put(pageNo, pageDef.getName());

			if(!isValidQuestionList(child,bindings))
				return false;
		}

		return true;
	}

	private boolean isValidQuestionList(TreeItem  parent,Map<String,QuestionDef> bindings){
		int count = parent.getChildCount();
		for(int index = 0; index < count; index++){
			TreeItem child = parent.getChild(index);
			QuestionDef questionDef = (QuestionDef)child.getUserObject();
			String variableName = questionDef.getBinding();
			if(bindings.containsKey(variableName) /*&& questionDef.getParent() == bindings.get(variableName).getParent()*/){
				tree.setSelectedItem(child);
				tree.ensureSelectedItemVisible();
				Window.alert(LocaleText.get("selectedQuestion") + questionDef.getText()+LocaleText.get("shouldNotShareQuestionBinding") + bindings.get(variableName).getDisplayText()+ "]");
				return false;
			}
			else
				bindings.put(variableName, questionDef);

			if(questionDef.isGroupQtnsDef()){
				if(!isValidQuestionList(child,bindings))
					return false;
			}
			else if(questionDef.getDataType() == QuestionDef.QTN_TYPE_LIST_EXCLUSIVE ||
					questionDef.getDataType() == QuestionDef.QTN_TYPE_LIST_MULTIPLE){
				if(!isValidOptionList(child))
					return false;
			}
		}

		return true;
	}

	private boolean isValidOptionList(TreeItem  parent){
		Map<String,String> bindings = new HashMap<String,String>();

		int count = parent.getChildCount();
		for(int index = 0; index < count; index++){
			TreeItem child = parent.getChild(index);
			OptionDef optionDef = (OptionDef)child.getUserObject();
			String variableName = optionDef.getBinding();
			if(bindings.containsKey(variableName)){
				tree.setSelectedItem(child);
				tree.ensureSelectedItemVisible();
				Window.alert(LocaleText.get("selectedOption") + optionDef.getText()+LocaleText.get("shouldNotShareOptionBinding") + bindings.get(variableName)+ "]");
				return false;
			}
			else
				bindings.put(variableName, optionDef.getText());
		}
		return true;
	}

	/**
	 * @see org.purc.purcforms.client.controller.IFormActionListener#moveUp()
	 */
	public void moveUp(){
		TreeItem item = tree.getSelectedItem();
		if(item == null)
			return;

		int index;
		TreeItem parent = item.getParentItem();
		if(parent == null){
			index = getRootItemIndex(parent);
			if(index == 0)
				return;
			tree.setSelectedItem(tree.getItem(index - 1));
		}
		else{
			index = parent.getChildIndex(item);
			if(index == 0)
				return;
			tree.setSelectedItem(parent.getChild(index - 1));
		}
	}

	/**
	 * @see org.purc.purcforms.client.controller.IFormActionListener#moveDown()
	 */
	public void moveDown(){
		TreeItem item = tree.getSelectedItem();
		if(item == null)
			return;

		int index;
		TreeItem parent = item.getParentItem();
		if(parent == null){
			index = getRootItemIndex(parent);
			if(index == tree.getItemCount() - 1)
				return;
			tree.setSelectedItem(tree.getItem(index + 1));
		}
		else{
			index = parent.getChildIndex(item);
			if(index == parent.getChildCount() - 1)
				return;
			tree.setSelectedItem(parent.getChild(index + 1));
		}
	}


	/**
	 * Selected the parent of the selected item.
	 */
	public void moveToParent(){
		TreeItem item = tree.getSelectedItem();
		if(item == null)
			return;

		TreeItem parent = item.getParentItem();
		if(parent == null)
			return;

		tree.setSelectedItem(parent);
		tree.ensureSelectedItemVisible();
	}


	/**
	 * Selects the child of the selected item.
	 */
	public void moveToChild(){
		TreeItem item = tree.getSelectedItem();
		if(item == null)
			return;

		if(item.getChildCount() == 0){
			addNewChildItem(false);
			return;
		}

		TreeItem child = item.getChild(0);
		tree.setSelectedItem(child);
		tree.ensureSelectedItemVisible();
	}


	/**
	 * Checks if the selected form is in read only mode. In read only mode
	 * we can only change the text and help text of items.
	 * 
	 * @return true if in read only mode, else false.
	 */
	private boolean inReadOnlyMode(){
		return Context.isStructureReadOnly();
	}

	private int getNextQuestionPos(Object parentObj){
		//TODO Not all users want to be forced to change repeat bindings,
		//especially those just trying out the form designer. So left to
		//only those who rebuild bindings.
		if(FormUtil.rebuildBindings()){
			if(parentObj instanceof QuestionDef){
				QuestionDef parentQuestionDef = (QuestionDef)parentObj;
				if(parentQuestionDef != null && parentQuestionDef.isGroupQtnsDef())
					return parentQuestionDef.getId() + parentQuestionDef.getGroupQtnsDef().getQuestionsCount() + 1;
			}
			
			return formDef.getQuestionCount() + 1;
		}

		return nextQuestionId;
	}

	private int getNextOptionPos(Object questionDef){		
		return ((QuestionDef)questionDef).getOptionCount() + 1;
	}

	public void rebuildBindings(){
		if(formDef == null)
			return;

		//If we do not first save the form when the user has added new questions,
		//we get a problems where data nodes are not created for the new questions
		//and some of the new questions get lost.
		saveItem();

		DeferredCommand.addCommand(new Command(){
			public void execute() {
				rebuildTheBindings();
			}
		});
	}
	
	private void rebuildTheBindings(){
		FormUtil.dlg.setText(LocaleText.get("loading"));
		FormUtil.dlg.center();

		DeferredCommand.addCommand(new Command(){
			public void execute() {
				try{
					for(int index = 0; index < formDef.getPageCount(); index++)
						rebuildPageBindings(index + 1, formDef.getPageAt(index));

					TreeItem item = tree.getSelectedItem();
					if(item != null)
						fireFormItemSelected(item.getUserObject(), item);

					FormUtil.dlg.hide();
				}
				catch(Exception ex){
					FormUtil.displayException(ex);
				}	
			}
		});
	}

	private void rebuildPageBindings(int pageNo, PageDef pageDef){
		for(int index = 0; index < pageDef.getQuestionCount(); index++)
			rebuildQuestionBindings(index + 1, pageDef.getQuestionAt(index));
	}

	private void rebuildQuestionBindings(int questionNo, QuestionDef questionDef){

		questionDef.setBinding(FormDesignerUtil.getQtnBinding(questionDef.getId(), questionNo));

		int dataType = questionDef.getDataType();
		if(dataType == QuestionDef.QTN_TYPE_LIST_EXCLUSIVE || dataType == QuestionDef.QTN_TYPE_LIST_MULTIPLE){
			for(int index = 0; index < questionDef.getOptionCount(); index++){
				OptionDef optionDef = questionDef.getOptionAt(index);
				optionDef.setBinding(FormDesignerUtil.getOptnBinding(optionDef.getId(), index + 1));
			}
		}
		else if(questionDef.isGroupQtnsDef()){
			GroupQtnsDef groupQtnsDef = questionDef.getGroupQtnsDef();
			if(groupQtnsDef == null)
				return;

			for(int index = 0; index < groupQtnsDef.getQuestionsCount(); index++)
				rebuildQuestionBindings(Integer.parseInt(questionDef.getBinding().substring(8)) + index + 1, groupQtnsDef.getQuestionAt(index));
		}
	}
	
	public void setSelectedItem(TreeItem item){
		tree.setSelectedItem(item);
		tree.ensureSelectedItemVisible();
		selectItem(item, false);
		updateTreeItemText(item.getUserObject(), item);
	}
	
	public void addRootItem(TreeItem item){
		tree.addItem(item);
	}
}