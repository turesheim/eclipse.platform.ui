/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others. All rights reserved.
 * This program and the accompanying materials are made available under the
 * terms of the Common Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors: IBM Corporation - initial API and implementation
 ******************************************************************************/

package org.eclipse.ui.internal;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.internal.runtime.InternalPlatform;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IPluginDescriptor;
import org.eclipse.core.runtime.IPluginRegistry;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Plugin;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.action.ActionContributionItem;
import org.eclipse.jface.action.CommandResolver;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.operation.ModalContext;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferenceConverter;
import org.eclipse.jface.preference.PreferenceManager;
import org.eclipse.jface.resource.ColorRegistry;
import org.eclipse.jface.resource.FontRegistry;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.JFaceColors;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.resource.StringConverter;
import org.eclipse.jface.util.ListenerList;
import org.eclipse.jface.util.OpenStrategy;
import org.eclipse.jface.util.SafeRunnable;
import org.eclipse.jface.window.Window;
import org.eclipse.jface.window.WindowManager;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.BusyIndicator;
import org.eclipse.swt.graphics.DeviceData;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IDecoratorManager;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorRegistry;
import org.eclipse.ui.IElementFactory;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.IPerspectiveDescriptor;
import org.eclipse.ui.IPerspectiveRegistry;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.IStartup;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IViewReference;
import org.eclipse.ui.IWindowListener;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.IWorkingSetManager;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.WorkbenchException;
import org.eclipse.ui.XMLMemento;
import org.eclipse.ui.activities.IWorkbenchActivitySupport;
import org.eclipse.ui.application.IWorkbenchPreferences;
import org.eclipse.ui.application.WorkbenchAdvisor;
import org.eclipse.ui.commands.ICommand;
import org.eclipse.ui.commands.IKeySequenceBinding;
import org.eclipse.ui.commands.IWorkbenchCommandSupport;
import org.eclipse.ui.contexts.IWorkbenchContextSupport;
import org.eclipse.ui.internal.activities.ws.WorkbenchActivitySupport;
import org.eclipse.ui.internal.colors.ColorDefinition;
import org.eclipse.ui.internal.commands.ws.WorkbenchCommandSupport;
import org.eclipse.ui.internal.contexts.ws.WorkbenchContextSupport;
import org.eclipse.ui.internal.decorators.DecoratorManager;
import org.eclipse.ui.internal.fonts.FontDefinition;
import org.eclipse.ui.internal.intro.IIntroConstants;
import org.eclipse.ui.internal.intro.IIntroRegistry;
import org.eclipse.ui.internal.intro.IntroDescriptor;
import org.eclipse.ui.internal.intro.IntroMessages;
import org.eclipse.ui.internal.misc.Assert;
import org.eclipse.ui.internal.misc.Policy;
import org.eclipse.ui.internal.misc.UIStats;
import org.eclipse.ui.internal.progress.ProgressManager;
import org.eclipse.ui.internal.testing.WorkbenchTestable;
import org.eclipse.ui.intro.IIntroPart;
import org.eclipse.ui.keys.KeySequence;
import org.eclipse.ui.keys.KeyStroke;
import org.eclipse.ui.keys.SWTKeySupport;
import org.eclipse.ui.progress.IProgressService;

/**
 * The workbench class represents the top of the Eclipse user interface. Its
 * primary responsability is the management of workbench windows, dialogs,
 * wizards, and other workbench-related windows.
 * <p>
 * Note that any code that is run during the creation of a workbench instance
 * should not required access to the display.
 * </p>
 * <p>
 * Note that this internal class changed significantly between 2.1 and 3.0.
 * Applications that used to define subclasses of this internal class need to
 * be rewritten to use the new workbench advisor API.
 * </p>
 */
public final class Workbench implements IWorkbench {
	private static final String VERSION_STRING[] = { "0.046", "2.0" }; //$NON-NLS-1$ //$NON-NLS-2$
	private static final String DEFAULT_WORKBENCH_STATE_FILENAME = "workbench.xml"; //$NON-NLS-1$
	private static final int RESTORE_CODE_OK = 0;
	private static final int RESTORE_CODE_RESET = 1;
	private static final int RESTORE_CODE_EXIT = 2;

	/**
	 * Holds onto the only instance of Workbench.
	 */
	private static Workbench instance;

	/**
	 * The testable object facade.
	 * 
	 * @since 3.0
	 */
	private static WorkbenchTestable testableObject;

	/**
	 * The display used for all UI interactions with this workbench.
	 * 
	 * @since 3.0
	 */
	private Display display;

	private WindowManager windowManager;
	private WorkbenchWindow activatedWindow;
	private EditorHistory editorHistory;
	private boolean runEventLoop = true;
	private boolean isStarting = true;
	private boolean isClosing = false;

	/**
	 * PlatformUI return code (as opposed to IPlatformRunnable return code).
	 */
	private int returnCode;

	private ListenerList windowListeners = new ListenerList();

	/**
	 * Advisor providing application-specific configuration and customization
	 * of the workbench.
	 * 
	 * @since 3.0
	 */
	private WorkbenchAdvisor advisor;

	/**
	 * Object for configuring the workbench. Lazily initialized to an instance
	 * unique to the workbench instance.
	 * 
	 * @since 3.0
	 */
	private WorkbenchConfigurer workbenchConfigurer;

	//for dynamic UI
	/**
	 * ExtensionEventHandler handles extension life-cycle events. 
	 */
	private ExtensionEventHandler extensionEventHandler;

	/**
	 * Creates a new workbench.
	 * 
	 * @param display
	 *            the display to be used for all UI interactions with the
	 *            workbench
	 * @param advisor
	 *            the application-specific advisor that configures and
	 *            specializes this workbench instance
	 * @since 3.0
	 */
	private Workbench(Display display, WorkbenchAdvisor advisor) {
		super();

		if (instance != null) {
			throw new IllegalStateException(WorkbenchMessages.getString("Workbench.CreatingWorkbenchTwice")); //$NON-NLS-1$
		}
		Assert.isNotNull(display);
		Assert.isNotNull(advisor);
		this.advisor = advisor;
		this.display = display;
		Workbench.instance = this;
		// for dynamic UI
		extensionEventHandler = new ExtensionEventHandler(this);
		InternalPlatform.getDefault().getRegistry().addRegistryChangeListener(extensionEventHandler);
	}

	/**
	 * Returns the one and only instance of the workbench, if there is one.
	 * 
	 * @return the workbench, or <code>null</code> if the workbench has not
	 *         been created, or has been created and already completed
	 */
	public static final Workbench getInstance() {
		return instance;
	}

	/**
	 * Creates the workbench and associates it with the the given display and
	 * workbench advisor, and runs the workbench UI. This entails processing
	 * and dispatching events until the workbench is closed or restarted.
	 * <p>
	 * This method is intended to be called by <code>PlatformUI</code>.
	 * Fails if the workbench UI has already been created.
	 * </p>
	 * <p>
	 * The display passed in must be the default display.
	 * </p>
	 * 
	 * @param display
	 *            the display to be used for all UI interactions with the
	 *            workbench
	 * @param advisor
	 *            the application-specific advisor that configures and
	 *            specializes the workbench
	 * @return return code {@link PlatformUI#RETURN_OK RETURN_OK}for normal
	 *         exit; {@link PlatformUI#RETURN_RESTART RETURN_RESTART}if the
	 *         workbench was terminated with a call to
	 *         {@link IWorkbench#restart IWorkbench.restart}; other values
	 *         reserved for future use
	 */
	public static final int createAndRunWorkbench(Display display, WorkbenchAdvisor advisor) {
		// create the workbench instance
		Workbench workbench = new Workbench(display, advisor);
		// run the workbench event loop
		int returnCode = workbench.runUI();
		return returnCode;
	}

	/**
	 * Creates the <code>Display</code> to be used by the workbench.
	 * 
	 * @return the display
	 */
	public static Display createDisplay() {
		// setup the application name used by SWT to lookup resources on some
		// platforms
		String applicationName = WorkbenchPlugin.getDefault().getAppName();
		if (applicationName != null) {
			Display.setAppName(applicationName);
		}

		// create the display
		Display newDisplay = null;
		if (Policy.DEBUG_SWT_GRAPHICS) {
			DeviceData data = new DeviceData();
			data.tracking = true;
			newDisplay = new Display(data);
		} else {
			newDisplay = new Display();
		}

		// workaround for 1GEZ9UR and 1GF07HN
		newDisplay.setWarnings(false);

		//Set the priority higher than normal so as to be higher
		//than the JobManager.
		Thread.currentThread().setPriority(Math.min(Thread.MAX_PRIORITY, Thread.NORM_PRIORITY + 1));

		return newDisplay;
	}

