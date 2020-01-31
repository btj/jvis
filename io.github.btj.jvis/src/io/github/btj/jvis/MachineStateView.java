package io.github.btj.jvis;

import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.IDebugEventSetListener;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.debug.core.model.IStackFrame;
import org.eclipse.debug.core.model.IThread;
import org.eclipse.debug.core.model.IValue;
import org.eclipse.debug.core.model.IVariable;
import org.eclipse.jdt.debug.core.IJavaArray;
import org.eclipse.jdt.debug.core.IJavaObject;
import org.eclipse.jdt.debug.core.IJavaReferenceType;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaThread;
import org.eclipse.jdt.debug.core.IJavaType;
import org.eclipse.jdt.debug.core.IJavaValue;
import org.eclipse.jdt.debug.core.IJavaVariable;
import org.eclipse.jface.resource.FontDescriptor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.MouseMoveListener;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Cursor;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Transform;
import org.eclipse.ui.part.ViewPart;

enum MouseEventType { MOVED, UP, DOUBLE_CLICKED }; 

class Element {
	Element parent;
	ArrayList<Element> children = new ArrayList<>();
	int x, y, width, height;
	Element mouseChild;
	boolean mouseInside;
	
	Element(Element parent) {
		if (parent != null)
			parent.add(this);
	}
	
	void mapPoint(Point point, Element ancestor) {
		Element e = this;
		while (e != ancestor) {
			point.x += e.x;
			point.y += e.y;
			e = e.parent;
		}
	}
	
	void remove(Element child) {
		if (child.parent != this) throw new AssertionError();
		if (mouseChild == child)
			setMouseChild(null);
		child.parent = null;
		children.remove(child);
	}
	
	void add(Element child) {
		if (child.parent != null)
			child.parent.remove(child);
		children.add(child);
		child.parent = this;
	}
	
	void paint(GC gc) {
		Transform transform = new Transform(gc.getDevice());
		gc.getTransform(transform);
		for (Element child : children) {
			transform.translate(child.x, child.y);
			gc.setTransform(transform);
			child.paint(gc);
			transform.translate(-child.x, -child.y);
			gc.setTransform(transform);
		}
		transform.dispose();
	}
	
	int getCursor(int x, int y) {
		return SWT.CURSOR_ARROW;
	}
	
	void mouseEntered() {}
	
	private void mouseExitedInternal() {
		if (mouseChild != null)
			setMouseChild(null);
		mouseInside = false;
		mouseExited();
	}
	
	void mouseExited() {}
	
	void setMouseChild(Element child) {
		if (child != mouseChild) {
			if (mouseChild != null)
				mouseChild.mouseExitedInternal();
			if (child != null) {
				child.mouseInside = true;
				child.mouseEntered();
			}
			mouseChild = child;
		}
	}
	
	boolean handleMouseEvent(MouseEventType type, MouseEvent e) {
		//System.out.println("Entering handleMouseEvent(" + type + ", (" + e.x + ", " + e.y + "))");
		for (Element child : children) {
			if (child.x <= e.x && e.x < child.x + child.width && child.y <= e.y && e.y < child.y + child.height) {
				e.x -= child.x;
				e.y -= child.y;
				//System.out.println("Entering child at (" + child.x + ", " + child.y + "), extent (" + child.width + ", " + child.height + ")");
				setMouseChild(child);
				boolean result = child.handleMouseEvent(type, e);
				//System.out.println("Leaving child");
				e.x += child.x;
				e.y += child.y;
				return result;
			} //else
				//System.out.println("Skipping child at (" + child.x + ", " + child.y + "), extent (" + child.width + ", " + child.height + ")");
		}
		setMouseChild(null);
		if (type == MouseEventType.MOVED) {
			((Canvas)e.widget).setCursor(((Canvas)e.widget).getDisplay().getSystemCursor(getCursor(e.x, e.y)));
			return true;
		}
		return false;
	}
}

class Arrow {
	int fromX, fromY;
	Element toElement;
	
	Arrow(int fromX, int fromY, Element toElement) {
		this.fromX = fromX;
		this.fromY = fromY;
		this.toElement = toElement;
	}
	
	static int ARROWHEAD_LENGTH = 20;
	static int ARROWHEAD_WIDTH = 10;
	
