/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2012 Servoy BV

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

package com.servoy.extension.dependency;

import com.servoy.extension.VersionStringUtils;

/**
 * Always chooses the highest (compatible if no conflicts) version of a lib.
 * @author acostescu
 */
public class MaxVersionLibChooser implements ILibVersionChooser
{

	public TrackableLibDependencyDeclaration[] chooseLibDeclarations(LibChoice[] libChoices)
	{
		TrackableLibDependencyDeclaration[] results = new TrackableLibDependencyDeclaration[libChoices.length];
		for (int i = 0; i < libChoices.length; i++)
		{
			results[i] = chooseLibDeclaration(libChoices[i]);
		}

		return results;
	}

	/**
	 * Chooses highest version lib declaration from the version list.
	 * @param libChoice the choices available for this a lib.
	 * @return the highest version of the lib.
	 */
	protected TrackableLibDependencyDeclaration chooseLibDeclaration(LibChoice libChoice)
	{
		TrackableLibDependencyDeclaration chosenOne = null;
		for (TrackableLibDependencyDeclaration libVersion : libChoice.libDependencies)
		{
			if (chosenOne == null || (VersionStringUtils.compareVersions(chosenOne.version, libVersion.version) < 0))
			{
				if (libChoice.conflict || isCompatible(libVersion.version, libChoice.libDependencies))
				{
					chosenOne = libVersion;
				}
			}
		}
		return chosenOne;
	}

	protected boolean isCompatible(String libVersion, TrackableLibDependencyDeclaration[] libDependencies)
	{
		boolean compatible = true;
		for (TrackableLibDependencyDeclaration libDep : libDependencies)
		{
			if (!VersionStringUtils.belongsToInterval(libVersion, libDep.minVersion, libDep.maxVersion))
			{
				compatible = false;
				break;
			}
		}
		return compatible;
	}

}
