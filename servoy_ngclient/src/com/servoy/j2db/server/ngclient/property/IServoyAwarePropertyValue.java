/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2014 Servoy BV

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU Affero General Public License as published by the Free
 Software Foundation; either version 3 of the License, or (at your option) any
 later version.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License along
 with this program; if not, see http://www.gnu.org/licenses or write to the Free
 Software Foundation,Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301
 */

package com.servoy.j2db.server.ngclient.property;

import org.sablo.specification.property.ISmartPropertyValue;

import com.servoy.j2db.dataprocessing.IRecordInternal;
import com.servoy.j2db.server.ngclient.property.types.IRecordAwareType;

/**
 * Complex properties that are to be used within Servoy beans - interested in Servoy specific behavior.
 *
 * Usually this value's property type implements {@link IRecordAwareType}.
 *
 * @author acostescu
 */
public interface IServoyAwarePropertyValue extends ISmartPropertyValue
{

	/**
	 * Called when the record a component is bound to changes.<br/>
	 * It can only be called after {@link ISmartPropertyValue#attachToComponent(org.sablo.IChangeListener, org.sablo.WebComponent)}
	 * @param record the new record
	 */
	void pushRecord(IRecordInternal record);

}