	static void paintArrow(GC gc, int fromX, int fromY, Element toElement) {
		int toX, toY;
		
		Point toElementOrigin = new Point(0, 0);
		toElement.mapPoint(toElementOrigin, null);
		
		if (fromX < toElementOrigin.x)
			toX = toElementOrigin.x;
		else if (fromX < toElementOrigin.x + toElement.width)
			toX = fromX;
		else
			toX = toElementOrigin.x + toElement.width;
		
		if (fromY < toElementOrigin.y)
			toY = toElementOrigin.y;
		else if (fromY < toElementOrigin.y + toElement.height)
			toY = fromY;
		else
			toY = toElementOrigin.y + toElement.height;
		
		if ((toX - fromX) * (toX - fromX) + (toY - fromY) * (toY - fromY) < 400) {
			// Avoid too short an arrow; point to the furthest corner
			if (fromX < toElementOrigin.x + toElement.width / 2)
				toX = toElementOrigin.x + toElement.width;
			else
				toX = toElement.x;
			if (fromY < toElementOrigin.y + toElement.height / 2)
				toY = toElementOrigin.y + toElement.height;
			else
				toY = toElementOrigin.y;
		}
		
		int length = (int)Math.sqrt((toX - fromX) * (toX - fromX) + (toY - fromY) * (toY - fromY));
		
		gc.drawLine(fromX, fromY, toX, toY);
		
		int arrowBaseX = toX + (fromX - toX) * ARROWHEAD_LENGTH / length;
		int arrowBaseY = toY + (fromY - toY) * ARROWHEAD_LENGTH / length;
		int vecX = (toY - fromY) * ARROWHEAD_WIDTH  / 2 / length;
		int vecY = (fromX - toX) * ARROWHEAD_WIDTH / 2 / length;
		
		Color oldBackground = gc.getBackground();
		gc.setBackground(gc.getDevice().getSystemColor(SWT.COLOR_BLACK));
		gc.fillPolygon(new int[] {toX, toY, arrowBaseX + vecX, arrowBaseY + vecY, arrowBaseX - vecX, arrowBaseY - vecY});
		gc.setBackground(oldBackground);
	}
	
	void paint(GC gc) {
		paintArrow(gc, fromX, fromY, toElement);
	}
}

abstract class VariablesTable {
	int namesWidth = 150;
	int valuesWidth = 150;
	
	abstract void updateNamesWidth();
	abstract void updateValuesWidth();
}

class MachineStateCanvas extends Canvas {

	static String chopPackageName(String fullyQualifiedName) {
		int i = fullyQualifiedName.lastIndexOf('.');
		if (i >= 0)
			return fullyQualifiedName.substring(i + 1);
		else
			return fullyQualifiedName;
	}
	
	static int OUTER_MARGIN = 4;
	
	Font boldFont;
	Color objectColor;
	Element machine;
	Heap heap;
	VariablesTable stackVariablesTable = new VariablesTable() {

		@Override
		void updateNamesWidth() {
			stack.updateNamesWidth();
			
		}

		@Override
		void updateValuesWidth() {
			stack.updateValuesWidth();
		}
		
	};
	
	CallStack stack;
	List<Runnable> delayedInitializers;
	List<Arrow> arrows; // Only meaningful during a paint()
	
	class Variable extends Element {
	
		final static int PADDING = 1;
		final static int INNER_PADDING = 3;
		
		VariablesTable table;
		String name;
		Point nameExtent;
		String valueString;
		Object value;
		Point valueExtent;
	
