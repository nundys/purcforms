package org.purc.purcforms.client.widget;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.purc.purcforms.client.controller.QuestionChangeListener;
import org.purc.purcforms.client.locale.LocaleText;
import org.purc.purcforms.client.model.FormDef;
import org.purc.purcforms.client.model.OptionDef;
import org.purc.purcforms.client.model.QuestionDef;
import org.purc.purcforms.client.model.ValidationRule;
import org.purc.purcforms.client.util.FormUtil;

import com.google.gwt.event.dom.client.BlurEvent;
import com.google.gwt.event.dom.client.BlurHandler;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.FocusEvent;
import com.google.gwt.event.dom.client.FocusHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.event.dom.client.KeyUpHandler;
import com.google.gwt.event.logical.shared.SelectionEvent;
import com.google.gwt.event.logical.shared.SelectionHandler;
import com.google.gwt.resources.client.ImageResource;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.DeferredCommand;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Hyperlink;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.MultiWordSuggestOracle;
import com.google.gwt.user.client.ui.RadioButton;
import com.google.gwt.user.client.ui.SuggestBox;
import com.google.gwt.user.client.ui.TabBar;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.TextBoxBase;
import com.google.gwt.user.client.ui.Widget;


/**
 * Wraps a widget and gives it capability to be used at run time for data collection.
 * 
 * @author daniel
 *
 */
public class RuntimeWidgetWrapper extends WidgetEx implements QuestionChangeListener{

	/** Widget to display error message icon when the widget's validation fails. */
	protected Image errorImage;

	/** Collection of RadioButton and CheckBox wrapped widgets for a given question. */
	protected List<RuntimeWidgetWrapper> childWidgets;

	/** Listener to edit events. */
	protected EditListener editListener;
	
	/** Listener to widget events like hinding showing, etc. */
	protected WidgetListener widgetListener;
	
	protected EnabledChangeListener enabledListener;

	private ImageResource errorImageProto;

	/** Flag that tells whether this widget is locked and hence doesn't allow editing. */
	private boolean locked = false;

	/** Used when form is first loaded and one needs to click the edit button. */
	private boolean readOnly = false;
	
	/** The widget's validation rule. */
	private ValidationRule validationRule;

	/**
	 * Creates a copy of the widget.
	 * 
	 * @param widget the widget to copy.
	 */
	public RuntimeWidgetWrapper(RuntimeWidgetWrapper widget){
		super(widget);

		editListener = widget.getEditListener();
		errorImage = FormUtil.createImage(widget.getErrorImage());
		errorImageProto = widget.getErrorImage();
		errorImage.setTitle(LocaleText.get("requiredErrorMsg"));

		if(widget.getValidationRule() != null)
			validationRule = new ValidationRule(widget.getValidationRule());

		panel.add(this.widget);
		initWidget(panel);
		setupEventListeners();

		if(widget.questionDef != null){ //TODO For long list of options may need to share list
			//If we have a validation rule, then it already has a question copy
			//which we should use if validations are to fire expecially for repeats
			if(validationRule != null)
				questionDef = validationRule.getFormDef().getQuestion(widget.questionDef.getId());
			else
				questionDef = new QuestionDef(widget.questionDef,widget.questionDef.getParent());
		}
	}

	public RuntimeWidgetWrapper(Widget widget, ImageResource errorImageProto, EditListener editListener, WidgetListener widgetListener, EnabledChangeListener enabledListener){
		this.widget = widget;

		if(!(widget instanceof TabBar)){
			this.errorImageProto = errorImageProto;
			this.errorImage = FormUtil.createImage(errorImageProto);
			this.editListener = editListener;
			this.widgetListener = widgetListener;
			this.enabledListener = enabledListener;

			panel.add(widget);
			initWidget(panel);
			setupEventListeners();
			errorImage.setTitle(LocaleText.get("requiredErrorMsg"));

			DOM.sinkEvents(getElement(),DOM.getEventsSunk(getElement()) | Event.MOUSEEVENTS /*| Event.ONCONTEXTMENU | Event.KEYEVENTS*/);
		}
	}

	public ImageResource getErrorImage(){
		return errorImageProto;
	}

	/**
	 * Gets the question edit listener.
	 * 
	 * @return the edit listener.
	 */
	public EditListener getEditListener(){
		return editListener;
	}

	@Override
	public void onBrowserEvent(Event event) {
		if(locked || readOnly){
			event.preventDefault();
			event.stopPropagation();
		}

		/*if(widget instanceof RadioButton && DOM.eventGetType(event) == Event.ONMOUSEUP){
			if(((RadioButton)widget).getValue() == true){
				event.stopPropagation();
				event.preventDefault();
				((RadioButton)widget).setValue(false);
				return;
			}
		}*/
	}

	/**
	 * Sets up events listeners.
	 */
	private void setupEventListeners(){
		if(widget instanceof DatePickerEx){
			((DatePickerEx)widget).addBlurHandler(new BlurHandler(){
				public void onBlur(BlurEvent event){
					//This line is commented out because it makes focus stay on date widget
					//and never move, even after trying to tab away (on Chrome and Safari)
					//((DatePickerEx)widget).selectAll();
				}
			});
		}

		if(widget instanceof TextBoxBase)
			setupTextBoxEventListeners();
		else if(widget instanceof DateTimeWidget)
			setupDateTimeEventListeners();
		else if(widget instanceof CheckBox){
			((CheckBox)widget).addKeyDownHandler(new KeyDownHandler(){
				public void onKeyDown(KeyDownEvent event) {
					int keyCode = event.getNativeKeyCode();
					if((keyCode == KeyCodes.KEY_ENTER && !event.isShiftKeyDown()) || keyCode == KeyCodes.KEY_DOWN
							|| keyCode == KeyCodes.KEY_RIGHT)
						editListener.onMoveToNextWidget((RuntimeWidgetWrapper)panel.getParent());
					else if((keyCode == KeyCodes.KEY_ENTER && event.isShiftKeyDown()) || keyCode == KeyCodes.KEY_UP || keyCode == KeyCodes.KEY_LEFT)
						editListener.onMoveToPrevWidget((RuntimeWidgetWrapper)panel.getParent());
				}
			}); 
		}
		else if(widget instanceof ListBox){
			((ListBox)widget).addChangeHandler(new ChangeHandler(){
				public void onChange(ChangeEvent event){
					onListBoxChange(((ListBox)widget).getSelectedIndex());
				}
			});

			((ListBox)widget).addKeyDownHandler(new KeyDownHandler(){
				public void onKeyDown(KeyDownEvent event) {
					int keyCode = event.getNativeKeyCode();
					if((keyCode == KeyCodes.KEY_ENTER && !event.isShiftKeyDown()) || keyCode == KeyCodes.KEY_RIGHT)
						editListener.onMoveToNextWidget((RuntimeWidgetWrapper)panel.getParent());
					else if((keyCode == KeyCodes.KEY_ENTER && event.isShiftKeyDown()) || keyCode == KeyCodes.KEY_LEFT)
						editListener.onMoveToPrevWidget((RuntimeWidgetWrapper)panel.getParent());
					/*else if(keyCode == KeyCodes.KEY_UP || keyCode == KeyCodes.KEY_DOWN){
						//This is put such that we can detect list box changes immediately on moving the
						//up and down arrow keys contrary to the browser's default implementation which 
						//would fire the change event only when one moves focus away from the listbox.
						//This is sometimes the desired behavior but not always.
						ListBox listBox = (ListBox)widget;
						int index = listBox.getSelectedIndex();
						if(keyCode == KeyCodes.KEY_UP){
							if(index > 0)
								onListBoxChange(index - 1);
						}
						else if(keyCode == KeyCodes.KEY_DOWN){
							if(index < listBox.getItemCount() - 1)
								onListBoxChange(index + 1);
						}
					}*/
					else{
						DeferredCommand.addCommand(new Command() {
							public void execute() {
								//This makes the listbox fire change events immediately such that keyboard selection
								//either using up and down arrow keys or typing the first character of a
								//select option does the same as a mouse click without having to wait for the user
								//to tab away.
								((ListBox)widget).setFocus(false);
								((ListBox)widget).setFocus(true);
							}
						});	
					}
				}//TODO Do we really wanna alter the behaviour of the arrow keys for list boxes?
			});
		}

		DOM.sinkEvents(getElement(),DOM.getEventsSunk(getElement()) | Event.MOUSEEVENTS | Event.ONCONTEXTMENU | Event.KEYEVENTS);
	}

