/*******************************************************************************
 * Copyright (c) 2008 Matthew Hall and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Matthew Hall - initial API and implementation (bug 194734)
 ******************************************************************************/

package org.eclipse.jface.internal.databinding.swt;

import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.widgets.Control;

/**
 * @since 3.3
 * 
 */
public class ControlFontProperty extends WidgetValueProperty {
	/**
	 * 
	 */
	public ControlFontProperty() {
		super(Font.class);
	}

	public Object getValue(Object source) {
		return ((Control) source).getFont();
	}

	public void setValue(Object source, Object value) {
		((Control) source).setFont((Font) value);
	}

	public String toString() {
		return "Control.font <Font>"; //$NON-NLS-1$
	}
}