		void setValue(GC gc, IValue value) throws DebugException {
			if (!(value instanceof IJavaValue))
				return;
			IJavaValue javaValue = (IJavaValue)value;
			IJavaType javaType = javaValue.getJavaType();
			if (!(javaType instanceof IJavaReferenceType))
				return;
			if (javaValue.isNull())
				return;
			if (javaType.getName().equals("java.lang.String")) {
				this.value = this.valueString = '"' + (String)this.value + '"' + " (id=" + ((IJavaObject)javaValue).getUniqueId() + ")";
				return;
			}
			this.valueString = chopPackageName(javaType.getName()) + " (id=" + ((IJavaObject)value).getUniqueId() + ")";
			delayedInitializers.add(() -> {
				try {
					this.value = heap.get(gc, (IJavaObject)value);
				} catch (DebugException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			});
		}
		
		Variable(Element parent, GC gc, Heap heap, int x, int y, VariablesTable table, IVariable variable) throws DebugException {
			super(parent);
			this.x = x;
			this.y = y;
			this.table = table;
			this.width = table.namesWidth + table.valuesWidth;
			this.name = variable.getName();
			this.nameExtent = gc.stringExtent(this.name);
			IValue value = variable.getValue();
			this.valueString = value.getValueString();
			this.value = valueString;
			this.valueExtent = gc.stringExtent(valueString);
			setValue(gc, value);
			this.height = PADDING + Math.max(this.nameExtent.y, this.valueExtent.y) + PADDING;
		}
		
		@Override
		void paint(GC gc) {
			gc.drawString(this.name, this.table.namesWidth - this.nameExtent.x - INNER_PADDING, PADDING);
			Color oldBackground = gc.getBackground();
			gc.setBackground(gc.getDevice().getSystemColor(SWT.COLOR_WHITE));
			gc.fillRectangle(this.table.namesWidth + 2, 0, this.table.valuesWidth - 2, this.height);
			if (this.value instanceof String || ((JavaObject)this.value).parent == null) {
				gc.drawString(valueString, this.table.namesWidth + INNER_PADDING, PADDING);
			} else {
				Point from = new Point(this.table.namesWidth + this.table.valuesWidth / 2, this.height / 2);
				mapPoint(from, null);
				arrows.add(new Arrow(from.x, from.y, (JavaObject)this.value));
			}
			gc.setBackground(oldBackground);
		}
		
		int getDesiredNamesWidth() {
			return nameExtent.x + INNER_PADDING;
		}
		
		int getDesiredValuesWidth() {
			return INNER_PADDING + valueExtent.x + INNER_PADDING;
		}
		
		int getCursor(int x, int y) {
			if (Math.abs(x - table.namesWidth) < 5)
				return SWT.CURSOR_SIZEE;
			else if (Math.abs(x - table.namesWidth - table.valuesWidth) < 10)
				return SWT.CURSOR_SIZEE;
			else
				return SWT.CURSOR_ARROW;
		}
		
		@Override boolean handleMouseEvent(MouseEventType type, MouseEvent e) {
			switch (type) {
			case DOUBLE_CLICKED: {
				// TODO: Create child elements for the column edges?
				if (Math.abs(e.x - table.namesWidth) < 5)
					table.updateNamesWidth();
				else if (Math.abs(e.x - table.namesWidth - table.valuesWidth) < 10)
					table.updateValuesWidth();
				else if (table.namesWidth <= e.x)
					if (value instanceof JavaObject)
						if (((JavaObject)value).parent == null) {
							heap.add((JavaObject)value);
							redraw();
						}
				return true;
			}
			default: break;
			}
			return super.handleMouseEvent(type, e);
		}

	}
	
	class ReturnFrame extends Element {
		final static int BORDER = StackFrame.BORDER;
		final static int PADDING = StackFrame.PADDING;
		
		Variable returnValue;
		
		ReturnFrame(GC gc, int y, int localsX, IVariable returnValue) throws DebugException {
			super(stack);
			this.x = MachineStateCanvas.OUTER_MARGIN;
			this.y = y;
			this.width = BORDER + PADDING + stack.table.namesWidth + stack.table.valuesWidth + PADDING + BORDER;
			this.returnValue = new Variable(this, gc, heap, localsX, BORDER + PADDING, stack.table, returnValue);
		}
		
		void paint(GC gc) {
			gc.setBackground(gc.getDevice().getSystemColor(SWT.COLOR_GRAY));
			gc.fillRectangle(0, 0, this.width, this.returnValue.height + 2 * PADDING + 2 * BORDER);
			gc.drawRectangle(0, 0, this.width, this.returnValue.height + 2 * PADDING + 2 * BORDER);
			super.paint(gc);
		}
		
	}
	
	int getStackFrameWidth() {
		return StackFrame.BORDER + StackFrame.PADDING + stack.table.namesWidth + stack.table.valuesWidth + StackFrame.PADDING + StackFrame.BORDER;
	}
	
	class StackFrame extends Element {
		
		final static int BORDER = 2;
		final static int PADDING = 3;
		
		String method;
		Point methodExtent;
		boolean active;
		