	private void onListBoxChange(int index){
		questionDef.setAnswer(((ListBox)widget).getValue(index));
		isValid(false);
		editListener.onValueChanged((RuntimeWidgetWrapper)panel.getParent());
	}

	public void addSuggestBoxChangeEvent(){
		if(widget instanceof TextBox){
			((TextBox)widget).addChangeHandler(new ChangeHandler(){
				public void onChange(ChangeEvent event){
					onSuggestBoxChange();
				}
			});
		}
	}

	private void onSuggestBoxChange(){
		if(questionDef != null){
			OptionDef optionDef = questionDef.getOptionWithText(getTextBoxAnswer());
			if(optionDef != null)
				questionDef.setAnswer(optionDef.getBinding());
			else{
				questionDef.setAnswer(null);
				setText(null);
			}
			isValid(false);
			editListener.onValueChanged((RuntimeWidgetWrapper)panel.getParent());
		}
		else
			((TextBox)widget).setText(null);
	}

	/**
	 * Sets up text box event listeners.
	 */
	private void setupTextBoxEventListeners(){
		if(widget.getParent() instanceof SuggestBox){
			if(widget.getParent() instanceof SuggestBox){
				((SuggestBox)widget.getParent()).addSelectionHandler(new SelectionHandler(){
					public void onSelection(SelectionEvent event){
						onSuggestBoxChange();
					}
				});
			}

			((TextBoxBase)widget).addClickHandler(new ClickHandler(){
				public void onClick(ClickEvent event){
					((TextBoxBase)widget).selectAll();
				}
			});

			((TextBoxBase)widget).addFocusHandler(new FocusHandler(){
				public void onFocus(FocusEvent event){
					((TextBoxBase)widget).selectAll();
				}
			});

			addSuggestBoxChangeEvent();
		}
		else{
			((TextBoxBase)widget).addChangeHandler(new ChangeHandler(){
				public void onChange(ChangeEvent event){
					//questionDef.setAnswer(((TextBoxBase)widget).getText());
					if(questionDef != null){
						questionDef.setAnswer(getTextBoxAnswer());
						isValid(false);
						editListener.onValueChanged((RuntimeWidgetWrapper)panel.getParent());
					}

					if(widget instanceof DatePickerWidget)
						editListener.onMoveToNextWidget((RuntimeWidgetWrapper)panel.getParent());

					//Window.alert(widget.getElement().getId());
					//Window.alert(FormUtil.getElementValue(DOM.getElementById(widget.getElement().getId())));

					/*if("question1".equals(widget.getElement().getId())){
						DateTimeFormat format = FormUtil.getDateDisplayFormat();
						String value1 = format.format(new Date());
						String value2 = FormUtil.getElementValue(DOM.getElementById(widget.getElement().getId()));
						FormUtil.setElementValue(DOM.getElementById("question5"), value1, value2);
					}*/
				}
			});
		}

		((TextBoxBase)widget).addKeyUpHandler(new KeyUpHandler(){
			public void onKeyUp(KeyUpEvent event) {
				if(event.getNativeKeyCode() == KeyCodes.KEY_TAB)
					return;

				if(questionDef != null && !(questionDef.getDataType() == QuestionDef.QTN_TYPE_LIST_EXCLUSIVE || questionDef.getDataType() == QuestionDef.QTN_TYPE_LIST_EXCLUSIVE_DYNAMIC)){
					questionDef.setAnswer(getTextBoxAnswer());

					isValid(false);

					editListener.onValueChanged((RuntimeWidgetWrapper)panel.getParent());
				}
			}
		});

		((TextBoxBase)widget).addKeyDownHandler(new KeyDownHandler(){
			public void onKeyDown(KeyDownEvent event) {
				int keyCode = event.getNativeKeyCode();
				if((keyCode == KeyCodes.KEY_ENTER && !event.isShiftKeyDown()) || keyCode == KeyCodes.KEY_DOWN)
					editListener.onMoveToNextWidget((RuntimeWidgetWrapper)panel.getParent());
				else if((keyCode == KeyCodes.KEY_ENTER && event.isShiftKeyDown()) || keyCode == KeyCodes.KEY_UP)
					editListener.onMoveToPrevWidget((RuntimeWidgetWrapper)panel.getParent());
			}
		});

		((TextBoxBase)widget).addKeyPressHandler(new KeyPressHandler(){
			public void onKeyPress(KeyPressEvent event) {
				int keyCode = event.getCharCode();
				if((externalSource != null && externalSource.trim().length() > 0) && 
						(displayField == null || displayField.trim().length() == 0) &&
						(valueField == null || valueField.trim().length() == 0) ){

					if(keyCode == KeyCodes.KEY_TAB || keyCode == KeyCodes.KEY_ENTER || isDateOrTimeExternalSource()){
						//editListener.onMoveToNextWidget((RuntimeWidgetWrapper)panel.getParent());
						return;
					}

					((TextBoxBase) event.getSource()).cancelKey();
					
					//Remove error icon.
					while(panel.getWidgetCount() > 1)
						panel.remove(1);

					if(keyCode == (char) KeyCodes.KEY_DELETE || keyCode == (char) KeyCodes.KEY_BACKSPACE){
						((TextBoxBase) event.getSource()).setText("");
						if(questionDef != null)
							questionDef.setAnswer(null);

						return;
					}
				
					Label label = new Label("");
					label.setVisible(false);
					panel.add(label);
					FormUtil.searchExternal(externalSource,String.valueOf(event.getCharCode()), widget.getElement(), label.getElement(), widget.getElement(),filterField);
				}
			}
		});
		
		((TextBoxBase)widget).addClickHandler(new ClickHandler(){
			public void onClick(ClickEvent event) {
				if((externalSource != null && externalSource.trim().length() > 0) && 
						(displayField == null || displayField.trim().length() == 0) &&
						(valueField == null || valueField.trim().length() == 0) ){

					//We want external source display on click for only date/time widgets.
					if (!isDateOrTimeExternalSource())
						return;
					
					//Remove error icon.
					while(panel.getWidgetCount() > 1)
						panel.remove(1);
				
					Label label = new Label("");
					label.setVisible(false);
					panel.add(label);
					FormUtil.searchExternal(externalSource, "", widget.getElement(), label.getElement(), widget.getElement(), filterField);
				}
			}
		});
	}


