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

/**
 * @since 3.3
 * 
 */
public abstract class WidgetBooleanValueProperty extends WidgetValueProperty {
	WidgetBooleanValueProperty(int[] events) {
		super(events, Boolean.TYPE);
	}

	WidgetBooleanValueProperty(int event) {
		super(event, Boolean.TYPE);
	}

	WidgetBooleanValueProperty() {
		super(Boolean.TYPE);
	}

	public Object getValue(Object source) {
		return doGetBooleanValue(source) ? Boolean.TRUE : Boolean.FALSE;
	}

	public void setValue(Object source, Object value) {
		if (value == null)
			value = Boolean.FALSE;
		doSetBooleanValue(source, ((Boolean) value).booleanValue());
	}

	abstract boolean doGetBooleanValue(Object source);

	abstract void doSetBooleanValue(Object source, boolean value);
}