		StackFrame(GC gc, Heap heap, int y, IStackFrame frame, boolean active) throws DebugException {
			super(stack);
			this.active = active;
			this.x = MachineStateCanvas.OUTER_MARGIN;
			this.y = y;
			this.width = getStackFrameWidth();
			y = 0;
			if (frame instanceof IJavaStackFrame) {
				IJavaStackFrame javaFrame = (IJavaStackFrame)frame;
				String className = chopPackageName(javaFrame.getDeclaringTypeName());
				String signature = String.join(", ", javaFrame.getArgumentTypeNames().stream().map(MachineStateCanvas::chopPackageName).collect(Collectors.toList()));
				this.method = className + "::" + javaFrame.getMethodName() + "(" + signature + ")";
			} else
				this.method = frame.getName();
			int lineNumber = frame.getLineNumber();
			if (1 <= lineNumber)
				this.method += " on line " + lineNumber;
			this.methodExtent = gc.stringExtent(this.method);
			y += BORDER;
			y += PADDING;
			y += this.methodExtent.y;
			y += PADDING;
			IVariable[] variables = frame.getVariables();
			IVariable returnValue = null;
			if (active && variables.length > 0) {
				// The first local in the active stack frame seems to be the return value from the most recent call
				returnValue = variables[0];
				int length = variables.length;
				System.arraycopy(variables, 1, variables = new IVariable[length - 1], 0, length - 1);
			}
			int localsX = BORDER + PADDING;
			for (int i = 0; i < variables.length; i++) {
				IVariable variable = variables[i];
				if (!(variable.getName().equals("Lambda") || variable instanceof IJavaVariable && ((IJavaVariable)variable).isStatic())) {
					Variable local = new Variable(this, gc, heap, localsX, y, stack.table, variable);
					y += local.height + PADDING;
				}
			}
			y += BORDER;
			this.height = y;
			if (returnValue != null && !returnValue.getName().equals("no method return value") && !returnValue.getReferenceTypeName().equals("void")) {
				new ReturnFrame(gc, this.y + y, localsX, returnValue);
			}
		}
		
		@Override
		void paint(GC gc) {
			gc.setBackground(gc.getDevice().getSystemColor(SWT.COLOR_GREEN));  //active ? SWT.COLOR_YELLOW : SWT.COLOR_GREEN));
			gc.fillRectangle(0, 0, this.width, this.height);
			int oldWidth = gc.getLineWidth();
			if (active)
				gc.setLineWidth(2);
			gc.drawRectangle(0, 0, this.width, this.height);
			gc.setLineWidth(oldWidth);
			//Font oldFont = gc.getFont();
			//gc.setFont(methodFont);
			gc.drawString(this.method, (this.width - this.methodExtent.x) / 2 , BORDER + PADDING);
			//gc.setFont(oldFont);
			super.paint(gc);
		}
		
		int getDesiredNamesWidth() {
			int width = Math.max(20, methodExtent.x - stack.table.valuesWidth);
			for (Element e : children)
				width = Math.max(width, ((Variable)e).getDesiredNamesWidth());
			return width;
		}
		
		int getDesiredValuesWidth() {
			int width = Math.max(20, methodExtent.x - stack.table.namesWidth);
			for (Element e : children)
				width = Math.max(width, ((Variable)e).getDesiredValuesWidth());
			return width;
		}
	}
	
	class CallStack extends Element {

		VariablesTable table = stackVariablesTable;

		CallStack(GC gc, Heap heap, IStackFrame[] frames) throws DebugException {
			super(machine);
			stack = this;
			int y = MachineStateCanvas.OUTER_MARGIN;
			for (int i = 0; i < frames.length; i++) {
				IStackFrame frame = frames[frames.length - i - 1];
				boolean active = i == frames.length - 1;
				if (active || !(frame instanceof IJavaStackFrame && ((IJavaStackFrame)frame).getDeclaringTypeName().contains("$$Lambda$"))) {
					StackFrame stackFrame = new StackFrame(gc, heap, y, frame, active);
					y += stackFrame.height;
				}
			}
			width = OUTER_MARGIN + getStackFrameWidth() + OUTER_MARGIN;
			height = 10000;
		}

		public void updateNamesWidth() {
			int maxNamesWidth = 20;
			for (Element frame : children)
				maxNamesWidth = Math.max(maxNamesWidth, ((StackFrame)frame).getDesiredNamesWidth());
			table.namesWidth = maxNamesWidth;
			redraw();
		}
		
		public void updateValuesWidth() {
			int maxValuesWidth = 20;
			for (Element frame : children)
				maxValuesWidth = Math.max(maxValuesWidth, ((StackFrame)frame).getDesiredValuesWidth());
			table.valuesWidth = maxValuesWidth;
			redraw();
		}
	}
	
	class JavaObject extends Element {
		
		static final int BORDER = 2;
		static final int PADDING = 3;
		
