/*******************************************************************************
 * Copyright (c) 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.ui.internal.ide.actions;

import java.io.File;

import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.internal.ide.ChooseWorkspaceData;
import org.eclipse.ui.internal.ide.ChooseWorkspaceDialog;
import org.eclipse.ui.internal.ide.IDEWorkbenchMessages;

/**
 * Implements the open workspace action. Opens a dialog prompting for a
 * directory and then restarts the IDE on that workspace.
 * 
 * @since 3.0
 */
public class OpenWorkspaceAction extends Action implements
        ActionFactory.IWorkbenchAction {

    private static final String PROP_VM = "eclipse.vm"; //$NON-NLS-1$
    private static final String PROP_VMARGS = "eclipse.vmargs"; //$NON-NLS-1$
    private static final String PROP_COMMANDS = "eclipse.commands"; //$NON-NLS-1$
    private static final String PROP_EXIT_CODE = "eclipse.exitcode"; //$NON-NLS-1$
    private static final String PROP_EXIT_DATA = "eclipse.exitdata"; //$NON-NLS-1$
    private static final String CMD_DATA = "-data"; //$NON-NLS-1$
    private static final String CMD_VMARGS = "-vmargs"; //$NON-NLS-1$
    private static final String NEW_LINE = "\n"; //$NON-NLS-1$

    private IWorkbenchWindow window;

    /**
     * Set this action's definition and text so that it will be used for
     * File->Open Workspace... in the argument window.
     * 
     * @param window
     *            the window in which this action should appear
     */
    public OpenWorkspaceAction(IWorkbenchWindow window) {
        super(IDEWorkbenchMessages.getString("OpenWorkspaceAction.text")); //$NON-NLS-1$

        if (window == null)
            throw new IllegalArgumentException();

        // TODO help?

        this.window = window;
        setToolTipText(IDEWorkbenchMessages
                .getString("OpenWorkspaceAction.toolTip")); //$NON-NLS-1$
        setActionDefinitionId("org.eclipse.ui.file.openWorkspace"); //$NON-NLS-1$
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.eclipse.jface.action.Action#dispose()
     */
    public void run() {
        String path = promptForWorkspace();
        if (path == null)
            return;

        String command_line = buildCommandLine(path);
        if (command_line == null)
            return;

        System.setProperty(PROP_EXIT_CODE, Integer.toString(24));
        System.setProperty(PROP_EXIT_DATA, command_line);
        window.getWorkbench().restart();
    }

    /**
     * Use the ChooseWorkspaceDialog to get the new workspace from the user.
     * 
     * @return a string naming the new workspace and null if cancel was selected
     */
    private String promptForWorkspace() {
        // get the current workspace as the default
        String instanceLoc = Platform.getLocation().toOSString();
        while (instanceLoc.charAt(instanceLoc.length() - 1) == File.separatorChar)
            instanceLoc = instanceLoc.substring(0, instanceLoc.length() - 1);

        ChooseWorkspaceData data = new ChooseWorkspaceData(instanceLoc);
        ChooseWorkspaceDialog dialog
        		= new ChooseWorkspaceDialog(window.getShell(), data, true);
        dialog.prompt(true);

        // return null if the user changed their mind
        String selection = data.getSelection();
        if (selection == null)
            return null;

        // otherwise store the new selection and return the selection
        data.writePersistedData();
        return selection;
    }

    /**
     * Create and return a string with command line options for eclipse.exe that
     * will launch a new workbench that is the same as the currently running
     * one, but using the argument directory as its workspace.
     * 
     * @param workspace
     *            the directory to use as the new workspace
     * @return a string of command line options or null on error
     */
    private String buildCommandLine(String workspace) {
        String property = System.getProperty(PROP_VM);
        if (property == null) {
            MessageDialog
                    .openError(
                            window.getShell(),
                            IDEWorkbenchMessages
                                    .getString("OpenWorkspaceAction.errorTitle"), //$NON-NLS-1$
                            IDEWorkbenchMessages
                                    .format("OpenWorkspaceAction.errorMessage", new Object[] { PROP_VM })); //$NON-NLS-1$
            return null;
        }

        StringBuffer result = new StringBuffer(512);
        result.append(property);
        result.append(NEW_LINE);

        // append the vmargs and commands. Assume that these already end in \n
        String vmargs = System.getProperty(PROP_VMARGS);
        if (vmargs != null) result.append(vmargs);

        // append the rest of the args, replacing or adding -data as required
        property = System.getProperty(PROP_COMMANDS);
        if (property == null) {
            result.append(CMD_DATA);
            result.append(NEW_LINE);
            result.append(workspace);
            result.append(NEW_LINE);
        } else {
            // find the index of the arg to replace its value
            int cmd_data_pos = property.indexOf(CMD_DATA);
            if (cmd_data_pos != -1) {
                cmd_data_pos += CMD_DATA.length() + 1;
                result.append(property.substring(0, cmd_data_pos));
                result.append(workspace);
                result.append(property.substring(property.indexOf('\n',
                        cmd_data_pos)));
            } else {
                result.append(CMD_DATA);
                result.append(NEW_LINE);
                result.append(workspace);
                result.append(NEW_LINE);
                result.append(property);
            }
        }

        // put the vmargs back at the very end (the eclipse.commands property
        // already contains the -vm arg)
        if (vmargs != null) {
            result.append(CMD_VMARGS);
            result.append(NEW_LINE);
            result.append(vmargs);
        }

        return result.toString();
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.eclipse.jface.action.Action#dispose()
     */
    public void dispose() {
        window = null;
    }
}
