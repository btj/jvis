package io.github.btj.jvis;

import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;

import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.IDebugEventSetListener;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.debug.core.model.IStackFrame;
import org.eclipse.debug.core.model.IThread;
import org.eclipse.debug.core.model.IVariable;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.ui.part.ViewPart;

class Element {
	int x, y, width, height;
}

class Variable extends Element {

	final static int PADDING = 1;
	final static int INNER_PADDING = 3;
	
	String name;
	Point nameExtent;
	String value;
	Point valueExtent;
	int nameWidth;
	int valueWidth;

	Variable(GC gc, int x, int y, int nameWidth, int valueWidth, IVariable variable) throws DebugException {
		this.x = x;
		this.y = y;
		this.nameWidth = nameWidth;
		this.valueWidth = valueWidth;
		this.width = nameWidth + valueWidth;
		this.name = variable.getName();
		this.nameExtent = gc.stringExtent(this.name);
		this.value = variable.getValue().getValueString();
		this.valueExtent = gc.stringExtent(this.value);
		this.height = PADDING + Math.max(this.nameExtent.y, this.valueExtent.y) + PADDING;
	}
	
	void paint(GC gc) {
		gc.drawString(this.name, this.x + this.nameWidth - this.nameExtent.x - INNER_PADDING, this.y + PADDING);
		Color oldBackground = gc.getBackground();
		gc.setBackground(gc.getDevice().getSystemColor(SWT.COLOR_WHITE));
		gc.fillRectangle(this.x + this.nameWidth, this.y, this.valueWidth, this.height);
		gc.drawString(this.value, this.x + this.nameWidth + INNER_PADDING, this.y + PADDING);
		gc.setBackground(oldBackground);
	}
}

class StackFrame extends Element {
	
	final static int BORDER = 2;
	final static int WIDTH = 300;
	final static int PADDING = 3;
	
	String method;
	Point methodExtent;
	Variable[] locals;
	
	StackFrame(GC gc, int y, IStackFrame frame) throws DebugException {
		this.x = MachineStateCanvas.OUTER_MARGIN;
		this.y = y;
		this.width = WIDTH;
		this.method = frame.getName();
		this.methodExtent = gc.stringExtent(this.method);
		y += BORDER;
		y += PADDING;
		y += this.methodExtent.y;
		y += PADDING;
		IVariable[] variables = frame.getVariables();
		this.locals = new Variable[variables.length];
		int localsX = this.x + BORDER + PADDING;
		int localsWidth = this.width - 2 * (BORDER + PADDING);
		int namesWidth = localsWidth / 2;
		int valuesWidth = localsWidth - namesWidth;
		for (int i = 0; i < variables.length; i++) {
			IVariable variable = variables[i];
			Variable local = locals[i] = new Variable(gc, localsX, y, namesWidth, valuesWidth, variable);
			y += local.height + PADDING;
		}
		this.height = y + BORDER - this.y;
	}
	
	void paint(GC gc, boolean active) {
		gc.setBackground(gc.getDevice().getSystemColor(active ? SWT.COLOR_YELLOW : SWT.COLOR_GREEN));
		gc.fillRectangle(this.x, this.y, this.width, this.height);
		gc.drawRectangle(this.x, this.y, this.width, this.height);
		gc.drawString(this.method, this.x + (this.width - this.methodExtent.x) / 2 , this.y + BORDER + PADDING);
		for (Variable v : this.locals)
			v.paint(gc);
	}
}

class MachineStateCanvas extends Canvas {
	
	static int OUTER_MARGIN = 4;
	
	StackFrame[] stackFrames;

	MachineStateCanvas(Composite parent) {
		super(parent, 0);
		addPaintListener(this::paint);
	}
	
	void layoutStackFrames(GC gc, IStackFrame[] frames) throws DebugException {
		stackFrames = new StackFrame[frames.length];
		int y = OUTER_MARGIN;
		for (int i = 0; i < frames.length; i++) {
			IStackFrame frame = frames[frames.length - i - 1];
			StackFrame stackFrame = stackFrames[i] = new StackFrame(gc, y, frame);
			y += stackFrame.height;
		}
	}

	void paint(PaintEvent event) {
		IDebugTarget[] targets = DebugPlugin.getDefault().getLaunchManager().getDebugTargets();
		if (targets.length > 0) {
			try {
				IThread[] threads = targets[0].getThreads();
				if (threads.length > 0) {
					IStackFrame[] frames = threads[0].getStackFrames();
					if (frames.length > 0) {
						layoutStackFrames(event.gc, frames);
						StackFrame activeFrame = stackFrames[stackFrames.length - 1];
						for (StackFrame frame : stackFrames)
							frame.paint(event.gc, frame == activeFrame);
					}
				}
			} catch (DebugException e) {
				throw new RuntimeException(e);
			}
		} else
			event.gc.drawString("No program running.", 1, 1);
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
