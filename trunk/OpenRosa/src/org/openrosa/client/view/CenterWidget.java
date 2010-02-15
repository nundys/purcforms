package org.openrosa.client.view;

import org.purc.purcforms.client.PurcConstants;
import org.purc.purcforms.client.util.FormUtil;

import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.DecoratedTabPanel;
import com.google.gwt.user.client.ui.Label;


/**
 * 
 * @author daniel
 *
 */
public class CenterWidget extends Composite {

	/**
	 * Tab widget housing the contents.
	 */
	private DecoratedTabPanel tabs = new DecoratedTabPanel();
	
	private XformsTabWidget xformsWidget = new XformsTabWidget();
	private DesignTabWidget designWidget = new DesignTabWidget();
	
	
	public CenterWidget() {		
		initDesignTab();
		initXformsTab();
		initItextTab();

		FormUtil.maximizeWidget(tabs);

		tabs.selectTab(0);
		initWidget(tabs);
		//tabs.addSelectionHandler(this);
		
		FormUtil.maximizeWidget(this);
	}
	
	private void initDesignTab(){
		tabs.add(designWidget, "Design");
		//FormUtil.maximizeWidget(txtLayoutXml);
	}
	
	private void initXformsTab(){
		tabs.add(xformsWidget, "Xforms");
		//FormUtil.maximizeWidget(txtLayoutXml);
	}
	
	private void initItextTab(){
		tabs.add(new Label(), "Internationalization");
		//FormUtil.maximizeWidget(txtLayoutXml);
	}
	
	public void onWindowResized(int width, int height){
		int shortcutHeight = height - getAbsoluteTop();
		if(shortcutHeight > 50)
			xformsWidget.adjustHeight(shortcutHeight-130 + PurcConstants.UNITS);
		
		designWidget.onWindowResized(width, height);
	}
}