		long id;
		String className;
		String title;
		Point titleExtent;
		VariablesTable table = new VariablesTable() {

			@Override
			void updateNamesWidth() {
				int namesWidth = Math.max(20, titleExtent.x - valuesWidth);
				for (Variable v : variables)
					namesWidth = Math.max(namesWidth, v.getDesiredNamesWidth());
				this.namesWidth = namesWidth;
				redraw();
			}

			@Override
			void updateValuesWidth() {
				int valuesWidth = Math.max(20, titleExtent.x - namesWidth);
				for (Variable v : variables)
					valuesWidth = Math.max(valuesWidth, v.getDesiredValuesWidth());
				this.valuesWidth = valuesWidth;
				redraw();
			}
			
		};
		Variable[] variables;
		
		Element closeButton;
		
		int getWidth() {
			return BORDER + PADDING + table.namesWidth + table.valuesWidth + PADDING + BORDER;
		}
		
		JavaObject(int x, int y, long id) {
			super(heap);
			this.x = x;
	        this.y = y;
	        this.id = id;
	        this.width = getWidth();
	        this.height = 50;
	        
	        closeButton = new Element(this) {
	        	
	        	@Override
	        	void paint(GC gc) {
	        		if (JavaObject.this.mouseInside) {
		        		gc.drawLine(0, 0, this.width, this.height);
		        		gc.drawLine(0, this.height, this.width, 0);
	        		}
	        	}
	        	
	        	@Override
	        	boolean handleMouseEvent(MouseEventType type, MouseEvent e) {
	        		if (type == MouseEventType.UP) {
	        			JavaObject.this.parent.remove(JavaObject.this);
	        			redraw();
	        			return true;
	        		}
	        		return super.handleMouseEvent(type, e);
	        	}
	        };
	        closeButton.width = 10;
	        closeButton.height = 10;
	        closeButton.y = BORDER + PADDING;
		}
    	
    	@Override
    	void mouseEntered() { redraw(); }
    	
    	@Override
    	void mouseExited() { redraw(); }
		
		void setState(GC gc, IJavaObject javaObject) throws DebugException {
			className = MachineStateCanvas.chopPackageName(javaObject.getReferenceTypeName());
			title = this.className + " (id=" + id + ")";
			if (javaObject instanceof IJavaArray)
				title += " (length=" + ((IJavaArray)javaObject).getLength() + ")";
			titleExtent = gc.stringExtent(title);
			int y = BORDER + PADDING;
			y += titleExtent.y;
			y += PADDING;
			IVariable[] variables = javaObject.getVariables();
			this.variables = new Variable[variables.length];
			int localsX = BORDER + PADDING;
			for (int i = 0; i < variables.length; i++) {
				if (!(variables[i] instanceof IJavaVariable && ((IJavaVariable)variables[i]).isStatic())) {
					Variable variable = this.variables[i] = new Variable(this, gc, heap, localsX, y, table, variables[i]);
					y += variable.height;
					y += PADDING;
				}
			}
			y += BORDER;
			this.height = y;
			this.width = getWidth();
			closeButton.x = this.width - BORDER - PADDING - closeButton.width;
		}
		
		@Override
		void paint(GC gc) {
			Color oldBackground = gc.getBackground();
			gc.setBackground(objectColor);
			gc.fillRoundRectangle(0, 0, this.width, this.height, 10, 10);
			gc.drawRoundRectangle(0, 0, this.width, this.height, 10, 10);
			gc.drawString(this.title, BORDER + PADDING, BORDER + PADDING);
			super.paint(gc);
			gc.setBackground(oldBackground);
		}
	}


	class Heap extends Element {
		
		static final int PADDING = 10;
		
		int nextX = 30;
		int nextY = MachineStateCanvas.OUTER_MARGIN;
		
		HashMap<Long, JavaObject> objects = new HashMap<>();
		
		Heap() {
			super(machine);
			this.x = 300;
			this.width = 10000;
			this.height = 10000;
		}
		
		JavaObject get(GC gc, IJavaObject javaObject) throws DebugException {
			long id = javaObject.getUniqueId();
			JavaObject result = objects.get(id);
			if (result == null) {
				result = new JavaObject(nextX, nextY, id);
				result.setState(gc, javaObject);
				nextY += result.height + PADDING;
				objects.put(id, result);
			} else
				result.setState(gc, javaObject);
			return result;
		}
	}
	