	/**
	 * Returns the testable object facade, for use by the test harness.
	 * 
	 * @return the testable object facade
	 * @since 3.0
	 */
	public static WorkbenchTestable getWorkbenchTestable() {
		if (testableObject == null) {
			testableObject = new WorkbenchTestable();
		}
		return testableObject;
	}

	/*
	 * (non-Javadoc) Method declared on IWorkbench.
	 */
	public void addWindowListener(IWindowListener l) {
		windowListeners.add(l);
	}

	/*
	 * (non-Javadoc) Method declared on IWorkbench.
	 */
	public void removeWindowListener(IWindowListener l) {
		windowListeners.remove(l);
	}

	/**
	 * Fire window opened event.
	 * 
	 * @param window
	 *            The window which just opened; should not be <code>null</code>.
	 */
	protected void fireWindowOpened(final IWorkbenchWindow window) {
		Object list[] = windowListeners.getListeners();
		for (int i = 0; i < list.length; i++) {
			final IWindowListener l = (IWindowListener) list[i];
			Platform.run(new SafeRunnable() {
				public void run() {
					l.windowOpened(window);
				}
			});
		}
	}
	/**
	 * Fire window closed event.
	 * 
	 * @param window
	 *            The window which just closed; should not be <code>null</code>.
	 */
	protected void fireWindowClosed(final IWorkbenchWindow window) {
		if (activatedWindow == window) {
			// Do not hang onto it so it can be GC'ed
			activatedWindow = null;
		}

		Object list[] = windowListeners.getListeners();
		for (int i = 0; i < list.length; i++) {
			final IWindowListener l = (IWindowListener) list[i];
			Platform.run(new SafeRunnable() {
				public void run() {
					l.windowClosed(window);
				}
			});
		}
	}
	/**
	 * Fire window activated event.
	 * 
	 * @param window
	 *            The window which was just activated; should not be <code>null</code>.
	 */
	protected void fireWindowActivated(final IWorkbenchWindow window) {
		Object list[] = windowListeners.getListeners();
		for (int i = 0; i < list.length; i++) {
			final IWindowListener l = (IWindowListener) list[i];
			Platform.run(new SafeRunnable() {
				public void run() {
					l.windowActivated(window);
				}
			});
		}
	}
	/**
	 * Fire window deactivated event.
	 * 
	 * @param window
	 *            The window which was just deactivated; should not be <code>null</code>.
	 */
	protected void fireWindowDeactivated(final IWorkbenchWindow window) {
		Object list[] = windowListeners.getListeners();
		for (int i = 0; i < list.length; i++) {
			final IWindowListener l = (IWindowListener) list[i];
			Platform.run(new SafeRunnable() {
				public void run() {
					l.windowDeactivated(window);
				}
			});
		}
	}

	/**
	 * Closes the workbench. Assumes that the busy cursor is active.
	 * 
	 * @param force
	 *            true if the close is mandatory, and false if the close is
	 *            allowed to fail
	 * @return true if the close succeeded, and false otherwise
	 */
	private boolean busyClose(final boolean force) {

		// save any open editors if they are dirty
		isClosing = saveAllEditors(!force);
		if (!force && !isClosing) {
			return false;
		}

		IPreferenceStore store = getPreferenceStore();
		boolean closeEditors = store.getBoolean(IWorkbenchPreferences.SHOULD_CLOSE_EDITORS_ON_EXIT);
		if (closeEditors) {
			Platform.run(new SafeRunnable() {
				public void run() {
					IWorkbenchWindow windows[] = getWorkbenchWindows();
					for (int i = 0; i < windows.length; i++) {
						IWorkbenchPage pages[] = windows[i].getPages();
						for (int j = 0; j < pages.length; j++) {
							isClosing = isClosing && pages[j].closeAllEditors(false);
						}
					}
				}
			});
			if (!force && !isClosing) {
				return false;
			}
		}

		if (getWorkbenchConfigurer().getSaveAndRestore()) {
			Platform.run(new SafeRunnable() {
				public void run() {
					XMLMemento mem = recordWorkbenchState();
					//Save the IMemento to a file.
					saveMementoToFile(mem);
				}
				public void handleException(Throwable e) {
					String message;
					if (e.getMessage() == null) {
						message = WorkbenchMessages.getString("ErrorClosingNoArg"); //$NON-NLS-1$
					} else {
						message = WorkbenchMessages.format("ErrorClosingOneArg", new Object[] { e.getMessage()}); //$NON-NLS-1$
					}

					if (!MessageDialog.openQuestion(null, WorkbenchMessages.getString("Error"), message)) { //$NON-NLS-1$
						isClosing = false;
					}
				}
			});
		}
		if (!force && !isClosing) {
			return false;
		}

		Platform.run(new SafeRunnable(WorkbenchMessages.getString("ErrorClosing")) { //$NON-NLS-1$
			public void run() {
				if (isClosing || force)
					isClosing = windowManager.close();
			}
		});

		if (!force && !isClosing) {
			return false;
		}

		runEventLoop = false;
		return true;
	}

	/*
	 * (non-Javadoc) Method declared on IWorkbench.
	 */
	public boolean saveAllEditors(boolean confirm) {
		final boolean finalConfirm = confirm;
		final boolean[] result = new boolean[1];
		result[0] = true;

		Platform.run(new SafeRunnable(WorkbenchMessages.getString("ErrorClosing")) { //$NON-NLS-1$
			public void run() {
				//Collect dirtyEditors
				ArrayList dirtyEditors = new ArrayList();
				ArrayList dirtyEditorsInput = new ArrayList();
				IWorkbenchWindow windows[] = getWorkbenchWindows();
				for (int i = 0; i < windows.length; i++) {
					IWorkbenchPage pages[] = windows[i].getPages();
					for (int j = 0; j < pages.length; j++) {
						WorkbenchPage page = (WorkbenchPage) pages[j];
						IEditorPart editors[] = page.getDirtyEditors();
						for (int k = 0; k < editors.length; k++) {
							IEditorPart editor = editors[k];
							if (editor.isDirty()) {
								if (!dirtyEditorsInput.contains(editor.getEditorInput())) {
									dirtyEditors.add(editor);
									dirtyEditorsInput.add(editor.getEditorInput());
								}
							}
						}
					}
				}
				if (dirtyEditors.size() > 0) {
					IWorkbenchWindow w = getActiveWorkbenchWindow();
					if (w == null)
						w = windows[0];
					result[0] = EditorManager.saveAll(dirtyEditors, finalConfirm, w);
				}
			}
		});
		return result[0];
	}

	/**
	 * Opens a new workbench window and page with a specific perspective.
	 * 
	 * Assumes that busy cursor is active.
	 */
	private IWorkbenchWindow busyOpenWorkbenchWindow(String perspID, IAdaptable input)
		throws WorkbenchException {
		// Create a workbench window (becomes active window)
		WorkbenchWindow newWindow = newWorkbenchWindow();
		newWindow.create(); // must be created before adding to window manager
		windowManager.add(newWindow);
		getCommandSupport().registerForKeyBindings(newWindow.getShell(), false);

		// Create the initial page.
		try {
			newWindow.busyOpenPage(perspID, input);
		} catch (WorkbenchException e) {
			windowManager.remove(newWindow);
			throw e;
		}

		// Open after opening page, to avoid flicker.
		newWindow.open();

		return newWindow;
	}

	/*
	 * (non-Javadoc) Method declared on IWorkbench.
	 */
	public boolean close() {
		return close(PlatformUI.RETURN_OK, false);
	}
	/**
	 * Closes the workbench, returning the given return code from the run
	 * method. If forced, the workbench is closed no matter what.
	 * 
	 * @param returnCode
	 *            {@link PlatformUI#RETURN_OK RETURN_OK}for normal exit;
	 *            {@link PlatformUI#RETURN_RESTART RETURN_RESTART}if the
	 *            workbench was terminated with a call to
	 *            {@link IWorkbench#restart IWorkbench.restart};
	 *            {@link PlatformUI#RETURN_UNSTARTABLE RETURN_UNSTARTABLE}if
	 *            the workbench could not be started; other values reserved for
	 *            future use
	 * @param force
	 *            true to force the workbench close, and false for a "soft"
	 *            close that can be canceled
	 * @return true if the close was successful, and false if the close was
	 *         canceled
	 */
	/* package */
	boolean close(int returnCode, final boolean force) {
		this.returnCode = returnCode;
		final boolean[] ret = new boolean[1];
		BusyIndicator.showWhile(null, new Runnable() {
			public void run() {
				ret[0] = busyClose(force);
			}
		});
		return ret[0];
	}