	/**
	 * Sets up date time event listeners.
	 */
	private void setupDateTimeEventListeners(){
		((DateTimeWidget)widget).addChangeHandler(new ChangeHandler(){
			public void onChange(ChangeEvent event){
				//questionDef.setAnswer(((TextBox)widget).getText());
				if(questionDef != null){
					questionDef.setAnswer(getTextBoxAnswer());
					isValid(false);
					editListener.onValueChanged((RuntimeWidgetWrapper)panel.getParent());
				}

				if(widget instanceof DatePickerWidget)
					editListener.onMoveToNextWidget((RuntimeWidgetWrapper)panel.getParent());
			}
		});

		((DateTimeWidget)widget).addKeyUpHandler(new KeyUpHandler(){
			public void onKeyUp(KeyUpEvent event) {
				if(questionDef != null && !(questionDef.getDataType() == QuestionDef.QTN_TYPE_LIST_EXCLUSIVE || questionDef.getDataType() == QuestionDef.QTN_TYPE_LIST_EXCLUSIVE_DYNAMIC)){
					questionDef.setAnswer(getTextBoxAnswer());

					isValid(false);

					editListener.onValueChanged((RuntimeWidgetWrapper)panel.getParent());
				}
			}
		});
	}


	/**
	 * Sets the question for the widget.
	 * 
	 * @param questionDef the question definition object.
	 * @param loadWidget set to true to load widget values, else false.
	 */
	public void setQuestionDef(QuestionDef questionDef ,boolean loadWidget){
		this.questionDef = questionDef;

		if(loadWidget)
			loadQuestion();
	}

	/**
	 * Loads values for the widget.
	 */
	public void loadQuestion(){
		if(questionDef == null)
			return;

		//questionDef.clearChangeListeners(); Removed from here because we want to allow more that one widget listen on the same question.
		questionDef.addChangeListener(this);
		questionDef.setAnswer(questionDef.getDefaultValueSubmit());

		String defaultValue = questionDef.getDefaultValue();

		int type = questionDef.getDataType();
		if((type == QuestionDef.QTN_TYPE_LIST_EXCLUSIVE || type == QuestionDef.QTN_TYPE_LIST_EXCLUSIVE_DYNAMIC
				|| type == QuestionDef.QTN_TYPE_LIST_MULTIPLE)
				&& widget instanceof ListBox){
			List options  = questionDef.getOptions();
			int defaultValueIndex = 0;
			ListBox listBox = (ListBox)widget;
			listBox.clear(); //Could be called more than once.

			listBox.addItem("","");
			if(options != null){
				for(int index = 0; index < options.size(); index++){
					OptionDef optionDef = (OptionDef)options.get(index);
					listBox.addItem(optionDef.getText(), optionDef.getBinding());
					if(optionDef.getBinding().equalsIgnoreCase(defaultValue))
						defaultValueIndex = index+1;
				}
			}
			listBox.setSelectedIndex(defaultValueIndex);
		}
		else if((type == QuestionDef.QTN_TYPE_LIST_EXCLUSIVE || type == QuestionDef.QTN_TYPE_LIST_EXCLUSIVE_DYNAMIC) && widget instanceof TextBox){ 
			MultiWordSuggestOracle oracle = new MultiWordSuggestOracle();
			FormUtil.loadOptions(questionDef.getOptions(),oracle);
			if(widget.getParent() != null){
				RuntimeWidgetWrapper copyWidget = new RuntimeWidgetWrapper(this);
				panel.remove(widget.getParent());
				panel.remove(widget);

				copyWidgetProperties(copyWidget);
				setWidth(getWidth());
				setHeight(getHeight());
			}

			SuggestBox sgstBox = new SuggestBox(oracle,(TextBox)widget);
			panel.add(sgstBox);
			sgstBox.setTabIndex(((TextBox)widget).getTabIndex());
			setupTextBoxEventListeners();
		}
		else if(type == QuestionDef.QTN_TYPE_BOOLEAN && widget instanceof ListBox){
			ListBox listBox = (ListBox)widget;
			listBox.addItem("","");
			listBox.addItem(QuestionDef.TRUE_DISPLAY_VALUE, QuestionDef.TRUE_VALUE);
			listBox.addItem(QuestionDef.FALSE_DISPLAY_VALUE ,QuestionDef.FALSE_VALUE);
			listBox.setSelectedIndex(0);

			if(defaultValue != null){
				if(defaultValue.trim().equalsIgnoreCase(QuestionDef.TRUE_VALUE))
					listBox.setSelectedIndex(1);
				else if(defaultValue.trim().equalsIgnoreCase(QuestionDef.FALSE_VALUE))
					listBox.setSelectedIndex(2);
			}
		}
		else if(type == QuestionDef.QTN_TYPE_LIST_MULTIPLE && defaultValue != null 
				&& defaultValue.trim().length() > 0&& binding != null 
				&& binding.trim().length() > 0 && widget instanceof CheckBox){
			if(defaultValue.contains(binding))
				((CheckBox)widget).setValue(true);
		}
		else if(type == QuestionDef.QTN_TYPE_DATE_TIME && widget instanceof DateTimeWidget){
			if(defaultValue != null && defaultValue.trim().length() > 0 && questionDef.isDate()){
				if(QuestionDef.isDateFunction(defaultValue))
					defaultValue = questionDef.getDefaultValueDisplay();
				else
					defaultValue = fromSubmit2DisplayDate(defaultValue);

				((DateTimeWidget)widget).setText(defaultValue);
			}
		}
		else if(type == QuestionDef.QTN_TYPE_BOOLEAN && defaultValue != null 
				&& defaultValue.trim().length() > 0 && binding != null 
				&& binding.trim().length() > 0 && widget instanceof CheckBox){
			
			if(defaultValue.trim().equalsIgnoreCase(QuestionDef.TRUE_VALUE))
				((CheckBox)widget).setValue(true);
		}


		if(widget instanceof TextBoxBase){
			((TextBoxBase)widget).setText(""); //first init just incase we have default value

			if(defaultValue != null && defaultValue.trim().length() > 0){
				if(type == QuestionDef.QTN_TYPE_LIST_EXCLUSIVE){
					OptionDef optionDef = questionDef.getOptionWithValue(defaultValue);
					if(optionDef != null)
						((TextBoxBase)widget).setText(optionDef.getText());
				}
				else{
					if(defaultValue.trim().length() > 0 && questionDef.isDate() && questionDef.isDateFunction(defaultValue))
						defaultValue = questionDef.getDefaultValueDisplay();
					else if(defaultValue.trim().length() > 0 && questionDef.isDate())
						defaultValue = fromSubmit2DisplayDate(defaultValue);

					if(defaultValue != null && type == QuestionDef.QTN_TYPE_NUMERIC){
						int pos = defaultValue.indexOf('.');
						if(pos > 0)
							defaultValue = defaultValue.substring(0, pos);
					}

					if(defaultValue != null && questionDef.getDataType() == QuestionDef.QTN_TYPE_DECIMAL)
						defaultValue = defaultValue.replace(FormUtil.SAVE_DECIMAL_SEPARATOR, FormUtil.getDecimalSeparator());	

					if(defaultValue != null)
						defaultValue = defaultValue.trim();
					
					((TextBoxBase)widget).setText(defaultValue);

					setExternalSourceDisplayValue();
				}
			}
		}

		if(questionDef.getDataType() == QuestionDef.QTN_TYPE_REPEAT)
			questionDef.setAnswer("0");

		//TODO Looks like this should be at the end after all widgets are loaded
		//isValid();

		if(!questionDef.isEnabled())
			setEnabled(false);
		if(!questionDef.isVisible())
			setVisible(false);
		if(questionDef.isLocked())
			setLocked(true);
	}


