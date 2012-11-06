package org.purc.purcforms.client.widget.grid;

import org.purc.purcforms.client.widget.DesignWidgetWrapper;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Element;
import com.google.gwt.user.client.ui.AbsolutePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.user.client.ui.WidgetCollection;

public class GridPanel extends AbsolutePanel {

	private WidgetCollection verticalLines = new WidgetCollection(this);
	private WidgetCollection horizontalLines = new WidgetCollection(this);

	@Override
	public void add(Widget w) {
		if(isGridLine(w)) {
			 // Detach new child.
			w.removeFromParent();

		    // Logical attach.
			if(isVerticalLine(w))
				verticalLines.add(w);
			else
				horizontalLines.add(w);

		    // Physical attach.
		    DOM.appendChild(getElement(), w.getElement());

		    // Adopt.
		    adopt(w);
		}
		else {
			super.add(w);
		}
	}
	
	@Override
	public boolean remove(Widget w) {
		if(isGridLine(w)) {
			boolean removed = removeWidget(w);
		    if (removed) {
		      changeToStaticPositioning(w.getElement());
		    }
		    return removed;
		}
		else {
			return super.remove(w);
		}
	}
	
	public boolean removeWidget(Widget w) {
	    // Validate.
	    if (w.getParent() != this) {
	      return false;
	    }
	    // Orphan.
	    try {
	      orphan(w);
	    } finally {
	      // Physical detach.
	      Element elem = w.getElement();
	      DOM.removeChild(DOM.getParent(elem), elem);
	  
	      // Logical detach.
	      if(isVerticalLine(w)) {
	    	  verticalLines.remove(w);
	      }
	      else
	    	  horizontalLines.remove(w);
	    }
	    return true;
	}
	
	private static void changeToStaticPositioning(Element elem) {
	    DOM.setStyleAttribute(elem, "left", "");
	    DOM.setStyleAttribute(elem, "top", "");
	    DOM.setStyleAttribute(elem, "position", "");
	}
	
	public void add(Widget w, int left, int top) {
		if(isGridLine(w)) {
			// In order to avoid the potential for a flicker effect, it is necessary
		    // to set the position of the widget before adding it to the AbsolutePanel.
		    // The Widget should be removed from its parent before any positional
		    // changes are made to prevent flickering.
		    w.removeFromParent();
		    int beforeIndex = 0;
		    if(isVerticalLine(w))
		    	beforeIndex = verticalLines.size();
		    else
		    	beforeIndex = horizontalLines.size();
		    
		    String cursor = DOM.getStyleAttribute(((DesignWidgetWrapper)w).getWrappedWidget().getElement(), "cursor");
		    if("e-resize".equals(cursor) || "w-resize".equals(cursor) || "n-resize".equals(cursor) || "s-resize".equals(cursor))
		    	setWidgetPositionImpl(w, left, top);
		    else
		    	super.setWidgetPositionImpl(w, left, top);
		    
		    insert(w, beforeIndex);
		    verifyPositionNotStatic(w);
		}
		else
			super.add(w, left, top);
	}
	
	protected void insert(Widget child, Element container, int beforeIndex,
		      boolean domInsert) {
		if(!isGridLine(child)){
			super.insert(child, container, beforeIndex, domInsert);
		}
		else {
		    // Validate index; adjust if the widget is already a child of this panel.
		    //beforeIndex = adjustIndex(child, beforeIndex);
			// Check to see if this widget is already a direct child.
		    if (child.getParent() == this) {
		      // If the Widget's previous position was left of the desired new position
		      // shift the desired position left to reflect the removal
		      int idx = isVerticalLine(child) ? verticalLines.indexOf(child) : horizontalLines.indexOf(child);
		      if (idx < beforeIndex) {
		        beforeIndex--;
		      }
		    }
	
		    // Detach new child.
		    child.removeFromParent();
	
		    // Logical attach.
	    	if(isVerticalLine(child)) {
	    		verticalLines.insert(child, beforeIndex);
	    		resizeVerticalLineToFit((DesignWidgetWrapper)child);
	    	}
		    else
		    	horizontalLines.insert(child, beforeIndex);
	
		    // Physical attach.
		    if (domInsert) {
		      DOM.insertChild(container, child.getElement(), beforeIndex);
		    } else {
		      DOM.appendChild(container, child.getElement());
		    }
	
		    // Adopt.
		    adopt(child);
		}
	}
	