	/*
	 * (non-Javadoc) Method declared on IWorkbench.
	 */
	public IWorkbenchWindow getActiveWorkbenchWindow() {
		// Display will be null if SWT has not been initialized or
		// this method was called from wrong thread.
		// @issue if this is called from the wrong thread, this should fail,
		// not return null -- general workbench thread safety issue
		Display display = Display.getCurrent();
		if (display == null)
			return null;

		// Look at the current shell and up its parent
		// hierarchy for a workbench window.
		Control shell = display.getActiveShell();
		while (shell != null) {
			Object data = shell.getData();
			if (data instanceof IWorkbenchWindow)
				return (IWorkbenchWindow) data;
			shell = shell.getParent();
		}

		// Look for the window that was last known being
		// the active one
		WorkbenchWindow win = getActivatedWindow();
		if (win != null) {
			return win;
		}

		// Look at all the shells and pick the first one
		// that is a workbench window.
		Shell shells[] = display.getShells();
		for (int i = 0; i < shells.length; i++) {
			Object data = shells[i].getData();
			if (data instanceof IWorkbenchWindow)
				return (IWorkbenchWindow) data;
		}

		// Can't find anything!
		return null;
	}

	/*
	 * Returns the editor history.
	 */
	protected EditorHistory getEditorHistory() {
		if (editorHistory == null) {
			editorHistory = new EditorHistory();
		}
		return editorHistory;
	}
	/*
	 * (non-Javadoc) Method declared on IWorkbench.
	 */
	public IEditorRegistry getEditorRegistry() {
		return WorkbenchPlugin.getDefault().getEditorRegistry();
	}

	/*
	 * Returns the number for a new window. This will be the first number > 0
	 * which is not used to identify another window in the workbench.
	 */
	private int getNewWindowNumber() {
		// Get window list.
		Window[] windows = windowManager.getWindows();
		int count = windows.length;

		// Create an array of booleans (size = window count).
		// Cross off every number found in the window list.
		boolean checkArray[] = new boolean[count];
		for (int nX = 0; nX < count; nX++) {
			if (windows[nX] instanceof WorkbenchWindow) {
				WorkbenchWindow ww = (WorkbenchWindow) windows[nX];
				int index = ww.getNumber() - 1;
				if (index >= 0 && index < count)
					checkArray[index] = true;
			}
		}

		// Return first index which is not used.
		// If no empty index was found then every slot is full.
		// Return next index.
		for (int index = 0; index < count; index++) {
			if (!checkArray[index])
				return index + 1;
		}
		return count + 1;
	}

	/*
	 * (non-Javadoc) Method declared on IWorkbench.
	 */
	public IPerspectiveRegistry getPerspectiveRegistry() {
		return WorkbenchPlugin.getDefault().getPerspectiveRegistry();
	}

	/*
	 * (non-Javadoc) Method declared on IWorkbench.
	 */
	public PreferenceManager getPreferenceManager() {
		return WorkbenchPlugin.getDefault().getPreferenceManager();
	}
	/*
	 * (non-Javadoc) Method declared on IWorkbench.
	 */
	public IPreferenceStore getPreferenceStore() {
		return WorkbenchPlugin.getDefault().getPreferenceStore();
	}

	/*
	 * (non-Javadoc) Method declared on IWorkbench.
	 */
	public ISharedImages getSharedImages() {
		return WorkbenchPlugin.getDefault().getSharedImages();
	}
	/**
	 * Returns the window manager for this workbench.
	 * 
	 * @return the window manager
	 */
	/* package */
	WindowManager getWindowManager() {
		return windowManager;
	}

	/*
	 * Answer the workbench state file.
	 */
	private File getWorkbenchStateFile() {
		IPath path = WorkbenchPlugin.getDefault().getStateLocation();
		path = path.append(DEFAULT_WORKBENCH_STATE_FILENAME);
		return path.toFile();
	}

	/*
	 * (non-Javadoc) Method declared on IWorkbench.
	 */
	public int getWorkbenchWindowCount() {
		return windowManager.getWindowCount();
	}

	/*
	 * (non-Javadoc) Method declared on IWorkbench.
	 */
	public IWorkbenchWindow[] getWorkbenchWindows() {
		Window[] windows = windowManager.getWindows();
		IWorkbenchWindow[] dwindows = new IWorkbenchWindow[windows.length];
		System.arraycopy(windows, 0, dwindows, 0, windows.length);
		return dwindows;
	}

	/*
	 * (non-Javadoc) Method declared on IWorkbench.
	 */
	public IWorkingSetManager getWorkingSetManager() {
		return WorkbenchPlugin.getDefault().getWorkingSetManager();
	}

	/**
	 * Initializes the workbench now that the display is created.
	 * 
	 * @param windowImage
	 *            the descriptor of the image to be used in the corner of each
	 *            window, or <code>null</code> if none
	 * @return true if init succeeded.
	 */
	private boolean init(ImageDescriptor windowImage, Display display) {
		// setup debug mode if required.
		if (WorkbenchPlugin.getDefault().isDebugging()) {
			WorkbenchPlugin.DEBUG = true;
			ModalContext.setDebugMode(true);
		}

		// create workbench window manager
		windowManager = new WindowManager();

		IIntroRegistry introRegistry = getIntroRegistry();
		if (introRegistry.getIntroCount() > 0) {
			introDescriptor = (IntroDescriptor) introRegistry.getIntros()[0];
		}		
		
		// begin the initialization of the activity, command, and context
		// mangers

		workbenchActivitySupport = new WorkbenchActivitySupport();
		workbenchCommandSupport = new WorkbenchCommandSupport(this);
		workbenchContextSupport = new WorkbenchContextSupport();

		workbenchCommandSupport.getCommandManager().addCommandManagerListener(
			workbenchCommandsAndContexts.commandManagerListener);

		workbenchContextSupport.getContextManager().addContextManagerListener(
			workbenchCommandsAndContexts.contextManagerListener);

		// establish relationship between jface and the command manager
		CommandResolver.getInstance().setCommandResolver(new CommandResolver.ICallback() {

			public Integer getAccelerator(String commandId) {
				Integer accelerator = null;
				ICommand command =
					workbenchCommandSupport.getCommandManager().getCommand(commandId);

				if (command.isDefined()) {
					List keySequenceBindings = command.getKeySequenceBindings();
					final int size = keySequenceBindings.size();
					
					for (int i = 0; i < size; i++) {
						IKeySequenceBinding keySequenceBinding =
							(IKeySequenceBinding) keySequenceBindings.get(i);
						KeySequence keySequence = keySequenceBinding.getKeySequence();
						List keyStrokes = keySequence.getKeyStrokes();

						if (keyStrokes.size() == 1) {
							KeyStroke keyStroke = (KeyStroke) keyStrokes.get(0);
							accelerator =
								new Integer(SWTKeySupport.convertKeyStrokeToAccelerator(keyStroke));
							break;
						}
					}
				}

				return accelerator;
			}

			public String getAcceleratorText(String commandId) {
				String acceleratorText = null;
				ICommand command =
					workbenchCommandSupport.getCommandManager().getCommand(commandId);

				if (command.isDefined()) {
					List keySequenceBindings = command.getKeySequenceBindings();

					if (!keySequenceBindings.isEmpty()) {
						IKeySequenceBinding keySequenceBinding =
							(IKeySequenceBinding) keySequenceBindings.get(0);
						acceleratorText = keySequenceBinding.getKeySequence().format();
					}
				}

				return acceleratorText;
			}

			public boolean isAcceleratorInUse(int accelerator) {
				KeySequence keySequence =
					KeySequence.getInstance(
						SWTKeySupport.convertAcceleratorToKeyStroke(accelerator));
				return workbenchCommandSupport.getCommandManager().isPerfectMatch(keySequence)
					|| workbenchCommandSupport.getCommandManager().isPartialMatch(keySequence);
			}

			public final boolean isActive(final String commandId) {
				if (commandId != null) {
					final ICommand command =
						workbenchCommandSupport.getCommandManager().getCommand(commandId);

					if (command != null)
						return command.isDefined() && workbenchActivitySupport.getActivityManager().getIdentifier(command.getId()).isEnabled();
				}

				return true;
			}
		});

		addWindowListener(workbenchCommandsAndContexts.windowListener);
		workbenchCommandsAndContexts.commandHandlerServiceChanged();		
		workbenchCommandsAndContexts.contextActivationServiceChanged();

		// end the initialization of the activity, command, and context
		// managers

		// allow the workbench configurer to initialize
		getWorkbenchConfigurer().init();

		initializeImages(windowImage);
		initializeFonts();
		initializeColors();
		initializeApplicationColors();

		// now that the workbench is sufficiently initialized, let the advisor
		// have a turn.
		advisor.initialize(getWorkbenchConfigurer());

		// configure use of color icons in toolbars
		boolean useColorIcons = getPreferenceStore().getBoolean(IPreferenceConstants.COLOR_ICONS);
		ActionContributionItem.setUseColorIconsInToolbars(useColorIcons);

		// initialize workbench single-click vs double-click behavior
		initializeSingleClickOption();

		// deadlock code
		boolean avoidDeadlock = true;

		String[] commandLineArgs = Platform.getCommandLineArgs();
		for (int i = 0; i < commandLineArgs.length; i++) {
			if (commandLineArgs[i].equalsIgnoreCase("-allowDeadlock")) //$NON-NLS-1$
				avoidDeadlock = false;
		}

		if (avoidDeadlock) {
			UILockListener uiLockListener = new UILockListener(display);
			Platform.getJobManager().setLockListener(uiLockListener);
			display.setSynchronizer(new UISynchronizer(display, uiLockListener));
		}

		// initialize activity helper. TODO why does this belong here and not
		// up further in the main initialization section for activities?
		activityHelper = ActivityPersistanceHelper.getInstance();

		// attempt to restore a previous workbench state
		try {
			UIStats.start(UIStats.RESTORE_WORKBENCH, "Workbench"); //$NON-NLS-1$

			advisor.preStartup();

			int restoreCode = openPreviousWorkbenchState();
			if (restoreCode == RESTORE_CODE_EXIT) {
				return false;
			}
			if (restoreCode == RESTORE_CODE_RESET) {
				openFirstTimeWindow();
			}
		} finally {
			UIStats.end(UIStats.RESTORE_WORKBENCH, "Workbench"); //$NON-NLS-1$
		}

		forceOpenPerspective();

		isStarting = false;
		return true;
	}