	public void setExternalSourceDisplayValue(){
		if(externalSource == null || externalSource.trim().length() == 0)
			return;

		if(questionDef == null || questionDef.getDataNode() == null)
			return;

		if(!(widget instanceof TextBox))
			return;

		String defaultValue = questionDef.getDefaultValue();
		if(defaultValue == null || defaultValue.trim().length() == 0)
			return;

		String displayValue = questionDef.getDataNode().getAttribute("displayValue");
		if(displayValue != null){
			while(panel.getWidgetCount() > 1)
				panel.remove(1);

			Label label = new Label(defaultValue);
			label.setVisible(false);
			panel.add(label);

			((TextBox)widget).setText(displayValue);

			//Used only once on form loading.
			questionDef.getDataNode().removeAttribute("displayValue");
		}
	}


	public void setAnswer(String answer){
		questionDef.setAnswer(answer);

		int type = questionDef.getDataType();

		if((type == QuestionDef.QTN_TYPE_LIST_EXCLUSIVE || type == QuestionDef.QTN_TYPE_LIST_EXCLUSIVE_DYNAMIC
				|| type == QuestionDef.QTN_TYPE_LIST_MULTIPLE)
				&& widget instanceof ListBox){
			List options  = questionDef.getOptions();
			int defaultValueIndex = 0;
			ListBox listBox = (ListBox)widget;

			if(options != null){
				for(int index = 0; index < options.size(); index++){
					OptionDef optionDef = (OptionDef)options.get(index);
					if(optionDef.getBinding().equalsIgnoreCase(answer))
						defaultValueIndex = index+1;
				}
			}
			listBox.setSelectedIndex(defaultValueIndex);
		}
		else if(type == QuestionDef.QTN_TYPE_BOOLEAN && widget instanceof ListBox){
			ListBox listBox = (ListBox)widget;
			if(answer != null){
				if(answer.equalsIgnoreCase(QuestionDef.TRUE_VALUE))
					listBox.setSelectedIndex(1);
				else if(answer.equalsIgnoreCase(QuestionDef.FALSE_VALUE))
					listBox.setSelectedIndex(2);
			}
		}
		else if(type == QuestionDef.QTN_TYPE_LIST_MULTIPLE && answer != null 
				&& answer.trim().length() > 0&& binding != null 
				&& binding.trim().length() > 0 && widget instanceof CheckBox){
			if(answer.contains(binding))
				((CheckBox)widget).setValue(true);
		}
		else if(type == QuestionDef.QTN_TYPE_DATE_TIME && widget instanceof DateTimeWidget)
			((DateTimeWidget)widget).setText(answer);


		if(widget instanceof TextBoxBase){
			((TextBoxBase)widget).setText(""); //first init just incase we have default value

			if(answer != null && answer.trim().length() > 0){
				if(type == QuestionDef.QTN_TYPE_LIST_EXCLUSIVE){
					OptionDef optionDef = questionDef.getOptionWithValue(answer);
					if(optionDef != null)
						((TextBoxBase)widget).setText(optionDef.getText());
				}
				else{
					if(answer.trim().length() > 0 && questionDef.isDate() && questionDef.isDateFunction(answer))
						answer = questionDef.getDefaultValueDisplay();
					else if(answer.trim().length() > 0 && questionDef.isDate())
						answer = fromSubmit2DisplayDate(answer);

					((TextBoxBase)widget).setText(answer);
				}
			}
		}
	}


	/**
	 * Converts a date,time or dateTime from its xml submit format to display format.
	 * 
	 * @param value the text value in submit format.
	 * @return the value in its display format.
	 */
	private String fromSubmit2DisplayDate(String value){
		try{
			if(questionDef.getDataType() == QuestionDef.QTN_TYPE_TIME)
				return FormUtil.getTimeDisplayFormat().format(FormUtil.getTimeSubmitFormat().parse(value));
			else if(questionDef.getDataType() == QuestionDef.QTN_TYPE_DATE_TIME)
				return FormUtil.getDateTimeDisplayFormat().format(FormUtil.getDateTimeSubmitFormat().parse(value));
			else
				return FormUtil.getDateDisplayFormat().format(FormUtil.getDateSubmitFormat().parse(value));
		}catch(Exception ex){}
		return null;
	}

