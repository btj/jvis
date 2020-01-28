package io.github.btj.jvis;

import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.IDebugEventSetListener;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.debug.core.model.IStackFrame;
import org.eclipse.debug.core.model.IThread;
import org.eclipse.swt.SWT;
import org.eclipse.ui.part.ViewPart;

public class MachineView extends ViewPart {

	int count;

	class MyCanvas extends Canvas {

		MyCanvas(Composite parent) {
			super(parent, 0);
		}
	}

	public MachineView() {
	}

	public void createPartControl(Composite parent) {
    	  MyCanvas canvas = new MyCanvas(parent);
    	  canvas.addPaintListener(event -> {
    		  String text = "No program running.";
    		  IDebugTarget[] targets = DebugPlugin.getDefault().getLaunchManager().getDebugTargets();
    		  if (targets.length > 0) {
    			  try {
					IThread[] threads = targets[0].getThreads();
					if (threads.length > 0) {
						IStackFrame[] frames = threads[0].getStackFrames();
						text = frames.length + " stack frames";
					}
				} catch (DebugException e) {
					throw new RuntimeException(e);
				}
    		  }
    		  event.gc.drawString(text + " (paint count: " + count++ + ")", 1, 1);
    	  });
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