	MachineStateCanvas(Composite parent) {
		super(parent, SWT.DOUBLE_BUFFERED);
		addPaintListener(this::paint);
		addMouseMoveListener(new MouseMoveListener() {

			@Override
			public void mouseMove(MouseEvent e) {
				if (stack != null)
					machine.handleMouseEvent(MouseEventType.MOVED, e);
			}
			
		});
		addMouseListener(new MouseListener() {

			@Override
			public void mouseDoubleClick(MouseEvent e) {
				if (stack != null)
					machine.handleMouseEvent(MouseEventType.DOUBLE_CLICKED, e);
			}

			@Override
			public void mouseDown(MouseEvent e) {
//				if (stack != null)
//					if (!stack.handleMouseEvent(e))
//						heap.handleMouseEvent(e);
				
			}

			@Override
			public void mouseUp(MouseEvent e) {
				if (stack != null)
					machine.handleMouseEvent(MouseEventType.UP, e);
			}
			
		});
		FontDescriptor boldDescriptor = FontDescriptor.createFrom(getFont()).setStyle(SWT.BOLD);
		boldFont = boldDescriptor.createFont(getDisplay());
		objectColor = new Color(getDisplay(), 255, 204, 203);
		addDisposeListener(event -> {
			boldFont.dispose();
			objectColor.dispose();
		});
	}
	
	Element canvas = new Element(null);

	void paint(PaintEvent event) {
		GC gc = event.gc;
		IDebugTarget[] targets = DebugPlugin.getDefault().getLaunchManager().getDebugTargets();
		int y = 0;
		if (targets.length > 0) {
			try {
				IThread[] threads = targets[0].getThreads();
				if (threads.length > 0) {
					if (targets.length > 1) {
						String message = "Multiple debug targets exist. Showing target " + targets[0].getName() + ".";
						event.gc.drawString(message, 1, y + 1);
						y += 1 + gc.stringExtent(message).y + 1;
					}
					List<IThread> userThreads = Arrays.stream(threads).filter(t -> {
						try {
							return !(t instanceof IJavaThread && ((IJavaThread)t).isSystemThread());
						} catch (DebugException e) {
							e.printStackTrace();
							return false;
						}
					}).collect(Collectors.toList());
					if (userThreads.size() > 1) {
						String message = "Target has multiple threads. Showing thread " + userThreads.get(0).getName() + ". Ignoring threads ";
						for (int i = 1; i < userThreads.size(); i++) {
							if (1 < i)
								message += ", ";
							message += userThreads.get(i).getName();
						}
						message += ".";
						event.gc.drawString(message, 1, y + 1);
						y += 1 + gc.stringExtent(message).y + 1;
					}
					IStackFrame[] frames = threads[0].getStackFrames();
					if (frames.length > 0) {
						if (heap == null) {
							machine = new Element(canvas);
							heap = new Heap();
						}
						machine.y = y;
						delayedInitializers = new ArrayList<>();
						if (stack != null)
							machine.remove(stack);
						new CallStack(gc, heap, frames);
						heap.x = stack.width;
						while (!delayedInitializers.isEmpty()) {
							List<Runnable> oldDelayedInitializers = delayedInitializers;
							delayedInitializers = new ArrayList<>();
							for (Runnable r : oldDelayedInitializers)
								r.run();
						}
						delayedInitializers = null;
					}
				}
			} catch (DebugException e) {
				throw new RuntimeException(e);
			}
			if (stack != null) {
				arrows = new ArrayList<>();
				canvas.paint(gc);
				for (Arrow arrow : arrows)
					arrow.paint(gc);
				arrows = null;
			}
		} else {
			if (machine != null)
				canvas.remove(machine);
			machine = null;
			heap = null;
			stack = null;
			event.gc.drawString("No program running.", 1, 1);
		}
	}
}

public class MachineStateView extends ViewPart {

	public MachineStateView() {
	}

	public void createPartControl(Composite parent) {
		MachineStateCanvas canvas = new MachineStateCanvas(parent);
		Display display = canvas.getDisplay();
		IDebugEventSetListener debugListener = events -> {
			display.asyncExec(() -> {
				if (!canvas.isDisposed())
					canvas.redraw();
			});
		};
		DebugPlugin.getDefault().addDebugEventListener(debugListener);
		canvas.addDisposeListener(event -> {
			DebugPlugin.getDefault().removeDebugEventListener(debugListener);
		});
	}

	public void setFocus() {
		// set focus to my widget. For a label, this doesn't
		// make much sense, but for more complex sets of widgets
		// you would decide which one gets the focus.
	}
}
