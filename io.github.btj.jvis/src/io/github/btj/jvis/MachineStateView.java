package io.github.btj.jvis;

import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;

import java.util.ArrayList;
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
import org.eclipse.jdt.debug.core.IJavaObject;
import org.eclipse.jdt.debug.core.IJavaReferenceType;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaValue;
import org.eclipse.jface.resource.FontDescriptor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Transform;
import org.eclipse.ui.part.ViewPart;

class Element {
	Element parent;
	ArrayList<Element> children = new ArrayList<>();
	int x, y, width, height;
	
	Element(Element parent) {
		if (parent != null)
			parent.add(this);
	}
	
	void remove(Element child) {
		if (child.parent != this) throw new AssertionError();
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
		//Transform transform = new Transform(gc.getDevice());
		//gc.getTransform(transform);
		for (Element child : children) {
			//transform.translate(x, y);
			//gc.setTransform(transform);
			child.paint(gc);
			//transform.translate(-x, -y);
			//gc.setTransform(transform);
		}
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
		
		if (fromX < toElement.x)
			toX = toElement.x;
		else if (fromX < toElement.x + toElement.width)
			toX = fromX;
		else
			toX = toElement.x + toElement.width;
		
		if (fromY < toElement.y)
			toY = toElement.y;
		else if (fromY < toElement.y + toElement.height)
			toY = fromY;
		else
			toY = toElement.y + toElement.height;
		
		if ((toX - fromX) * (toX - fromX) + (toY - fromY) * (toY - fromY) < 400) {
			// Avoid too short an arrow; point to the furthest corner
			if (fromX < toElement.x + toElement.width / 2)
				toX = toElement.x + toElement.width;
			else
				toX = toElement.x;
			if (fromY < toElement.y + toElement.height / 2)
				toY = toElement.y + toElement.height;
			else
				toY = toElement.y;
		}
		
		int length = (int)Math.sqrt((toX - fromX) * (toX - fromX) + (toY - fromY) * (toY - fromY));
		
		gc.drawLine(fromX, fromY, toX, toY);
		
		int arrowBaseX = toX + (fromX - toX) * ARROWHEAD_LENGTH / length;
		int arrowBaseY = toY + (fromY - toY) * ARROWHEAD_LENGTH / length;
		int vecX = (toY - fromY) * ARROWHEAD_WIDTH / length;
		int vecY = (fromX - toX) * ARROWHEAD_WIDTH / length;
		
		Color oldBackground = gc.getBackground();
		gc.setBackground(gc.getDevice().getSystemColor(SWT.COLOR_BLACK));
		gc.fillPolygon(new int[] {toX, toY, arrowBaseX + vecX, arrowBaseY + vecY, arrowBaseX - vecX, arrowBaseY - vecY});
		gc.setBackground(oldBackground);
	}
	
	void paint(GC gc) {
		paintArrow(gc, fromX, fromY, toElement);
	}
}

class VariablesTable {
	int namesWidth = 150;
	int valuesWidth = 150;
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
	Heap heap;
	CallStack stack;
	List<Arrow> arrows; // Only meaningful during a paint()
	
	class Variable extends Element {
	
		final static int PADDING = 1;
		final static int INNER_PADDING = 3;
		
		VariablesTable table;
		String name;
		Point nameExtent;
		Object value;
		Point valueExtent;
	
		Variable(Element parent, GC gc, Heap heap, int x, int y, VariablesTable table, IVariable variable) throws DebugException {
			super(parent);
			this.x = x;
			this.y = y;
			this.table = table;
			this.width = table.namesWidth + table.valuesWidth;
			this.name = variable.getName();
			this.nameExtent = gc.stringExtent(this.name);
			IValue value = variable.getValue();
			String valueString = value.getValueString();
			this.value = valueString;
			this.valueExtent = gc.stringExtent(valueString);
			if (value instanceof IJavaValue && ((IJavaValue)value).getJavaType() instanceof IJavaReferenceType && !((IJavaValue)value).isNull()) {
				this.value = heap.get((IJavaObject)value);
			}
			this.height = PADDING + Math.max(this.nameExtent.y, this.valueExtent.y) + PADDING;
		}
		
		@Override
		void paint(GC gc) {
			gc.drawString(this.name, this.x + this.table.namesWidth - this.nameExtent.x - INNER_PADDING, this.y + PADDING);
			Color oldBackground = gc.getBackground();
			gc.setBackground(gc.getDevice().getSystemColor(SWT.COLOR_WHITE));
			gc.fillRectangle(this.x + this.table.namesWidth + 2, this.y, this.table.valuesWidth - 2, this.height);
			if (this.value instanceof String) {
				String valueString = (String)this.value;
				gc.drawString(valueString, this.x + this.table.namesWidth + INNER_PADDING, this.y + PADDING);
			} else {
				arrows.add(new Arrow(this.x + this.table.namesWidth + this.table.valuesWidth / 2, this.y + this.height / 2, (JavaObject)this.value));
			}
			gc.setBackground(oldBackground);
		}
	}
	
	class StackFrame extends Element {
		
		final static int BORDER = 2;
		final static int PADDING = 3;
		
		String method;
		Point methodExtent;
		Variable[] locals;
		boolean active;
		Variable returnValue;
		