	/**
	 * Sets whether this widget is enabled.
	 * 
	 * @param enabled <code>true</code> to enable the widget, <code>false</code>
	 *        to disable it.
	 */
	public void setEnabled(boolean enabled){
		if(widget instanceof RadioButton){
			((RadioButton)widget).setEnabled(enabled);
			if(!enabled)
				((RadioButton)widget).setValue(false);
		}
		else if(widget instanceof CheckBox){
			((CheckBox)widget).setEnabled(enabled);
			if(!enabled)
				((CheckBox)widget).setValue(false);
		}
		else if(widget instanceof Button)
			((Button)widget).setEnabled(enabled);
		else if(widget instanceof ListBox){
			((ListBox)widget).setEnabled(enabled);
			if(!enabled)
				((ListBox)widget).setSelectedIndex(0);
		}
		else if(widget instanceof TextArea){
			((TextArea)widget).setEnabled(enabled);
			if(!enabled)
				((TextArea)widget).setText(null);
		}
		else if(widget instanceof TextBox){
			((TextBox)widget).setEnabled(enabled);
			if(!enabled)
				((TextBox)widget).setText(null);
		}
		else if(widget instanceof DateTimeWidget){
			((DateTimeWidget)widget).setEnabled(enabled);
			if(!enabled)
				((DateTimeWidget)widget).setText(null);
		}
		else if(widget instanceof RuntimeGroupWidget)
			((RuntimeGroupWidget)widget).setEnabled(enabled);
	}

	/**
	 * Determines if to allow editing of the widget value.
	 * 
	 * @param locked set to true to prevent editing of the widget value.
	 */
	public void setLocked(boolean locked){
		this.locked = locked;

		//Give a visual clue that this widget is locked.
		DOM.setStyleAttribute(widget.getElement(), "opacity", locked ? "0.6" : "100");

		if(widget instanceof RuntimeGroupWidget)
			((RuntimeGroupWidget)widget).setLocked(locked);
	}
	
	public void setReadOnly(boolean readOnly){
		this.readOnly = readOnly;

		if(widget instanceof RuntimeGroupWidget)
			((RuntimeGroupWidget)widget).setReadOnly(readOnly);
		else if(widget instanceof Image || widget instanceof HTML) {
			Widget wgt = widget.getParent().getParent().getParent().getParent();
			if(wgt instanceof RuntimeGroupWidget)
				((RuntimeGroupWidget)wgt).setReadOnlyEx(readOnly);
		}
	}

	/**
	 * Checks if this widget does not allow changing of its value.
	 * 
	 * @return true if it does not allow, else false.
	 */
	public boolean isLocked(){
		return locked;
	}
	
	public boolean isReadOnly() {
		return readOnly;
	}

	/**
	 * Gets the user answer from a TextBoxBase widget.
	 * 
	 * @return the text answer.
	 */
	private String getTextBoxAnswer(){
		String value = null;
		if(widget instanceof TextBoxBase)
			value = ((TextBoxBase)widget).getText();
		else if(widget instanceof TimeWidget)
			value = ((TimeWidget)widget).getText();
		else if(widget instanceof DateTimeWidget)
			value = ((DateTimeWidget)widget).getText();

		try{
			if(questionDef.isDate() && value != null && value.trim().length() > 0){
				if(questionDef.getDataType() == QuestionDef.QTN_TYPE_TIME){
					value = FormUtil.getTimeSubmitFormat().format(FormUtil.getTimeDisplayFormat().parse(value));

					// ISO 8601 requires a colon in time zone offset (Java doesn't
					// include the colon, so we need to insert it
					if("yyyy-MM-dd'T'HH:mm:ssZ".equals(FormUtil.getTimeSubmitFormat().getPattern()))
						value = value.substring(0, 22) + ":" + value.substring(22);
				}
				else if(questionDef.getDataType() == QuestionDef.QTN_TYPE_DATE_TIME){
					value = FormUtil.getDateTimeSubmitFormat().format(FormUtil.getDateTimeDisplayFormat().parse(value));

					// ISO 8601 requires a colon in time zone offset (Java doesn't
					// include the colon, so we need to insert it
					if("yyyy-MM-dd'T'HH:mm:ssZ".equals(FormUtil.getDateTimeSubmitFormat().getPattern()))
						value = value.substring(0, 22) + ":" + value.substring(22);
				}
				else 
					value = FormUtil.getDateSubmitFormat().format(FormUtil.getDateDisplayFormat().parse(value));
			}
		}
		catch(Exception ex){
			//If we get a problem parsing date, just return null.
			value = null;

			if(panel.getWidgetCount() < 2)
				panel.add(errorImage);

			String format = FormUtil.getDateDisplayFormat().getPattern();
			if(questionDef.getDataType() == QuestionDef.QTN_TYPE_TIME)
				format = FormUtil.getTimeDisplayFormat().getPattern();
			else if(questionDef.getDataType() == QuestionDef.QTN_TYPE_DATE_TIME)
				format = FormUtil.getDateTimeDisplayFormat().getPattern();

			errorImage.setTitle(LocaleText.get("wrongFormat") + " " + format);
		}

		return value;
	}