	/**
	 * Initialize colors defined by the new colorDefinitions extension point.
	 * Note this will be rolled into initializeColors() at some point.
	 * 
	 * @since 3.0
	 */
	private void initializeApplicationColors() {
		//Iterate through the definitions and initialize thier
		//defaults in the preference store.
		ColorDefinition[] definitions = ColorDefinition.getDefinitions();
				
		initializeApplicationColors(definitions);
	}

	/**
	 * For dynamic UI
	 * 
	 * @param definitions the color definitions to initialize
	 * @since 3.0
	 */
	public void initializeApplicationColors(ColorDefinition[] definitions) {
		IPreferenceStore store = getPreferenceStore();
		ColorRegistry registry = JFaceResources.getColorRegistry();
	
		// sort the definitions by dependant ordering so that we process 
		// ancestors before children.		
		ColorDefinition [] copyOfDefinitions = new ColorDefinition[definitions.length];
		System.arraycopy(definitions, 0, copyOfDefinitions, 0, definitions.length);
		Arrays.sort(copyOfDefinitions, ColorDefinition.HIERARCHY_COMPARATOR);

		for (int i = 0; i < copyOfDefinitions.length; i++) {
			ColorDefinition definition = copyOfDefinitions[i];
			installColor(definition, registry, store);
		}
	}

	/**
	 * Installs the given color in the color registry.
	 * 
	 * @param definition
	 *            the color definition
	 * @param registry
	 *            the color registry
	 * @param store
	 *            the preference store from which to obtain color data
	 */
	private void installColor(
		ColorDefinition definition,
		ColorRegistry registry,
		IPreferenceStore store) {
				
		String id = definition.getId();
		RGB color = PreferenceConverter.getColor(store, id);
		if (color == PreferenceConverter.COLOR_DEFAULT_DEFAULT) {
			// process RGB if no good value is set.
			color = StringConverter.asRGB(definition.getValue(), null);

			if (color != null) 
				PreferenceConverter.setDefault(
						store, 
						id, 
						color);
			else { // we have a default value.  Get it.
				color = PreferenceConverter.getColor(store, definition.getDefaultsTo());
				if (color != null) 
					PreferenceConverter.setDefault(
							store, 
							id, 
							color);
				}
		}

		if (color != null) {
			registry.put(id, color);
		}
	}

	private void initializeSingleClickOption() {
		IPreferenceStore store = WorkbenchPlugin.getDefault().getPreferenceStore();
		boolean openOnSingleClick = store.getBoolean(IPreferenceConstants.OPEN_ON_SINGLE_CLICK);
		boolean selectOnHover = store.getBoolean(IPreferenceConstants.SELECT_ON_HOVER);
		boolean openAfterDelay = store.getBoolean(IPreferenceConstants.OPEN_AFTER_DELAY);
		int singleClickMethod =
			openOnSingleClick ? OpenStrategy.SINGLE_CLICK : OpenStrategy.DOUBLE_CLICK;
		if (openOnSingleClick) {
			if (selectOnHover)
				singleClickMethod |= OpenStrategy.SELECT_ON_HOVER;
			if (openAfterDelay)
				singleClickMethod |= OpenStrategy.ARROW_KEYS_OPEN;
		}
		OpenStrategy.setOpenMethod(singleClickMethod);
	}

	/*
	 * Initializes the workbench fonts with the stored values.
	 * 
	 * TODO: Investigate fix for Bug 45943.  Because of how the font values are 
	 * loaded, the provided fix may be adequate but it proved insufficient for 
	 * the color registry.  
	 */
	private void initializeFonts() {
		//Iterate through the definitions and initialize thier
		//defaults in the preference store.
		FontDefinition[] definitions = FontDefinition.getDefinitions();
		initializeFonts(definitions);
	}
	
	/**
	 * For dynamic UI
	 * 
	 * @param definitions the fonts to initialize
	 * @since 3.0
	 */
	public void initializeFonts(FontDefinition[] definitions) {
		FontRegistry registry = JFaceResources.getFontRegistry();
		IPreferenceStore store = getPreferenceStore();		
		ArrayList fontsToSet = new ArrayList();

		for (int i = 0; i < definitions.length; i++) {
			installFont(definitions[i].getId(), registry, store);
		}

		// post-process the defaults to allow for out-of-order specification.
		for (int i = 0; i < definitions.length; i++) {
			FontDefinition definition = definitions[i];
			String defaultsTo = definition.getDefaultsTo();
			if (defaultsTo != null) {
				PreferenceConverter.setDefault(
					store,
					definition.getId(),
					PreferenceConverter.getDefaultFontDataArray(store, defaultsTo));

				//If there is no value in the registry pass though the mapping
				if (!registry.hasValueFor(definition.getId())) {
					fontsToSet.add(definition);
				}
			}
		}

		/*
		 * Now that all of the font have been initialized anything that is
		 * still at its defaults and has a defaults to needs to have its value
		 * set in the registry. Post process to be sure that all of the fonts
		 * have the correct setting before there is an update.
		 */
		Iterator updateIterator = fontsToSet.iterator();
		while (updateIterator.hasNext()) {
			FontDefinition update = (FontDefinition) updateIterator.next();
			registry.put(update.getId(), registry.getFontData(update.getDefaultsTo()));
		}
	}

	/*
	 * Installs the given font in the font registry.
	 * 
	 * @param fontKey the font key 
	 * @param registry the font registry 
	 * @param store the preference store from which to obtain font data
	 */
	private void installFont(String fontKey, FontRegistry registry, IPreferenceStore store) {
		if (store.isDefault(fontKey))
			return;
		FontData[] font = PreferenceConverter.getFontDataArray(store, fontKey);
		registry.put(fontKey, font);
	}

	/*
	 * Initialize the workbench images.
	 * 
	 * @since 3.0
	 */
	private void initializeImages(ImageDescriptor windowImage) {
		if (windowImage != null) {
			WorkbenchImages.getImageRegistry().put(
				IWorkbenchGraphicConstants.IMG_OBJS_DEFAULT_PROD,
				windowImage);
			Image image =
				WorkbenchImages.getImage(IWorkbenchGraphicConstants.IMG_OBJS_DEFAULT_PROD);
			if (image != null) {
				Window.setDefaultImage(image);
			}
		} else {
			// Avoid setting a missing image as the window default image
			WorkbenchImages.getImageRegistry().put(
				IWorkbenchGraphicConstants.IMG_OBJS_DEFAULT_PROD,
				ImageDescriptor.getMissingImageDescriptor());
		}
	}

	/*
	 * Initialize the workbench colors.
	 * 
	 * @since 3.0
	 */
	private void initializeColors() {
		// @issue some colors are generic; some are app-specific
		WorkbenchColors.startup();
	}

	/**
	 * Returns <code>true</code> if the workbench is in the process of
	 * closing.
	 */
	public boolean isClosing() {
		return isClosing;
	}

	/*
	 * Returns true if the workbench is in the process of starting
	 */
	/* package */
	boolean isStarting() {
		return isStarting;
	}