	private void verifyPositionNotStatic(Widget child) {
	    // Only verify widget position in Development Mode
	    if (GWT.isProdMode()) {
	      return;
	    }

	    // Non-visible or detached elements have no offsetParent
	    if (child.getElement().getOffsetParent() == null) {
	      return;
	    }
	    
	    // Check if offsetParent == parent
	    if (child.getElement().getOffsetParent() == getElement()) {
	      return;
	    }

	    /*
	     * When this AbsolutePanel is the document BODY, e.g. RootPanel.get(), then
	     * no explicit position:relative is needed as children are already
	     * positioned relative to their parent. For simplicity we test against
	     * parent, not offsetParent, since in IE6+IE7 (but not IE8+) standards mode,
	     * the offsetParent, for elements whose parent is the document BODY, is the
	     * HTML element, not the BODY element.
	     */
	    if ("body".equals(getElement().getNodeName().toLowerCase())) {
	      return;
	    }

	    /*
	     * Warn the developer, but allow the execution to continue in case legacy
	     * apps depend on broken CSS.
	     */
	    String className = getClass().getName();
	    GWT.log("Warning: " + className + " descendants will be incorrectly "
	        + "positioned, i.e. not relative to their parent element, when "
	        + "'position:static', which is the CSS default, is in effect. One "
	        + "possible fix is to call "
	        + "'panel.getElement().getStyle().setPosition(Position.RELATIVE)'.",
	        // Stack trace provides context for the developer
	        new IllegalStateException(className
	            + " is missing CSS 'position:{relative,absolute,fixed}'"));
	}
	
	public boolean isVerticalLine(Widget w){
		return ((DesignWidgetWrapper)w).getWrappedWidget() instanceof VerticalGridLine;
	}
	
	public boolean isGridLine(Widget w){
		return w instanceof DesignWidgetWrapper && ((DesignWidgetWrapper)w).getWrappedWidget() instanceof GridLine;
	}
	
	public void resizeVerticalLineToFit(DesignWidgetWrapper verticalLine) {
		int top = verticalLine.getTopInt();
		int height = verticalLine.getHeightInt();
		int bottom = top + height;
		
		int left = verticalLine.getLeftInt();
		
		int prevDifTop = top - 20; //TODO Need not hard code the header label height
		int prevDifBottom = getOffsetHeight() - bottom; // - 20;
		for(Widget w : horizontalLines) {
			
			int x = ((DesignWidgetWrapper)w).getLeftInt();
			if(!(left >= x && left <= x + ((DesignWidgetWrapper)w).getWidthInt()))
				continue; //horizontal line does not intersect us
			
			int t = ((DesignWidgetWrapper)w).getTopInt();
			
			//check if line before our top
			if(t <= top) {
				int dif = top - t;
				if(dif < prevDifTop) {
					prevDifTop = dif; //closest so far from top
				}
			}
			else if(t >= bottom) { //check if line after our bottom
				int dif = t - bottom;
				if(dif < prevDifBottom) {
					prevDifBottom = dif; //closest so far from bottom
				}
			}
		}
		
		top = top - prevDifTop;
		height = bottom + prevDifBottom - top;
		
		verticalLine.setTopInt(top);
		verticalLine.setHeight(height);
	}
	
	public void resizeHorizontalLineToFit(DesignWidgetWrapper horizontalLine) {
		int left = horizontalLine.getLeftInt();
		int width = horizontalLine.getWidthInt();
		int right = left + width;
		
		int top = horizontalLine.getTopInt();
		
		int prevDifLeft = left; //distance from table left
		int prevDifRight = getOffsetWidth() - right; //distance from table right
		for(Widget w : verticalLines) {
			
			int y = ((DesignWidgetWrapper)w).getTopInt();
			if(!(top >= y && top <= y + ((DesignWidgetWrapper)w).getHeightInt()))
				continue; //vertical line does not intersect us
			
			int l = ((DesignWidgetWrapper)w).getLeftInt();
			
			//check if line before our left
			if(l <= left) {
				int dif = left - l;
				if(dif < prevDifLeft) {
					prevDifLeft = dif; //closest so far from left
				}
			}
			else if(l >= right) { //check if line after our right
				int dif = l - right;
				if(dif < prevDifRight) {
					prevDifRight = dif; //closest so far from right
				}
			}
		}
		
		left = left - prevDifLeft;
		width = right + prevDifRight - left;
		
		horizontalLine.setLeftInt(left);
		horizontalLine.setWidthInt(width);
	}
	
	@Override
	protected void setWidgetPositionImpl(Widget w, int left, int top) {
		super.setWidgetPositionImpl(w, left, top);
		
		if(isGridLine(w)) {
			if(isVerticalLine(w))
				resizeVerticalLineToFit((DesignWidgetWrapper)w);
			else
				resizeHorizontalLineToFit((DesignWidgetWrapper)w);
		}
	}
	