	/**
	 * Retrieves the value from the widget to the question definition object for this widget.
	 * 
	 * @param formDef the form to which this widget's question belongs.
	 */
	public void saveValue(FormDef formDef){
		if(questionDef == null){
			if(widget instanceof RuntimeGroupWidget)
				((RuntimeGroupWidget)widget).saveValue(formDef);

			return;
		}

		//These are not used for filling any answers. HTML is used for audio and video
		if((widget instanceof Label || widget instanceof Button) && !(widget instanceof HTML))
			return;

		String defaultValue = questionDef.getDefaultValueSubmit();

		if(widget instanceof TextBox && questionDef.getDataType() == QuestionDef.QTN_TYPE_LIST_EXCLUSIVE){
			OptionDef optionDef = questionDef.getOptionWithText(((TextBox)widget).getText());
			if(optionDef != null)
				questionDef.setAnswer(optionDef.getBinding());
			else {
				questionDef.setAnswer(null);
				
				if(externalSource != null && externalSource.trim().length() > 0 ){
					String answer = getTextBoxAnswer();
					if(panel.getWidgetCount() > 1 && answer != null && answer.trim().length() > 0){
						Widget wid = panel.getWidget(1);
						if(wid instanceof Label){
							answer = ((Label)wid).getText();
							optionDef = questionDef.getOptionWithValue(answer);
							if(optionDef != null)
								questionDef.setAnswer(optionDef.getBinding());
						}
					}
				}
			}

			//Fire fox clears default values when the widget is disabled. So put it as the answer manually.
			if(defaultValue != null && defaultValue.trim().length() > 0 && !((TextBox)widget).isEnabled()){
				if(questionDef.getAnswer() == null || questionDef.getAnswer().trim().length() == 0)
					questionDef.setAnswer(defaultValue);
			}
		}
		else if(widget instanceof TextBox){
			String answer = getTextBoxAnswer();

			if(externalSource != null && externalSource.trim().length() > 0 /*&&
					questionDef.getDataType() == QuestionDef.QTN_TYPE_NUMERIC*/){ //the internal save (non display) value needs to also work for non numerics.
				//answer = null; //TODO This seems to cause some bugs where numeric questions seem un answered. 

				if(panel.getWidgetCount() > 1 && answer != null && answer.trim().length() > 0){
					Widget wid = panel.getWidget(1);
					if(wid instanceof Label){
						String ans = ((Label)wid).getText();
						if (ans != null && ans.trim().length() > 0)
							answer = ans;
					}
				}
			}

			questionDef.setAnswer(answer);

			//Fire fox clears default values when the widget is disabled. So put it as the answer manually.
			if(defaultValue != null && defaultValue.trim().length() > 0 && !((TextBox)widget).isEnabled()){
				if(questionDef.getAnswer() == null || questionDef.getAnswer().trim().length() == 0)
					questionDef.setAnswer(defaultValue);
			}
		}
		else if(widget instanceof TextArea){
			questionDef.setAnswer(((TextArea)widget).getText());

			//Fire fox clears default values when the widget is disabled. So put it as the answer manually.
			if(defaultValue != null && defaultValue.trim().length() > 0 && !((TextArea)widget).isEnabled()){
				if(questionDef.getAnswer() == null || questionDef.getAnswer().trim().length() == 0)
					questionDef.setAnswer(defaultValue);
			}
		}
		else if(widget instanceof ListBox){
			if(questionDef.getDataType() == QuestionDef.QTN_TYPE_LIST_EXCLUSIVE ||
					questionDef.getDataType() == QuestionDef.QTN_TYPE_BOOLEAN){
				String value = null;
				ListBox lb = (ListBox)widget;
				if(lb.getSelectedIndex() >= 0)
					value = lb.getValue(lb.getSelectedIndex());
				questionDef.setAnswer(value);
			}

			//Fire fox clears default values when the widget is disabled. So put it as the answer manually.
			if(defaultValue != null && defaultValue.trim().length() > 0 && !((ListBox)widget).isEnabled()){
				if(questionDef.getAnswer() == null || questionDef.getAnswer().trim().length() == 0)
					questionDef.setAnswer(defaultValue);
			}
		}
		else if(widget instanceof RadioButton){ //Should be before CheckBox
			if(!(questionDef.getDataType() == QuestionDef.QTN_TYPE_LIST_EXCLUSIVE ||
					questionDef.getDataType() == QuestionDef.QTN_TYPE_BOOLEAN) || childWidgets == null)
				return;

			String value = null;

			if(questionDef.getDataType() == QuestionDef.QTN_TYPE_BOOLEAN)
				value = questionDef.getAnswer();
			else{
				for(int index=0; index < childWidgets.size(); index++){
					RuntimeWidgetWrapper childWidget = childWidgets.get(index);
					String binding = childWidget.getBinding();
					if(((RadioButton)((RuntimeWidgetWrapper)childWidget).getWrappedWidget()).getValue() == true && binding != null){
						value = binding;
						break;
					}
				}
			}

			questionDef.setAnswer(value);
		}
		else if(widget instanceof CheckBox){
			if(childWidgets == null || !(questionDef.getDataType() == QuestionDef.QTN_TYPE_LIST_MULTIPLE ||
					questionDef.getDataType() == QuestionDef.QTN_TYPE_BOOLEAN)){
				return;
			}

			String value = "";
			if(questionDef.getDataType() == QuestionDef.QTN_TYPE_BOOLEAN){
				RuntimeWidgetWrapper childWidget = childWidgets.get(0);
				String binding = childWidget.getBinding();
				if(binding != null)
					value = (((CheckBox)((RuntimeWidgetWrapper)childWidget).getWrappedWidget()).getValue() == true) ? QuestionDef.TRUE_VALUE : QuestionDef.FALSE_VALUE;
			}
			else{
				for(int index=0; index < childWidgets.size(); index++){
					RuntimeWidgetWrapper childWidget = childWidgets.get(index);
					String binding = childWidget.getBinding();
					if(((CheckBox)((RuntimeWidgetWrapper)childWidget).getWrappedWidget()).getValue() == true && binding != null){
						if(value.length() != 0)
							value += " ";
						value += binding;
					}
				}
			}

			questionDef.setAnswer(value);
		}
		else if(widget instanceof DateTimeWidget){
			questionDef.setAnswer(getTextBoxAnswer());

			//Fire fox clears default values when the widget is disabled. So put it as the answer manually.
			if(defaultValue != null && defaultValue.trim().length() > 0 && !((DateTimeWidget)widget).isEnabled()){
				if(questionDef.getAnswer() == null || questionDef.getAnswer().trim().length() == 0)
					questionDef.setAnswer(defaultValue);
			}
			else if("endtime".equalsIgnoreCase(questionDef.getBinding())) //Add time when form filling ended.
				questionDef.setAnswer(FormUtil.getDateTimeSubmitFormat().format(new Date()));
		}
		else if(widget instanceof RuntimeGroupWidget)
			((RuntimeGroupWidget)widget).saveValue(formDef);

		//Repeat widgets have a value for row count which does not go anywhere in the model
		if(!(widget instanceof RuntimeGroupWidget))
			questionDef.updateNodeValue(formDef);
	}
	
	private boolean isDateOrTimeExternalSource() {
		return "date".equalsIgnoreCase(externalSource) || 
				"datetime".equalsIgnoreCase(externalSource) ||
				"time".equalsIgnoreCase(externalSource);
	}