	/*
	 * Creates a new workbench window.
	 * 
	 * @return the new workbench window
	 */
	private WorkbenchWindow newWorkbenchWindow() {
		return new WorkbenchWindow(getNewWindowNumber());
	}

	/*
	 * If a perspective was specified on the command line (-perspective) then
	 * force that perspective to open in the active window.
	 */
	private void forceOpenPerspective() {
		if (getWorkbenchWindowCount() == 0) {
			// there should be an open window by now, bail out.
			return;
		}

		String perspId = null;
		String[] commandLineArgs = Platform.getCommandLineArgs();
		for (int i = 0; i < commandLineArgs.length - 1; i++) {
			if (commandLineArgs[i].equalsIgnoreCase("-perspective")) { //$NON-NLS-1$
				perspId = commandLineArgs[i + 1];
				break;
			}
		}
		if (perspId == null) {
			return;
		}
		IPerspectiveDescriptor desc = getPerspectiveRegistry().findPerspectiveWithId(perspId);
		if (desc == null) {
			return;
		}

		IWorkbenchWindow win = getActiveWorkbenchWindow();
		if (win == null) {
			win = getWorkbenchWindows()[0];
		}
		try {
			showPerspective(perspId, win);
		} catch (WorkbenchException e) {
			String msg = "Workbench exception showing specified command line perspective on startup."; //$NON-NLS-1$
			WorkbenchPlugin.log(msg, new Status(IStatus.ERROR, PlatformUI.PLUGIN_ID, 0, msg, e));
		}
	}

	/*
	 * Create the initial workbench window.
	 */
	private void openFirstTimeWindow() {

		// create the workbench window
		WorkbenchWindow newWindow = newWorkbenchWindow();
		newWindow.create();
		windowManager.add(newWindow);
		getCommandSupport().registerForKeyBindings(newWindow.getShell(), false);

		// Create the initial page.
		try {
			newWindow.openPage(
				getPerspectiveRegistry().getDefaultPerspective(),
				getDefaultPageInput());
		} catch (WorkbenchException e) {
			ErrorDialog.openError(newWindow.getShell(), WorkbenchMessages.getString("Problems_Opening_Page"), //$NON-NLS-1$
			e.getMessage(), e.getStatus());
		}
		newWindow.open();
	}

	/*
	 * Create the workbench UI from a persistence file.
	 * 
	 * @return RESTORE_CODE_OK if a window was opened, RESTORE_CODE_RESET if no
	 * window was opened but one should be, and RESTORE_CODE_EXIT if the
	 * workbench should close immediately
	 */
	private int openPreviousWorkbenchState() {

		if (!getWorkbenchConfigurer().getSaveAndRestore()) {
			return RESTORE_CODE_RESET;
		}
		// Read the workbench state file.
		final File stateFile = getWorkbenchStateFile();
		// If there is no state file cause one to open.
		if (!stateFile.exists())
			return RESTORE_CODE_RESET;

		final int result[] = { RESTORE_CODE_OK };
		Platform.run(new SafeRunnable(WorkbenchMessages.getString("ErrorReadingState")) { //$NON-NLS-1$
			public void run() throws Exception {
				FileInputStream input = new FileInputStream(stateFile);
				BufferedReader reader = new BufferedReader(new InputStreamReader(input, "utf-8")); //$NON-NLS-1$
				IMemento memento = XMLMemento.createReadRoot(reader);

				// Validate known version format
				String version = memento.getString(IWorkbenchConstants.TAG_VERSION);
				boolean valid = false;
				for (int i = 0; i < VERSION_STRING.length; i++) {
					if (VERSION_STRING[i].equals(version)) {
						valid = true;
						break;
					}
				}
				if (!valid) {
					reader.close();
					MessageDialog.openError((Shell) null, WorkbenchMessages.getString("Restoring_Problems"), //$NON-NLS-1$
					WorkbenchMessages.getString("Invalid_workbench_state_ve")); //$NON-NLS-1$
					stateFile.delete();
					result[0] = RESTORE_CODE_RESET;
					return;
				}

				// Validate compatible version format
				// We no longer support the release 1.0 format
				if (VERSION_STRING[0].equals(version)) {
					reader.close();
						boolean ignoreSavedState = new MessageDialog(null, WorkbenchMessages.getString("Workbench.incompatibleUIState"), //$NON-NLS-1$
		null, WorkbenchMessages.getString("Workbench.incompatibleSavedStateVersion"), //$NON-NLS-1$
	MessageDialog.WARNING,
		new String[] { IDialogConstants.OK_LABEL, IDialogConstants.CANCEL_LABEL },
		0).open() == 0;
					// OK is the default
					if (ignoreSavedState) {
						stateFile.delete();
						result[0] = RESTORE_CODE_RESET;
					} else {
						result[0] = RESTORE_CODE_EXIT;
					}
					return;
				}

				// Restore the saved state
				IStatus restoreResult = restoreState(memento);
				reader.close();
				if (restoreResult.getSeverity() == IStatus.ERROR) {
					ErrorDialog.openError(null, WorkbenchMessages.getString("Workspace.problemsTitle"), //$NON-NLS-1$
					WorkbenchMessages.getString("Workbench.problemsRestoringMsg"), //$NON-NLS-1$
					restoreResult);
				}
			}
			public void handleException(Throwable e) {
				super.handleException(e);
				result[0] = RESTORE_CODE_RESET;
				stateFile.delete();
			}

		});
		// ensure at least one window was opened
		if (result[0] == RESTORE_CODE_OK && windowManager.getWindows().length == 0)
			result[0] = RESTORE_CODE_RESET;
		return result[0];
	}

	/*
	 * (non-Javadoc) Method declared on IWorkbench.
	 */
	public IWorkbenchWindow openWorkbenchWindow(IAdaptable input) throws WorkbenchException {
		return openWorkbenchWindow(getPerspectiveRegistry().getDefaultPerspective(), input);
	}

	/*
	 * (non-Javadoc) Method declared on IWorkbench.
	 */
	public IWorkbenchWindow openWorkbenchWindow(final String perspID, final IAdaptable input)
		throws WorkbenchException {
		// Run op in busy cursor.
		final Object[] result = new Object[1];
		BusyIndicator.showWhile(null, new Runnable() {
			public void run() {
				try {
					result[0] = busyOpenWorkbenchWindow(perspID, input);
				} catch (WorkbenchException e) {
					result[0] = e;
				}
			}
		});
		if (result[0] instanceof IWorkbenchWindow) {
			return (IWorkbenchWindow) result[0];
		} else if (result[0] instanceof WorkbenchException) {
			throw (WorkbenchException) result[0];
		} else {
			throw new WorkbenchException(WorkbenchMessages.getString("Abnormal_Workbench_Conditi")); //$NON-NLS-1$
		}
	}

	/*
	 * Record the workbench UI in a document
	 */
	private XMLMemento recordWorkbenchState() {
		XMLMemento memento = XMLMemento.createWriteRoot(IWorkbenchConstants.TAG_WORKBENCH);
		IStatus status = saveState(memento);
		if (status.getSeverity() != IStatus.OK) {
			ErrorDialog.openError((Shell) null, WorkbenchMessages.getString("Workbench.problemsSaving"), //$NON-NLS-1$
			WorkbenchMessages.getString("Workbench.problemsSavingMsg"), //$NON-NLS-1$
			status);
		}
		return memento;
	}

	/*
	 * (non-Javadoc) Method declared on IWorkbench.
	 */
	public boolean restart() {
		// this is the return code from run() to trigger a restart
		return close(PlatformUI.RETURN_RESTART, false);
	}

	/*
	 * Restores the state of the previously saved workbench
	 */
	private IStatus restoreState(IMemento memento) {

		MultiStatus result = new MultiStatus(PlatformUI.PLUGIN_ID, IStatus.OK, WorkbenchMessages.getString("Workbench.problemsRestoring"), null); //$NON-NLS-1$
		IMemento childMem;
		try {
			UIStats.start(UIStats.RESTORE_WORKBENCH, "MRUList"); //$NON-NLS-1$
			IMemento mruMemento = memento.getChild(IWorkbenchConstants.TAG_MRU_LIST); //$NON-NLS-1$
			if (mruMemento != null) {
				result.add(getEditorHistory().restoreState(mruMemento));
			}
		} finally {
			UIStats.end(UIStats.RESTORE_WORKBENCH, "MRUList"); //$NON-NLS-1$
		}
		// Get the child windows.
		IMemento[] children = memento.getChildren(IWorkbenchConstants.TAG_WINDOW);

		// Read the workbench windows.
		for (int x = 0; x < children.length; x++) {
			childMem = children[x];
			WorkbenchWindow newWindow = newWorkbenchWindow();
			newWindow.create();
			getCommandSupport().registerForKeyBindings(newWindow.getShell(), false);

			// allow the application to specify an initial perspective to open
			// @issue temporary workaround for ignoring initial perspective
			//			String initialPerspectiveId =
			// getAdvisor().getInitialWindowPerspectiveId();
			//			if (initialPerspectiveId != null) {
			//				IPerspectiveDescriptor desc =
			// getPerspectiveRegistry().findPerspectiveWithId(initialPerspectiveId);
			//				result.merge(newWindow.restoreState(childMem, desc));
			//			}
			result.merge(newWindow.restoreState(childMem, null));
			windowManager.add(newWindow);
			try {
				getAdvisor().postWindowRestore(newWindow.getWindowConfigurer());
			} catch (WorkbenchException e) {
				result.add(e.getStatus());
			}
			newWindow.open();
		}
		return result;
	}