	public void resizeGrid(int widthChange, int heightChange, int width, int height) {
		if(widthChange != 0) {
			for(Widget w : horizontalLines) {
				DesignWidgetWrapper widget = (DesignWidgetWrapper)w;
				int left = widget.getLeftInt();
				
				if(left > 0) {
					left = getNewResizeValue(left, widthChange, width);
					widget.setLeftInt(left);
				}
				
				int value = left + widget.getWidthInt();
				value = getNewResizeValue(value, widthChange, width);
				widget.setWidthInt(value - left);
			}
			
			for(Widget w : verticalLines) {
				((DesignWidgetWrapper)w).setLeftInt(getNewResizeValue(((DesignWidgetWrapper)w).getLeftInt(), widthChange, width));
			}
		}
		
		if(heightChange != 0) {
			int labelHeight = getLabelHeight();
			
			for(Widget w : verticalLines) {
				DesignWidgetWrapper widget = (DesignWidgetWrapper)w;
				int top = widget.getTopInt();
				
				if(top > labelHeight) {
					top = getNewResizeValue(top, heightChange, height);
					widget.setTopInt(top);
				}
				
				int value = top+ widget.getHeightInt();
				value = getNewResizeValue(value, heightChange, height);
				widget.setHeightInt(value - top);
			}
			
			for(Widget w : horizontalLines) {
				((DesignWidgetWrapper)w).setTopInt(getNewResizeValue(((DesignWidgetWrapper)w).getTopInt(), heightChange, height));
			}
		}
		
		for(Widget w : getChildren()) {
			DesignWidgetWrapper widget = (DesignWidgetWrapper)w;
			if(widget.getWidth().equals("100%"))
				continue; //header label widget
			
			if(widthChange != 0) {
				widget.setLeftInt(getNewResizeValue(widget.getLeftInt(), widthChange, width));
			}
			
			if(heightChange != 0) {
				widget.setTopInt(getNewResizeValue(widget.getTopInt(), heightChange, height));
			}
		}
	}
	
	private int getNewResizeValue(int value, int change, int newValue) {
		return (int)(value * ((double)newValue/(change + newValue)));
	}
	
	public void moveLine(int xChange, int yChange, int newLeft, int newTop){
		int oldX = xChange + newLeft;
		int oldY = yChange + newTop;
		if(xChange != 0 && xChange != -1){ //vertical line moved
			for(Widget w : horizontalLines) {
				DesignWidgetWrapper widget = (DesignWidgetWrapper)w;
				int left = widget.getLeftInt();
				if(left == oldX) {
					widget.setLeftInt(newLeft);
					widget.setWidthInt(widget.getWidthInt() + xChange);
				}
				else if((left + widget.getWidthInt()) == oldX) {
					widget.setWidthInt(widget.getWidthInt() - xChange);
				}
			}
			
			//Now move text after the moved line
			int nextLineX = getOffsetWidth();
			for(Widget w : verticalLines) {
				DesignWidgetWrapper widget = (DesignWidgetWrapper)w;
				int left = widget.getLeftInt();
				if(left > newLeft && left < nextLineX) {
					nextLineX = left;
				}
			}
			
			for(Widget w : getChildren()) {
				DesignWidgetWrapper widget = (DesignWidgetWrapper)w;
				if(widget.getWidth().equals("100%"))
					continue; //header label widget
				
				int left = widget.getLeftInt();
				if(left > oldX && left < nextLineX)
					widget.setLeftInt(left - xChange);
			}
		}
		else if(yChange != 0 && yChange != -1){ //horizontal line moved
			for(Widget w : verticalLines) {
				DesignWidgetWrapper widget = (DesignWidgetWrapper)w;
				int top = widget.getTopInt();
				if(top == oldY) {
					widget.setTopInt(newTop);
					widget.setHeightInt(widget.getHeightInt() + yChange);
				}
				else if((top + widget.getHeightInt()) == oldY)
					widget.setHeightInt(widget.getHeightInt() - yChange);
			}
			
			//Now move text after the moved line
			int nextLineY = getOffsetHeight();
			for(Widget w : horizontalLines) {
				DesignWidgetWrapper widget = (DesignWidgetWrapper)w;
				int top = widget.getTopInt();
				if(top > newTop && top < nextLineY) {
					nextLineY = top;
				}
			}

			for(Widget w : getChildren()) {
				DesignWidgetWrapper widget = (DesignWidgetWrapper)w;
				if(widget.getWidth().equals("100%"))
					continue; //header label widget
				
				int top = widget.getTopInt();
				if(top > oldY && top < nextLineY)
					widget.setTopInt(top - yChange);
			}
		}
	}
	
	public int getLabelHeight() {
		return ((DesignWidgetWrapper)this.getWidget(0)).getHeightInt();
	}
	
	public Widget getHorizontalWidget(int index) {
	    return horizontalLines.get(index);
	}

	public int getHorizontalWidgetCount() {
	    return horizontalLines.size();
	}
	
	public Widget getVerticalWidget(int index) {
	    return verticalLines.get(index);
	}

	public int getVerticalWidgetCount() {
	    return verticalLines.size();
	}
}