	/**
	 * Adds a CheckBox or RadioButton widget for the question of this widget.
	 * 
	 * @param childWidget the CheckBox or RadioButton widget.
	 */
	public void addChildWidget(final RuntimeWidgetWrapper childWidget){
		if(childWidgets == null)
			childWidgets = new ArrayList<RuntimeWidgetWrapper>();
		childWidgets.add(childWidget);

		String defaultValue = questionDef.getDefaultValue();
		int type = questionDef.getDataType();
		if((type == QuestionDef.QTN_TYPE_LIST_EXCLUSIVE ||
				type == QuestionDef.QTN_TYPE_LIST_MULTIPLE)
				&& widget instanceof CheckBox && defaultValue != null){ 
			if(childWidgets.size() == questionDef.getOptions().size()){
				for(int index=0; index < childWidgets.size(); index++){
					RuntimeWidgetWrapper kidWidget = childWidgets.get(index);
					if((type == QuestionDef.QTN_TYPE_LIST_MULTIPLE && defaultValue.contains(kidWidget.getBinding())) ||
							(type == QuestionDef.QTN_TYPE_LIST_EXCLUSIVE && defaultValue.equals(kidWidget.getBinding()))){

						((CheckBox)((RuntimeWidgetWrapper)kidWidget).getWrappedWidget()).setValue(true);
						if(type == QuestionDef.QTN_TYPE_LIST_EXCLUSIVE)
							break; //for this we can't select more than one.
					}
				}
			}
		}

		((CheckBox)childWidget.getWrappedWidget()).addClickHandler(new ClickHandler(){
			public void onClick(ClickEvent event){
				if(questionDef.getDataType() == QuestionDef.QTN_TYPE_LIST_EXCLUSIVE){
					if(((CheckBox)event.getSource()).getValue() == true)
						questionDef.setAnswer(((RuntimeWidgetWrapper)((Widget)event.getSource()).getParent().getParent()).getBinding());
					else
						questionDef.setAnswer(null);
				}
				else{
					String answer = "";
					for(int index=0; index < childWidgets.size(); index++){
						RuntimeWidgetWrapper childWidget = childWidgets.get(index);
						String binding = childWidget.getBinding();
						if(((CheckBox)((RuntimeWidgetWrapper)childWidget).getWrappedWidget()).getValue() == true && binding != null){
							if(answer.length() > 0)
								answer += " , ";
							answer += binding;
						}
					}
					questionDef.setAnswer(answer);
				}
				isValid(false);
				editListener.onValueChanged((RuntimeWidgetWrapper)panel.getParent());
			}
		});


		//As for now, am not yet sure why below is what i need to turn off
		//radio button selections using space bar.
		if(childWidget.getWrappedWidget() instanceof RadioButton){
			((RadioButton)childWidget.getWrappedWidget()).addKeyUpHandler(new KeyUpHandler(){
				public void onKeyUp(KeyUpEvent event) {
					if(event.getNativeKeyCode() == 32)
						((RadioButton)childWidget.getWrappedWidget()).setValue(false);
				}
			});
		}
	}

	/**
	 * Get's the widget that is wrapped by this widget.
	 */
	public Widget getWrappedWidget(){
		return widget;
	}


	//These taken from question data.
	public boolean isValid(boolean fireValueChanged){
		if(widget instanceof Label || widget instanceof Button || questionDef == null || 
				(widget instanceof CheckBox && childWidgets == null)){

			if(widget instanceof RuntimeGroupWidget)
				return ((RuntimeGroupWidget)widget).isValid(fireValueChanged);

			return true;
		}

		boolean answered = this.isAnswered();
		if(questionDef.getDataType() == QuestionDef.QTN_TYPE_REPEAT)
			answered = (questionDef.getAnswer() != null && !questionDef.getAnswer().equals("0"));
		
		if(questionDef.isRequired() && !answered){
			
			//Clear the value widget, if any, for external source widgets.
			if(externalSource != null && externalSource.trim().length() > 0){
				while(panel.getWidgetCount() > 1)
					panel.remove(1);
			}
			
			if(panel.getWidgetCount() < 2)
				panel.add(errorImage);

			errorImage.setTitle(LocaleText.get("requiredErrorMsg"));
			return false;
		}

		if(questionDef.getDataType() == QuestionDef.QTN_TYPE_REPEAT){
			boolean valid = false;
			if((widget instanceof RuntimeGroupWidget))
				valid = ((RuntimeGroupWidget)widget).isValid(fireValueChanged);
			if(!valid)
				return false;
		}

		//For some reason the validation rule object, before saving, has a formdef different
		//from the one we are using and hence need to update it
		if(validationRule != null){
			FormDef formDef = questionDef.getParentFormDef();
			if(formDef != validationRule.getFormDef())
				validationRule.setFormDef(formDef);
		}

		if(validationRule != null){
			if(!validationRule.isValid()){
				if(panel.getWidgetCount() < 2)
					panel.add(errorImage);
	
				errorImage.setTitle(validationRule.getErrorMessage());
				
				if(widget instanceof RuntimeGroupWidget)
					((RuntimeGroupWidget)widget).onValidationFailed(validationRule);
				
				return false;
			}
			else{
				if(widget instanceof RuntimeGroupWidget)
					((RuntimeGroupWidget)widget).onValidationPassed(validationRule);
			}
				
		}
		/*FormDef formDef = null;
		ValidationRule rule = new ValidationRule();
		if(!rule.isValid(formDef)){

		}*/

		//Date, Time & DateTime parse text input and give an answer of null if the entered
		//value is not valid and hence we need to show the error flag.
		if((widget instanceof TextBoxBase && questionDef.getAnswer() == null && ((TextBoxBase)widget).getText().trim().length() > 0) ||
				(widget instanceof TimeWidget && questionDef.getAnswer() == null && ((TimeWidget)widget).getText().trim().length() > 0) ||
				(widget instanceof DateTimeWidget && questionDef.getAnswer() == null && ((DateTimeWidget)widget).getText().trim().length() > 0)){

			if(panel.getWidgetCount() < 2)
				panel.add(errorImage);
			return false;
		}

		if(panel.getWidgetCount() > 1)
			panel.remove(errorImage);
		return true;
	}

	/**
	 * Check if the question represented by this widget has been answered.
	 * 
	 * @return true if answered, else false.
	 */
	public boolean isAnswered(){
		if(externalSource != null && externalSource.trim().length() > 0 ){
			if(panel.getWidgetCount() > 1){
				Widget wid = panel.getWidget(1);
				if(wid instanceof Label){
					questionDef.setAnswer(((Label)wid).getText());
				}
			}
		}
		
		return getAnswer() != null && getAnswer().toString().trim().length() > 0;
	}

	/**
	 * Gets the answer for the question wrapped by the widget.
	 * 
	 * @return the answer.
	 */
	private Object getAnswer() {
		if(questionDef == null)
			return null;

		return questionDef.getAnswer();
	}

	/**
	 * Sets input focus to the widget.
	 * 
	 * @return true if the widget accepts input focus.
	 */
	public boolean setFocus(){
		if(questionDef != null && (!questionDef.isVisible() || !questionDef.isEnabled() || questionDef.isLocked() || readOnly))
			return false;

		//Browser does not seem to set focus to check boxes and radio buttons

		if(widget instanceof RadioButton)
			((RadioButton)widget).setFocus(true);
		else if(widget instanceof CheckBox)
			((CheckBox)widget).setFocus(true);
		else if(widget instanceof ListBox)
			((ListBox)widget).setFocus(true);
		else if(widget instanceof TextBoxBase){
			((TextBoxBase)widget).setFocus(true);
			((TextBoxBase)widget).selectAll();
			if(panel.getWidget(0) instanceof TextBoxBase){
				((TextBoxBase)panel.getWidget(0)).setFocus(true);
			}
		}
		else if(widget instanceof Button)
			((Button)widget).setFocus(true);
		else if(widget instanceof DateTimeWidget)
			((DateTimeWidget)widget).setFocus(true);
		else if(widget instanceof RuntimeGroupWidget)
			return ((RuntimeGroupWidget)widget).setFocus();
		else
			return false;
		
		return true;
	}