	/**
	 * Returns an array of all plugins that extend the <code>org.eclipse.ui.startup</code>
	 * extension point.
	 */
	public IPluginDescriptor[] getEarlyActivatedPlugins() {
		IPluginRegistry registry = Platform.getPluginRegistry();
		IExtensionPoint point =
			registry.getExtensionPoint(PlatformUI.PLUGIN_ID, IWorkbenchConstants.PL_STARTUP);
		IExtension[] extensions = point.getExtensions();
		IPluginDescriptor result[] = new IPluginDescriptor[extensions.length];
		for (int i = 0; i < extensions.length; i++) {
			result[i] = extensions[i].getDeclaringPluginDescriptor();
		}
		return result;
	}

	/*
	 * Starts all plugins that extend the <code> org.eclipse.ui.startup </code>
	 * extension point, and that the user has not disabled via the preference
	 * page.
	 */
	private void startPlugins() {
		Runnable work = new Runnable() {
			IPreferenceStore store = getPreferenceStore();
			final String pref =
				store.getString(IPreferenceConstants.PLUGINS_NOT_ACTIVATED_ON_STARTUP);
			public void run() {
				IPluginRegistry registry = Platform.getPluginRegistry();
				IExtensionPoint point =
					registry.getExtensionPoint(
						PlatformUI.PLUGIN_ID,
						IWorkbenchConstants.PL_STARTUP);
				IExtension[] extensions = point.getExtensions();
				for (int i = 0; i < extensions.length; i++) {
					IExtension extension = extensions[i];
					// Look for the class attribute in the startup element
					// first
					IConfigurationElement[] configElements = extension.getConfigurationElements();
					// There should only be one configuration element and it
					// should
					// be named "startup".
					IConfigurationElement startupElement = null;
					for (int j = 0; j < configElements.length && startupElement == null; j++) {
						if (configElements[j].getName().equals(IWorkbenchConstants.TAG_STARTUP)) {
							startupElement = configElements[j];
						}
					}
					final IConfigurationElement startElement = startupElement;
					final String startupName;
					if (startElement != null) {
						// This will cause startupName to be null if
						// the class attribute does not exist.
						startupName = startElement.getAttribute(IWorkbenchConstants.TAG_CLASS);
					} else {
						startupName = null;
					}
					// If the startup element doesn't specify a class, use the
					// plugin class
					final IPluginDescriptor pluginDescriptor =
						extension.getDeclaringPluginDescriptor();
					SafeRunnable code = new SafeRunnable() {
						public void run() throws Exception {
							String id =
								pluginDescriptor.getUniqueIdentifier()
									+ IPreferenceConstants.SEPARATOR;
							if (pref.indexOf(id) < 0) {
								IStartup startup = null;
								if (startupName == null) {
									Plugin plugin = pluginDescriptor.getPlugin();
									startup = (IStartup) plugin;
								} else {
									startup =
										(IStartup) WorkbenchPlugin.createExtension(
											startElement,
											IWorkbenchConstants.TAG_CLASS);
								}
								startup.earlyStartup();
							}
						}
						public void handleException(Throwable exception) {
							WorkbenchPlugin.log("Unhandled Exception", new Status(IStatus.ERROR, PlatformUI.PLUGIN_ID, 0, "Unhandled Exception", exception)); //$NON-NLS-1$ //$NON-NLS-2$
						}
					};
					Platform.run(code);
				}
			}
		};

		Thread thread = new Thread(work);
		thread.start();
	}

	/**
	 * Internal method for running the workbench UI. This entails processing
	 * and dispatching events until the workbench is closed or restarted.
	 * 
	 * @return return code {@link PlatformUI#RETURN_OK RETURN_OK}for normal
	 *         exit; {@link PlatformUI#RETURN_RESTART RETURN_RESTART}if the
	 *         workbench was terminated with a call to
	 *         {@link IWorkbench#restart IWorkbench.restart};
	 *         {@link PlatformUI#RETURN_UNSTARTABLE RETURN_UNSTARTABLE}if the
	 *         workbench could not be started; other values reserved for future
	 *         use
	 * @since 3.0
	 */
	private int runUI() {
		UIStats.start(UIStats.START_WORKBENCH, "Workbench"); //$NON-NLS-1$

		try {
			// react to display close event by closing workbench nicely
			display.addListener(SWT.Close, new Listener() {
				public void handleEvent(Event event) {
					event.doit = close();
				}
			});

			// install backstop to catch exceptions thrown out of event loop
			Window.IExceptionHandler handler = new ExceptionHandler();
			Window.setExceptionHandler(handler);

			// initialize workbench and restore or open one window

			boolean initOK = init(WorkbenchPlugin.getDefault().getWindowImage(), display);

			// drop the splash screen now that a workbench window is up
			Platform.endSplash();

			// let the advisor run its start up code
			if (initOK) {
				advisor.postStartup(); // may trigger a close/restart
			}

			if (initOK && runEventLoop) {
				// start eager plug-ins
				startPlugins();

				display.asyncExec(new Runnable() {
					public void run() {
						UIStats.end(UIStats.START_WORKBENCH, "Workbench"); //$NON-NLS-1$
					}
				});

				getWorkbenchTestable().init(display, this);

				// the event loop
				runEventLoop(handler, display);
			}

			// shutdown in an orderly way after event loop finishes
			shutdown();
		} finally {
			// mandatory clean up
			if (!display.isDisposed()) {
				display.dispose();
			}
		}

		// restart or exit based on returnCode
		Workbench.instance = null;
		return returnCode;
	}

	/*
	 * Runs an event loop for the workbench.
	 */
	private void runEventLoop(Window.IExceptionHandler handler, Display display) {
		runEventLoop = true;
		while (runEventLoop) {
			try {
				if (!display.readAndDispatch()) {
					getAdvisor().eventLoopIdle(display);
				}
			} catch (Throwable t) {
				handler.handleException(t);
			}
		}
	}

	/*
	 * Saves the current state of the workbench so it can be restored later on
	 */
	private IStatus saveState(IMemento memento) {
		MultiStatus result = new MultiStatus(PlatformUI.PLUGIN_ID, IStatus.OK, WorkbenchMessages.getString("Workbench.problemsSaving"), null); //$NON-NLS-1$

		// Save the version number.
		memento.putString(IWorkbenchConstants.TAG_VERSION, VERSION_STRING[1]);

		// Save the workbench windows.
		IWorkbenchWindow[] windows = getWorkbenchWindows();
		for (int nX = 0; nX < windows.length; nX++) {
			WorkbenchWindow window = (WorkbenchWindow) windows[nX];
			IMemento childMem = memento.createChild(IWorkbenchConstants.TAG_WINDOW);
			result.merge(window.saveState(childMem));
		}
		result.add(getEditorHistory().saveState(memento.createChild(IWorkbenchConstants.TAG_MRU_LIST))); //$NON-NLS-1$
		return result;
	}

	/*
	 * Save the workbench UI in a persistence file.
	 */
	private boolean saveMementoToFile(XMLMemento memento) {
		// Save it to a file.
		File stateFile = getWorkbenchStateFile();
		try {
			FileOutputStream stream = new FileOutputStream(stateFile);
			OutputStreamWriter writer = new OutputStreamWriter(stream, "utf-8"); //$NON-NLS-1$
			memento.save(writer);
			writer.close();
		} catch (IOException e) {
			stateFile.delete();
			MessageDialog.openError((Shell) null, WorkbenchMessages.getString("SavingProblem"), //$NON-NLS-1$
			WorkbenchMessages.getString("ProblemSavingState")); //$NON-NLS-1$
			return false;
		}

		// Success !
		return true;
	}