		StackFrame(GC gc, Heap heap, int y, IStackFrame frame, boolean active) throws DebugException {
			super(stack);
			this.active = active;
			this.x = MachineStateCanvas.OUTER_MARGIN;
			this.y = y;
			this.width = BORDER + PADDING + stack.table.namesWidth + stack.table.valuesWidth + PADDING + BORDER;
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
			this.locals = new Variable[variables.length];
			int localsX = this.x + BORDER + PADDING;
			for (int i = 0; i < variables.length; i++) {
				IVariable variable = variables[i];
				Variable local = locals[i] = new Variable(this, gc, heap, localsX, y, stack.table, variable);
				y += local.height + PADDING;
			}
			y += BORDER;
			this.height = y - this.y;
			if (returnValue != null && !returnValue.getName().equals("no method return value") && !returnValue.getReferenceTypeName().equals("void")) {
				y += BORDER + PADDING;
				this.returnValue = new Variable(this, gc, heap, localsX, y, stack.table, returnValue);
			}
		}
		
		@Override
		void paint(GC gc) {
			gc.setBackground(gc.getDevice().getSystemColor(SWT.COLOR_GREEN));  //active ? SWT.COLOR_YELLOW : SWT.COLOR_GREEN));
			gc.fillRectangle(this.x, this.y, this.width, this.height);
			int oldWidth = gc.getLineWidth();
			if (active)
				gc.setLineWidth(2);
			gc.drawRectangle(this.x, this.y, this.width, this.height);
			gc.setLineWidth(oldWidth);
			//Font oldFont = gc.getFont();
			//gc.setFont(methodFont);
			gc.drawString(this.method, this.x + (this.width - this.methodExtent.x) / 2 , this.y + BORDER + PADDING);
			//gc.setFont(oldFont);
			if (this.returnValue != null) {
				gc.setBackground(gc.getDevice().getSystemColor(SWT.COLOR_GRAY));
				gc.fillRectangle(this.x, this.y + this.height, this.width, this.returnValue.height + 2 * PADDING + 2 * BORDER);
				gc.drawRectangle(this.x, this.y + this.height, this.width, this.returnValue.height + 2 * PADDING + 2 * BORDER);
			}
			super.paint(gc);
		}
	}
	
	class CallStack extends Element {

		VariablesTable table = new VariablesTable();
		StackFrame[] stackFrames;

		CallStack(GC gc, Heap heap, IStackFrame[] frames) throws DebugException {
			super(null);
			stack = this;
			stackFrames = new StackFrame[frames.length];
			int y = MachineStateCanvas.OUTER_MARGIN;
			for (int i = 0; i < frames.length; i++) {
				IStackFrame frame = frames[frames.length - i - 1];
				boolean active = i == frames.length - 1;
				StackFrame stackFrame = stackFrames[i] = new StackFrame(gc, heap, y, frame, active);
				y += stackFrame.height;
			}
		}
	}
	
	class JavaObject extends Element {
		
		static final int BORDER = 2;
		static final int PADDING = 3;
		
		long id;
		String className;
		
		JavaObject(int x, int y, long id) {
			super(heap);
			this.x = x;
	        this.y = y;
	        this.id = id;
	        this.width = 200;
	        this.height = 50;
		}
		
		void setState(IJavaObject javaObject) throws DebugException {
			className = MachineStateCanvas.chopPackageName(javaObject.getReferenceTypeName());
		}
		
		@Override
		void paint(GC gc) {
			Color oldBackground = gc.getBackground();
			gc.setBackground(objectColor);
			gc.fillRoundRectangle(this.x, this.y, this.width, this.height, 10, 10);
			gc.drawRoundRectangle(this.x, this.y, this.width, this.height, 10, 10);
			gc.drawString(this.className + " (id=" + id + ")", this.x + BORDER + PADDING, this.y + BORDER + PADDING);
			gc.setBackground(oldBackground);
		}
	}


	class Heap extends Element {
		int nextX = 300 + 30;
		int nextY = MachineStateCanvas.OUTER_MARGIN;
		
		HashMap<Long, JavaObject> objects = new HashMap<>();
		
		Heap() {
			super(null);
		}
		
		JavaObject get(IJavaObject javaObject) throws DebugException {
			long id = javaObject.getUniqueId();
			JavaObject result = objects.get(id);
			if (result == null) {
				result = new JavaObject(nextX, nextY, id);
				nextY += 100;
				objects.put(id, result);
			}
			result.setState(javaObject);
			return result;
		}
	}

	MachineStateCanvas(Composite parent) {
		super(parent, SWT.DOUBLE_BUFFERED);
		addPaintListener(this::paint);
		FontDescriptor boldDescriptor = FontDescriptor.createFrom(getFont()).setStyle(SWT.BOLD);
		boldFont = boldDescriptor.createFont(getDisplay());
		objectColor = new Color(getDisplay(), 255, 204, 203);
		addDisposeListener(event -> {
			boldFont.dispose();
			objectColor.dispose();
		});
	}

	void paint(PaintEvent event) {
		IDebugTarget[] targets = DebugPlugin.getDefault().getLaunchManager().getDebugTargets();
		if (targets.length > 0) {
			try {
				IThread[] threads = targets[0].getThreads();
				if (threads.length > 0) {
					IStackFrame[] frames = threads[0].getStackFrames();
					if (frames.length > 0) {
						if (heap == null)
							heap = new Heap();
						new CallStack(event.gc, heap, frames);
					}
				}
			} catch (DebugException e) {
				throw new RuntimeException(e);
			}
			if (stack != null) {
				heap.paint(event.gc);
				arrows = new ArrayList<>();
				stack.paint(event.gc);
				for (Arrow arrow : arrows)
					arrow.paint(event.gc);
				arrows = null;
			}
		} else {
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