	/**
	 * Clears the value or answer entered for this widget.
	 */
	public void clearValue(){
		if(widget instanceof RadioButton)
			((RadioButton)widget).setValue(false);
		else if(widget instanceof CheckBox)
			((CheckBox)widget).setValue(false);
		else if(widget instanceof ListBox)
			((ListBox)widget).setSelectedIndex(-1);
		else if(widget instanceof TextArea)
			((TextArea)widget).setText(null);
		else if(widget instanceof TextBox)
			((TextBox)widget).setText(null);
		else if(widget instanceof DatePickerEx)
			((DatePickerEx)widget).setText(null);
		else if(widget instanceof DateTimeWidget)
			((DateTimeWidget)widget).setText(null);
		else if(widget instanceof Image)
			((Image)widget).setUrl(null);
		else if(widget instanceof RuntimeGroupWidget)
			((RuntimeGroupWidget)widget).clearValue();

		if(questionDef != null){
			questionDef.setAnswer(null);

			//Clear value for external source widgets.
			//if(panel.getWidgetCount() == 2)
			while(panel.getWidgetCount() > 1)
				panel.remove(1);
		}
	}

	/**
	 * @see org.purc.purcforms.client.controller.QuestionChangeListener#onEnabledChanged(QuestionDef, boolean)
	 */
	public void onEnabledChanged(QuestionDef sender,boolean enabled) {
		if(!enabled)
			clearValue();

		setEnabled(enabled);
		
		if(enabledListener != null)
			enabledListener.onEnabledChanged(this, enabled);
	}

	/**
	 * @see org.purc.purcforms.client.controller.QuestionChangeListener#onLockedChanged(QuestionDef, boolean)
	 */
	public void onLockedChanged(QuestionDef sender,boolean locked) {
		if(locked)
			clearValue();

		setLocked(locked);
	}
	
	/**
	 * @see org.purc.purcforms.client.controller.QuestionChangeListener#onReadOnlyChanged(QuestionDef, boolean)
	 */
	public void onReadOnlyChanged(QuestionDef sender, boolean readOnly) {
		setReadOnly(readOnly);
	}

	/**
	 * @see org.purc.purcforms.client.controller.QuestionChangeListener#onRequiredChanged(QuestionDef, boolean)
	 */
	public void onRequiredChanged(QuestionDef sender,boolean required) {
		//As for now we do not set error messages on labels.
		if(!(widget instanceof Label)){
			if(!required && panel.getWidgetCount() > 1)
				panel.remove(errorImage);
			else if(required && panel.getWidgetCount() < 2 && !isAnswered())
				panel.add(errorImage);
		}
	}

	/**
	 * @see org.purc.purcforms.client.controller.QuestionChangeListener#onVisibleChanged(QuestionDef, boolean)
	 */
	public void onVisibleChanged(QuestionDef sender,boolean visible) {
		if(!visible)
			clearValue();

		setVisible(visible);
		
		if(widgetListener != null && isFocusable()){
			if(visible)
				widgetListener.onWidgetShown(this, getHeightInt());
			else
				widgetListener.onWidgetHidden(this, getHeightInt());
		}
	}

	/**
	 * @see org.purc.purcforms.client.controller.QuestionChangeListener#onBindingChanged(QuestionDef, String)
	 */
	public void onBindingChanged(QuestionDef sender,String newValue){
		if(newValue != null && newValue.trim().length() > 0)
			binding = newValue;
	}

	/**
	 * Gets the question wrapped by this widget.
	 * 
	 * @return the question definition object.
	 */
	public QuestionDef getQuestionDef(){
		return questionDef;
	}

	/**
	 * @see org.purc.purcforms.client.controller.QuestionChangeListener#onDataTypeChanged(QuestionDef, int)
	 */
	public void onDataTypeChanged(QuestionDef sender,int dataType){

	}

	/**
	 * Gets this widget's validation rule.
	 *
	 * @return the widget's validation rule.
	 */
	public ValidationRule getValidationRule() {
		return validationRule;
	}

	/**
	 * Sets the widget's validation rule.
	 * 
	 * @param validationRule the validation rule.
	 */
	public void setValidationRule(ValidationRule validationRule) {
		this.validationRule = validationRule;
	}

	/**
	 * @see org.purc.purcforms.client.controller.QuestionChangeListener#onOptionsChanged(QuestionDef, List)
	 */
	public void onOptionsChanged(QuestionDef sender,List<OptionDef> optionList){
		loadQuestion();
	}

	public RuntimeWidgetWrapper getInvalidWidget(){
		if(widget instanceof RuntimeGroupWidget)
			return ((RuntimeGroupWidget)widget).getInvalidWidget();
		return this;
	}

	/**
	 * Checks if this widget accepts input focus.
	 * 
	 * @return true if the widget accepts input focus, else false.
	 */
	public boolean isFocusable(){
		Widget wg = getWrappedWidget();
		return (wg instanceof TextBox || wg instanceof TextArea || wg instanceof DatePickerEx ||
				wg instanceof CheckBox || wg instanceof RadioButton || 
				wg instanceof RuntimeGroupWidget || wg instanceof ListBox
				|| wg instanceof DateTimeWidget);
	}


	public void moveToNextWidget(){
		editListener.onMoveToNextWidget((RuntimeWidgetWrapper)panel.getParent());
	}

	public void setBinding(String binding){
		super.setBinding(binding);
		if(getId() == null || getId().trim().length() == 0)
			setId();
	}

	public void setParentBinding(String parentBinding){
		super.setParentBinding(parentBinding);
		setId();
	}

	private void setId(){
		if(!(widget instanceof TextBoxBase))
			return;

		String id = "";

		if(binding != null)
			id += binding;

		if(parentBinding != null)
			id += parentBinding;

		if(id.trim().length() > 0)
			widget.getElement().setId(id);
	}

	public boolean isEditable(){
		return (widget instanceof TextBox || widget instanceof TextArea || widget instanceof ListBox || 
				widget instanceof CheckBox || widget instanceof DateTimeWidget || widget instanceof TimeWidget);
	}

	public void setId(String id){
		super.setId(id);
		widget.getElement().setId(id);
	}
}