	/*
	 * (non-Javadoc) Method declared on IWorkbench.
	 */
	public IWorkbenchPage showPerspective(String perspectiveId, IWorkbenchWindow window)
		throws WorkbenchException {
		Assert.isNotNull(perspectiveId);

		// If the specified window has the requested perspective open, then the
		// window
		// is given focus and the perspective is shown. The page's input is
		// ignored.
		WorkbenchWindow win = (WorkbenchWindow) window;
		if (win != null) {
			WorkbenchPage page = win.getActiveWorkbenchPage();
			if (page != null) {
				IPerspectiveDescriptor perspectives[] = page.getOpenedPerspectives();
				for (int i = 0; i < perspectives.length; i++) {
					IPerspectiveDescriptor persp = perspectives[i];
					if (perspectiveId.equals(persp.getId())) {
						win.getShell().open();
						page.setPerspective(persp);
						return page;
					}
				}
			}
		}

		// If another window that has the workspace root as input and the
		// requested
		// perpective open and active, then the window is given focus.
		IAdaptable input = getDefaultPageInput();
		IWorkbenchWindow[] windows = getWorkbenchWindows();
		for (int i = 0; i < windows.length; i++) {
			win = (WorkbenchWindow) windows[i];
			if (window != win) {
				WorkbenchPage page = win.getActiveWorkbenchPage();
				if (page != null) {
					boolean inputSame = false;
					if (input == null)
						inputSame = (page.getInput() == null);
					else
						inputSame = input.equals(page.getInput());
					if (inputSame) {
						Perspective persp = page.getActivePerspective();
						if (perspectiveId.equals(persp.getDesc().getId())) {
							Shell shell = win.getShell();
							shell.open();
							if (shell.getMinimized())
								shell.setMinimized(false);
							return page;
						}
					}
				}
			}
		}

		// Otherwise the requested perspective is opened and shown in the
		// specified
		// window or in a new window depending on the current user preference
		// for opening
		// perspectives, and that window is given focus.
		win = (WorkbenchWindow) window;
		if (win != null) {
			IPreferenceStore store = WorkbenchPlugin.getDefault().getPreferenceStore();
			int mode = store.getInt(IPreferenceConstants.OPEN_PERSP_MODE);
			IWorkbenchPage page = win.getActiveWorkbenchPage();
			IPerspectiveDescriptor persp = null;
			if (page != null)
				persp = page.getPerspective();

			// Only open a new window if user preference is set and the window
			// has an active perspective.
			if (IPreferenceConstants.OPM_NEW_WINDOW == mode && persp != null) {
				IWorkbenchWindow newWindow = openWorkbenchWindow(perspectiveId, input);
				return newWindow.getActivePage();
			} else {
				IPerspectiveDescriptor desc =
					getPerspectiveRegistry().findPerspectiveWithId(perspectiveId);
				if (desc == null)
					throw new WorkbenchException(WorkbenchMessages.getString("WorkbenchPage.ErrorRecreatingPerspective")); //$NON-NLS-1$
				win.getShell().open();
				if (page == null)
					page = win.openPage(perspectiveId, input);
				else
					page.setPerspective(desc);
				return page;
			}
		}

		// Just throw an exception....
		throw new WorkbenchException(WorkbenchMessages.format("Workbench.showPerspectiveError", new Object[] { perspectiveId })); //$NON-NLS-1$
	}

	/*
	 * (non-Javadoc) Method declared on IWorkbench.
	 */
	public IWorkbenchPage showPerspective(
		String perspectiveId,
		IWorkbenchWindow window,
		IAdaptable input)
		throws WorkbenchException {
		Assert.isNotNull(perspectiveId);

		// If the specified window has the requested perspective open and the
		// same requested
		// input, then the window is given focus and the perspective is shown.
		boolean inputSameAsWindow = false;
		WorkbenchWindow win = (WorkbenchWindow) window;
		if (win != null) {
			WorkbenchPage page = win.getActiveWorkbenchPage();
			if (page != null) {
				boolean inputSame = false;
				if (input == null)
					inputSame = (page.getInput() == null);
				else
					inputSame = input.equals(page.getInput());
				if (inputSame) {
					inputSameAsWindow = true;
					IPerspectiveDescriptor perspectives[] = page.getOpenedPerspectives();
					for (int i = 0; i < perspectives.length; i++) {
						IPerspectiveDescriptor persp = perspectives[i];
						if (perspectiveId.equals(persp.getId())) {
							win.getShell().open();
							page.setPerspective(persp);
							return page;
						}
					}
				}
			}
		}

		// If another window has the requested input and the requested
		// perpective open and active, then that window is given focus.
		IWorkbenchWindow[] windows = getWorkbenchWindows();
		for (int i = 0; i < windows.length; i++) {
			win = (WorkbenchWindow) windows[i];
			if (window != win) {
				WorkbenchPage page = win.getActiveWorkbenchPage();
				if (page != null) {
					boolean inputSame = false;
					if (input == null)
						inputSame = (page.getInput() == null);
					else
						inputSame = input.equals(page.getInput());
					if (inputSame) {
						Perspective persp = page.getActivePerspective();
						if (perspectiveId.equals(persp.getDesc().getId())) {
							win.getShell().open();
							return page;
						}
					}
				}
			}
		}

		// If the specified window has the same requested input but not the
		// requested
		// perspective, then the window is given focus and the perspective is
		// opened and shown
		// on condition that the user preference is not to open perspectives in
		// a new window.
		win = (WorkbenchWindow) window;
		if (inputSameAsWindow && win != null) {
			IPreferenceStore store = WorkbenchPlugin.getDefault().getPreferenceStore();
			int mode = store.getInt(IPreferenceConstants.OPEN_PERSP_MODE);

			if (IPreferenceConstants.OPM_NEW_WINDOW != mode) {
				IWorkbenchPage page = win.getActiveWorkbenchPage();
				IPerspectiveDescriptor desc =
					getPerspectiveRegistry().findPerspectiveWithId(perspectiveId);
				if (desc == null)
					throw new WorkbenchException(WorkbenchMessages.getString("WorkbenchPage.ErrorRecreatingPerspective")); //$NON-NLS-1$
				win.getShell().open();
				if (page == null)
					page = win.openPage(perspectiveId, input);
				else
					page.setPerspective(desc);
				return page;
			}
		}

		// If the specified window has no active perspective, then open the
		// requested perspective and show the specified window.
		if (win != null) {
			IWorkbenchPage page = win.getActiveWorkbenchPage();
			IPerspectiveDescriptor persp = null;
			if (page != null)
				persp = page.getPerspective();
			if (persp == null) {
				IPerspectiveDescriptor desc =
					getPerspectiveRegistry().findPerspectiveWithId(perspectiveId);
				if (desc == null)
					throw new WorkbenchException(WorkbenchMessages.getString("WorkbenchPage.ErrorRecreatingPerspective")); //$NON-NLS-1$
				win.getShell().open();
				if (page == null)
					page = win.openPage(perspectiveId, input);
				else
					page.setPerspective(desc);
				return page;
			}
		}

		// Otherwise the requested perspective is opened and shown in a new
		// window, and the
		// window is given focus.
		IWorkbenchWindow newWindow = openWorkbenchWindow(perspectiveId, input);
		return newWindow.getActivePage();
	}

	/*
	 * Shuts down the application.
	 */
	private void shutdown() {
		// for dynamic UI 
		InternalPlatform.getDefault().getRegistry().removeRegistryChangeListener(extensionEventHandler);

		// shutdown application-specific portions first
		advisor.postShutdown();

		// shutdown the rest of the workbench
		WorkbenchColors.shutdown();
		JFaceColors.disposeColors();
		if (getDecoratorManager() != null) {
			((DecoratorManager) getDecoratorManager()).shutdown();
		}
		activityHelper.shutdown();
		ProgressManager.getInstance().shutdown();
	}

	/*
	 * (non-Javadoc) Method declared on IWorkbench.
	 */
	public IDecoratorManager getDecoratorManager() {
		return WorkbenchPlugin.getDefault().getDecoratorManager();
	}

	/*
	 * Returns the workbench window which was last known being the active one,
	 * or <code> null </code> .
	 */
	private WorkbenchWindow getActivatedWindow() {
		if (activatedWindow != null) {
			Shell shell = activatedWindow.getShell();
			if (shell != null && !shell.isDisposed()) {
				return activatedWindow;
			}
		}

		return null;
	}

	/*
	 * Sets the workbench window which was last known being the active one, or
	 * <code> null </code> .
	 */
	/* package */
	void setActivatedWindow(WorkbenchWindow window) {
		activatedWindow = window;
	}

	/**
	 * Returns the unique object that applications use to configure the
	 * workbench.
	 * <p>
	 * IMPORTANT This method is declared package-private to prevent regular
	 * plug-ins from downcasting IWorkbench to Workbench and getting hold of
	 * the workbench configurer that would allow them to tamper with the
	 * workbench. The workbench configurer is available only to the
	 * application.
	 * </p>
	 */
	/* package */
	WorkbenchConfigurer getWorkbenchConfigurer() {
		if (workbenchConfigurer == null) {
			workbenchConfigurer = new WorkbenchConfigurer();
		}
		return workbenchConfigurer;
	}

	/**
	 * Returns the workbench advisor that created this workbench.
	 * <p>
	 * IMPORTANT This method is declared package-private to prevent regular
	 * plug-ins from downcasting IWorkbench to Workbench and getting hold of
	 * the workbench advisor that would allow them to tamper with the
	 * workbench. The workbench advisor is internal to the application.
	 * </p>
	 */
	/* package */
	WorkbenchAdvisor getAdvisor() {
		return advisor;
	}

	/*
	 * (non-Javadoc) Method declared on IWorkbench.
	 */
	public Display getDisplay() {
		return display;
	}

	/**
	 * Returns the default perspective id.
	 * 
	 * @return the default perspective id
	 */
	public String getDefaultPerspectiveId() {
		String id = getAdvisor().getInitialWindowPerspectiveId();
		// make sure we the advisor gave us one
		Assert.isNotNull(id);
		return id;
	}

	/**
	 * Returns the default workbench window page input.
	 * 
	 * @return the default window page input or <code>null</code> if none
	 */
	public IAdaptable getDefaultPageInput() {
		return getAdvisor().getDefaultPageInput();
	}

	/**
	 * Returns the id of the preference page that should be presented most
	 * prominently.
	 * 
	 * @return the id of the preference page, or <code>null</code> if none
	 */
	public String getMainPreferencePageId() {
		String id = getAdvisor().getMainPreferencePageId();
		return id;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ui.IWorkbench
	 * @since 3.0
	 */
	public IElementFactory getElementFactory(String factoryId) {
		Assert.isNotNull(factoryId);
		return WorkbenchPlugin.getDefault().getElementFactory(factoryId);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ui.IWorkbench#getProgressService()
	 */
	public IProgressService getProgressService() {
		return ProgressManager.getInstance();
	}

	private IWorkbenchActivitySupport workbenchActivitySupport;
	private IWorkbenchCommandSupport workbenchCommandSupport;
	private IWorkbenchContextSupport workbenchContextSupport;

	public IWorkbenchActivitySupport getActivitySupport() {
		return workbenchActivitySupport;
	}

	public IWorkbenchCommandSupport getCommandSupport() {
		return workbenchCommandSupport;
	}

	public IWorkbenchContextSupport getContextSupport() {
		return workbenchContextSupport;
	}

	/* TODO: reduce visibility, or better - get rid of entirely */
	public WorkbenchCommandsAndContexts workbenchCommandsAndContexts =
		new WorkbenchCommandsAndContexts(this);
	private ActivityPersistanceHelper activityHelper;

	/* (non-Javadoc)
	 * @see org.eclipse.ui.IWorkbench#closeIntro(org.eclipse.ui.intro.IIntroPart)
	 */
	public boolean closeIntro(IIntroPart part) {
		if (introPart == null || !introPart.equals(part))
			return false;
		introPart = null;

        IViewPart introView = getViewIntroAdapterPart();
		if (introView != null) {
			getViewIntroAdapterPart().getSite().getPage().hideView(introView);
		}
		return true;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.IWorkbench#showIntro(org.eclipse.ui.IWorkbenchWindow)
	 */
	public IIntroPart showIntro(IWorkbenchWindow preferredWindow) {
		if (getViewIntroAdapterPart() == null) {
			createIntro((WorkbenchWindow) preferredWindow);
		}
		else {
			try {
				ViewIntroAdapterPart viewPart = getViewIntroAdapterPart();
				WorkbenchPage page = (WorkbenchPage) viewPart.getSite().getPage();
				IPerspectiveDescriptor [] perspDescriptors = page.getOpenedPerspectives();
				for (int i = 0; i < perspDescriptors.length; i++) {
					IPerspectiveDescriptor descriptor = perspDescriptors[i];
					if (page.findPerspective(descriptor).containsView(viewPart)) {
						if (!page.getPerspective().equals(descriptor)) {
							page.setPerspective(descriptor);
						}
						break;
					}
				}
				
				page.getWorkbenchWindow().getShell().setActive();
				page.showView(IIntroConstants.INTRO_VIEW_ID);
			} catch (PartInitException e) {
				WorkbenchPlugin.log(IntroMessages.getString("Intro.could_not_show_part"), new Status(IStatus.ERROR, WorkbenchPlugin.PI_WORKBENCH, IStatus.ERROR, IntroMessages.getString("Intro.could_not_show_part"), e));	//$NON-NLS-1$ //$NON-NLS-2$
			}
		}
		return introPart;
	}


	/**
     * Create a new Intro area (a view, currently) in the provided window.  If there is no intro
     * descriptor for this workbench then no work is done.
     *
	 * @param preferredWindow the window to create the intro in.
	 * @since 3.0
	 */
	private void createIntro(WorkbenchWindow preferredWindow) {
		if (getIntroDescriptor() == null)
			return;
		
		WorkbenchPage workbenchPage = preferredWindow.getActiveWorkbenchPage();
		try {
			workbenchPage.showView(IIntroConstants.INTRO_VIEW_ID);
			setIntroStandby(introPart, false);
		} catch (PartInitException e) {
			WorkbenchPlugin.log(IntroMessages.getString("Intro.could_not_create_part"), new Status(IStatus.ERROR, WorkbenchPlugin.PI_WORKBENCH, IStatus.ERROR, IntroMessages.getString("Intro.could_not_create_part"), e)); //$NON-NLS-1$ //$NON-NLS-2$
		}		
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.IWorkbench#setIntroStandby(org.eclipse.ui.intro.IIntroPart, boolean)
	 */
	public void setIntroStandby(IIntroPart part, boolean standby) {
		if (introPart == null || !introPart.equals(part))
			return;
		
		PartPane pane = ((PartSite)getViewIntroAdapterPart().getSite()).getPane();
		if (standby == !pane.isZoomed()) {
			return;
		}
		
		((WorkbenchPage)getViewIntroAdapterPart().getSite().getPage()).toggleZoom(pane.getPartReference());
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.IWorkbench#isIntroStandby(org.eclipse.ui.intro.IIntroPart)
	 */
	public boolean isIntroStandby(IIntroPart part) {
		if (introPart == null || !introPart.equals(part))
			return false;

		return !((PartSite)getViewIntroAdapterPart().getSite()).getPane().isZoomed();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.IWorkbench#findIntro()
	 */
	public IIntroPart findIntro() {
		return introPart;
	}
	
	/** 
	 * @return the <code>ViewIntroAdapterPart</code> for this workbench, <code>null</code> if it 
     * cannot be found.
	 * @since 3.0
	 */
	private ViewIntroAdapterPart getViewIntroAdapterPart() {
		IWorkbenchWindow [] windows = getWorkbenchWindows();
		for (int i = 0; i < windows.length; i++) {
			IWorkbenchWindow window = windows[i];
			WorkbenchPage page = (WorkbenchPage) window.getActivePage();
			if (page == null) {
				continue;
			}
			IPerspectiveDescriptor [] perspDescs = page.getOpenedPerspectives();
			for (int j = 0; j < perspDescs.length; j++) {
				IPerspectiveDescriptor descriptor = perspDescs[j];
				IViewReference reference = page.findPerspective(descriptor).findView(IIntroConstants.INTRO_VIEW_ID);
				if (reference != null) {
					ViewIntroAdapterPart part = (ViewIntroAdapterPart) reference.getView(false);
					if (part != null)
						return part;
				}
			}
		}
		return null;
	}
		
	/**
	 * @return a new IIntroPart.  This has the side effect of setting the introPart field to the new
	 * value.
	 * @since 3.0
	 */
	/*package*/ IIntroPart createNewIntroPart() throws CoreException {	
		return introPart = introDescriptor == null ? null : introDescriptor.createIntro();
	}
	
	/** 
	 * @return the intro extension for this workbench.
	 * @since 3.0
	 */
	/*package*/ IntroDescriptor getIntroDescriptor() {
		return introDescriptor;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.ui.IWorkbench#getIntroRegistry()
	 */
	public IIntroRegistry getIntroRegistry() {
		return WorkbenchPlugin.getDefault().getIntroRegistry();
	}	
	
	/**
	 * The currently active introPart in this workspace, <code>null</code> if none.
	 */
	private IIntroPart introPart;
	
	/**
	 * The descriptor for the intro extension that is valid for this workspace, <code>null</code> if none.
	 */
	private IntroDescriptor introDescriptor;
}