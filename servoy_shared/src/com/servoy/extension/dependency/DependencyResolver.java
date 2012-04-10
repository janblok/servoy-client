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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Stack;

import com.servoy.extension.DependencyMetadata;
import com.servoy.extension.ExtensionDependencyDeclaration;
import com.servoy.extension.ExtensionProvider;
import com.servoy.extension.LibDependencyDeclaration;
import com.servoy.extension.VersionStringUtils;

/**
 * Class responsible for resolving extension & lib dependencies.<br>
 * Given an extension id, it is able to generate a valid dependency tree for that extension (not list because any dependency could have multiple compatible versions), if possible using the given ExtensionProvider.<br><br>
 * If that is not possible, it will report the reasons that prevent it.<br><br>
 * The terms "tree path" are used heavily within the documentation of this class. These tree "paths" refer to paths in a virtual tree that is traversed while trying to resolve an extension (through simulated install extension/replace existing installed extension operations).
 *  
 * @author acostescu
 */
@SuppressWarnings("nls")
public class DependencyResolver
{

	private final ExtensionProvider extensionProvider;
	private DependencyMetadata[] installedExtensions;
	private boolean ignoreLibConflicts;

	// the following members may be altered and restored while trying to resolve extensions
	private Map<String, List<LibDependencyDeclaration>> allInstalledLibs; // created from installedExtensions; <libID, array(lib_declarattions)>
	private Map<String, String> allInstalledExtensions; // created from installedExtensions; <extensionID, version>

	private Map<String, String> visitedExtensions;
	private Map<String, List<LibDependencyDeclaration>> visitedLibs;
	private Stack<DependencyMetadata[]> stillToResolve;
	private Stack<ExtensionNode> treePath;

	private List<ExtensionNode[]> results;
	private List<String> reasons; // TODO Change this into a list of objects with more info? Like abandoned extension path/reason type + some arguments used to construct the String message

	/**
	 * See class docs.
	 * @param extensionProvider the extensionProvider to use.
	 */
	public DependencyResolver(ExtensionProvider extensionProvider)
	{
		this.extensionProvider = extensionProvider;
	}

	public synchronized void setIgnoreLibConflicts(boolean ignoreLibConflicts)
	{
		this.ignoreLibConflicts = ignoreLibConflicts;
	}

	/**
	 * Tells this dependency resolver what the currently installed extension are.
	 * @param installedExtensions a map of extension id's to dependency meta-data containing info about all currently installed extensions. 
	 */
	public void setInstalledExtensions(DependencyMetadata[] installedExtensions)
	{
		this.installedExtensions = installedExtensions;
	}

	/**
	 * Returns the extension provider.
	 * @return the extension provider.
	 */
	protected ExtensionProvider getExtensionProvider()
	{
		return extensionProvider;
	}

	/**
	 * Constructs a valid dependency tree, if possible, starting with the given extension id and version.
	 * @param extensionId the id of the extension that should be the tree's root.
	 * @param version the version of the extension that should be the tree's root.
	 */
	public synchronized void resolveDependencies(String extensionId, String version)
	{
		results = null;
		reasons = new ArrayList<String>();

		if (extensionId != null)
		{
			DependencyMetadata[] extension = extensionProvider.getDependencyMetadata(new ExtensionDependencyDeclaration(extensionId, version, version));
			if (extension != null && extension.length == 1)
			{
				processInstalledExtensions(); // prepare working copies of installed extensions/libs

				visitedExtensions = new HashMap<String, String>();
				visitedLibs = new HashMap<String, List<LibDependencyDeclaration>>();
				stillToResolve = new Stack<DependencyMetadata[]>();
				treePath = new Stack<ExtensionNode>();

				resolveDependenciesRecursive(extension[0]);
			}
			else
			{
				reasons.add("Cannot resolve extension ('" + extensionId + "', " + version + ")");
			}
		}
	}

	/**
	 * Can be called after a call to {@link #resolveDependencies(String, String)}.
	 * @return a list of resolved extension paths. Each element of the list is an array of possible extensions to be installed.<br>
	 * If none is found, null will be returned.<br>
	 * If multiple possible extension paths are resolved, you may choose which one of them you want to install.
	 */
	public synchronized List<ExtensionNode[]> getResults()
	{
		return results;
	}

	/**
	 * Can be called after a call to {@link #resolveDependencies(String, String)}.
	 * @return a list of reasons why some tree paths were not resolved. Useful especially when no tree path was resolved ({@link #getResults()} == null).
	 */
	public synchronized String[] getFailReasons()
	{
		return reasons.toArray(new String[reasons.size()]);
	}

	protected void processInstalledExtensions()
	{
		allInstalledLibs = new HashMap<String, List<LibDependencyDeclaration>>();
		allInstalledExtensions = new HashMap<String, String>();
		if (installedExtensions == null) installedExtensions = new DependencyMetadata[0];
		for (DependencyMetadata extension : installedExtensions)
		{
			allInstalledExtensions.put(extension.id, extension.version);
			if (extension.libDependencies != null)
			{
				addToLibsMap(extension.libDependencies, allInstalledLibs);
			}
		}
	}

	/**
	 * Recursively resolves an extension dependency.<br>
	 * It enforces no extension dependency cycles and also checks for lib conflicts that might occur.<br><br>
	 * It also looks for possibilities to update installed extensions so that the given dependency can be installed.
	 * 
	 * @param extension description of the given extension dependency.
	 * @param visitedExtensions keeps track of recursively visited extension ids, to avoid cycles. The map's key is the extension id, the map's value is the version.
	 * @param visitedLibs keeps track of lib dependencies encountered in current tree path.
	 * @return the root node of the resolved dependency tree. Null if the dependency could not be resolved due to missing extension(s)/version(s) or conflict(s).
	 */
	protected void resolveDependenciesRecursive(DependencyMetadata extension)
	{
		int[] pops = new int[] { 0 }; // used to restore "stillToResolve" to it's previous state if necessary; ARRAY to be able to modify it when sent as parameter
		ExtensionNode node = null;
		boolean ok = false;
		LibDependencyDeclaration[][] libsToBeRemovedBecauseOfUpdate = new LibDependencyDeclaration[][] { null }; // if this call will result in an installed extension replacement (update), the old lib dependencies are no longer relevant; once ARRAY to be able to modify it when sent as parameter

		// check compatibility with current servoy installation
		if (extension.servoyDependency == null ||
			VersionStringUtils.belongsToInterval(VersionStringUtils.getCurrentServoyVersion(), extension.servoyDependency.minVersion,
				extension.servoyDependency.maxVersion))
		{
			if (visitedExtensions.containsKey(extension.id))
			{
				// so this extension is already in the dependency tree; check to see if existing version is the same one;
				// if it is then we found a valid tree path; otherwise, this is not a valid tree path
				if (VersionStringUtils.sameVersion(visitedExtensions.get(extension.id), extension.version))
				{
					ok = true;
				}
				else
				{
					// else invalid tree path
					reasons.add("The same extension is required twice due to dependencies, but with different versions (" +
						visitedExtensions.get(extension.id) + ", " + extension.version + "). Extension id '" + extension.id + "'.");
				}
			}
			else if (allInstalledExtensions.containsKey(extension.id))
			{
				// so this extension is already installed; if it is the same one, we are ok, otherwise see if this version can be updated
				if (VersionStringUtils.sameVersion(allInstalledExtensions.get(extension.id), extension.version))
				{
					ok = true;
				}
				else
				{
					node = new ExtensionNode(extension.id, extension.version, ExtensionNode.REPLACE_RESOLVE);
					ok = handleReplace(extension, libsToBeRemovedBecauseOfUpdate, pops);
				}
			}
			else
			{
				// new extension for this tree path (not already present in path, not in installed extensions either);
				node = new ExtensionNode(extension.id, extension.version, ExtensionNode.SIMPLE_DEPENDENCY_RESOLVE);
				ok = handleDependencies(extension, pops);
			}
		}
		else
		{
			// else not compatible with Servoy version - invalid tree path
			reasons.add("Extension " + extension + " is not compatible with current Servoy version (" + VersionStringUtils.getCurrentServoyVersion() + ", " +
				extension.servoyDependency.minVersion + ", " + extension.servoyDependency.maxVersion + ").");
		}

		if (ok)
		{
			if (stillToResolve.size() == 0)
			{
				// YEY, this is a complete valid extension dependency tree path; check that there are no lib conflicts with already installed libs and save it if it's ok
				// we are only doing this here because while recursively creating the tree path, some installed extensions might be replaced with other versions later in the tree path;
				// so we can't check this at each recursive call - just here at the end
				if (checkNoLibConflicts(allInstalledLibs, visitedLibs))
				{
					if (results == null) results = new ArrayList<ExtensionNode[]>();
					if (node != null) treePath.push(node);
					results.add(treePath.toArray(new ExtensionNode[treePath.size()]));
					if (node != null) treePath.pop();
				} // else invalid tree path
			}
			else
			{
				DependencyMetadata[] dependencyVersions = stillToResolve.pop();

				if (node != null)
				{
					// add lib and extension dependencies to this tree path
					visitedExtensions.put(extension.id, extension.version);
					if (extension.libDependencies != null) addToLibsMap(extension.libDependencies, visitedLibs);
					treePath.push(node);
					if (libsToBeRemovedBecauseOfUpdate[0] != null) removeFromLibsMap(libsToBeRemovedBecauseOfUpdate[0], allInstalledLibs);
				} // else it's an already resolved or already installed node

				for (DependencyMetadata dependencyVersion : dependencyVersions)
				{
					resolveDependenciesRecursive(dependencyVersion);
				}

				if (node != null)
				{
					// remove lib and extension dependencies from this tree path
					visitedExtensions.remove(extension.id);
					if (extension.libDependencies != null) removeFromLibsMap(extension.libDependencies, visitedLibs);
					treePath.pop();
					if (libsToBeRemovedBecauseOfUpdate[0] != null) addToLibsMap(libsToBeRemovedBecauseOfUpdate[0], allInstalledLibs);
				} // else it's an already resolved or already installed node

				stillToResolve.push(dependencyVersions); // push it back for reuse in alternate similar paths
			}
		}

		// restore stillToResolve's state
		while (pops[0]-- > 0)
			stillToResolve.pop();
	}

	protected boolean handleDependencies(DependencyMetadata extension, int[] pops)
	{
		boolean ok = false;
		boolean dependenciesOKForNow;

		// check for lib dependency conflicts with this version for current tree path
		dependenciesOKForNow = checkNoLibConflicts(extension.libDependencies, visitedLibs);

		// schedule resolve on all extension dependencies of this version
		if (dependenciesOKForNow)
		{
			if (extension.extensionDependencies != null && extension.extensionDependencies.length > 0)
			{
				for (int i = extension.extensionDependencies.length - 1; dependenciesOKForNow && (i >= 0); i--)
				{
					DependencyMetadata[] availableDependencyVersions = extensionProvider.getDependencyMetadata(extension.extensionDependencies[i]);
					if (availableDependencyVersions != null && availableDependencyVersions.length > 0)
					{
						stillToResolve.push(availableDependencyVersions);
						pops[0]++;
					}
					else
					{
						reasons.add("Cannot find a compatible version for extension dependency: " + extension.extensionDependencies[i] + ".");
						dependenciesOKForNow = false;
					}
				}

				ok = dependenciesOKForNow; // if true, it is a valid path for now; it will continue because stillToResolve is not empty for sure
			}
			else
			{
				// no more dependencies - so valid path
				ok = true;
			}
		} // else lib conflicts - invalid tree path
		return ok;
	}

	protected boolean handleReplace(DependencyMetadata extension, LibDependencyDeclaration[][] libsToBeRemovedBecauseOfUpdate, int[] pops)
	{
		boolean ok = false;
		// already installed extension, with different version; it is not visited before in this tree path so we might be able to update it
		boolean dependenciesOKForNow;

		// check for lib dependency conflicts with this version for current tree path
		dependenciesOKForNow = checkNoLibConflicts(extension.libDependencies, visitedLibs);

		if (dependenciesOKForNow)
		{
			// find who used to depend on old version, as well as the old version
			List<DependencyMetadata> brokenDependenciesToOldVersion = new ArrayList<DependencyMetadata>();
			for (DependencyMetadata dmd : installedExtensions)
			{
				if (dmd.id.equals(extension.id))
				{
					libsToBeRemovedBecauseOfUpdate[0] = dmd.libDependencies;
				}
				else
				{
					boolean noBrokenDep = true;
					if (dmd.extensionDependencies != null && dmd.extensionDependencies.length > 0)
					{
						for (int i = 0; noBrokenDep && (i < dmd.extensionDependencies.length); i++)
						{
							// see if it's a dependency broken by this update
							if (dmd.extensionDependencies[i].id == extension.id &&
								!VersionStringUtils.belongsToInterval(extension.version, dmd.extensionDependencies[i].minVersion,
									dmd.extensionDependencies[i].maxVersion))
							{
								brokenDependenciesToOldVersion.add(dmd);
								noBrokenDep = false;
							}
						}
					}
				}
			}

			// find alternatives to broken dependencies and schedule a resolve on them
			for (int i = brokenDependenciesToOldVersion.size() - 1; dependenciesOKForNow && (i >= 0); i--)
			{
				DependencyMetadata[] possibleReplacements = extensionProvider.getDependencyMetadata(new ExtensionDependencyDeclaration(
					brokenDependenciesToOldVersion.get(i).id, VersionStringUtils.UNBOUNDED, VersionStringUtils.UNBOUNDED));
				if (possibleReplacements != null)
				{
					// check which versions are compatible with the new updated dependency
					List<DependencyMetadata> compatibleAlternatives = new ArrayList<DependencyMetadata>();
					for (DependencyMetadata candidate : possibleReplacements)
					{
						boolean noDependencyConflict = true;
						if (candidate.extensionDependencies != null && candidate.extensionDependencies.length > 0)
						{
							for (int j = 0; noDependencyConflict && (j < candidate.extensionDependencies.length); j++)
							{
								if (candidate.extensionDependencies[j].id.equals(extension.id) &&
									!VersionStringUtils.belongsToInterval(extension.version, candidate.extensionDependencies[j].minVersion,
										candidate.extensionDependencies[j].maxVersion))
								{
									noDependencyConflict = false;
								}
							}
						}
						if (noDependencyConflict) compatibleAlternatives.add(candidate);
					}
					if (compatibleAlternatives.size() > 0)
					{
						// schedule for resolve
						stillToResolve.push(compatibleAlternatives.toArray(new DependencyMetadata[compatibleAlternatives.size()]));
						pops[0]++;
					}
					else
					{
						reasons.add("An installed extension's replacement is not possible due to resulting broken dependencies. Extension: " + extension +
							"'. Broken extension: '" + brokenDependenciesToOldVersion.get(i) + "'.");
						dependenciesOKForNow = false; // other versions of a broken dependency were found, but none was compatible; invalid tree path
					}
				}
				else
				{
					reasons.add("An installed extension's replacement is not possible due to resulting broken dependencies. Extension: " + extension +
						"'. Broken extension: '" + brokenDependenciesToOldVersion.get(i) + "'.");
					dependenciesOKForNow = false; // no replacements for a broken dependency; invalid tree path
				}
			}
			ok = dependenciesOKForNow; // if true, it is a valid path for now; it will continue because stillToResolve is not empty for sure
		}

		if (ok)
		{
			// check also for dependencies of newly replaced extension, not only for broken dependencies to it
			ok = handleDependencies(extension, pops);
		}

		return ok;
	}

	protected boolean checkNoLibConflicts(Map<String, List<LibDependencyDeclaration>> libs1, Map<String, List<LibDependencyDeclaration>> libs2)
	{
		if (ignoreLibConflicts) return true;

		boolean noConflict = true;
		Iterator<Entry<String, List<LibDependencyDeclaration>>> it = libs2.entrySet().iterator();
		while (noConflict && it.hasNext())
		{
			Entry<String, List<LibDependencyDeclaration>> entry = it.next();
			List<LibDependencyDeclaration> lst1 = entry.getValue();
			List<LibDependencyDeclaration> lst2 = libs1.get(entry.getKey());

			if (lst2 != null && lst1 != null) // lst1 should always be != null actually at this point
			{
				List<String> availableLibVersions = new ArrayList<String>(lst1.size() + lst2.size());
				for (LibDependencyDeclaration sameExistingLib : lst1)
				{
					availableLibVersions.add(sameExistingLib.version);
				}
				for (LibDependencyDeclaration sameExistingLib : lst2)
				{
					availableLibVersions.add(sameExistingLib.version);
				}
				for (LibDependencyDeclaration sameExistingLib : lst1)
				{
					filterOutIncompatibleLibVersions(sameExistingLib, availableLibVersions);
				}
				for (LibDependencyDeclaration sameExistingLib : lst2)
				{
					filterOutIncompatibleLibVersions(sameExistingLib, availableLibVersions);
				}

				if (availableLibVersions.size() == 0)
				{
					noConflict = false; // incompatible libs...
					reasons.add("Library dependency incompatibility for lib id '" + entry.getKey() + "'.");
				}
			}
		}
		return noConflict;
	}

	protected boolean checkNoLibConflicts(LibDependencyDeclaration[] libDependencies, Map<String, List<LibDependencyDeclaration>> existingLibDependencies)
	{
		if (ignoreLibConflicts) return true;

		boolean noConflict = true;
		if (libDependencies != null && libDependencies.length > 0)
		{
			for (int i = 0; noConflict && (i < libDependencies.length); i++)
			{
				LibDependencyDeclaration lib = libDependencies[i];
				List<LibDependencyDeclaration> sameVisitedLibs = existingLibDependencies.get(lib.id);
				if (sameVisitedLibs != null)
				{
					List<String> availableLibVersions = new ArrayList<String>(sameVisitedLibs.size() + 1);
					availableLibVersions.add(lib.version);
					for (LibDependencyDeclaration sameExistingLib : sameVisitedLibs)
					{
						availableLibVersions.add(sameExistingLib.version);
					}
					filterOutIncompatibleLibVersions(lib, availableLibVersions);
					for (LibDependencyDeclaration sameExistingLib : sameVisitedLibs)
					{
						filterOutIncompatibleLibVersions(sameExistingLib, availableLibVersions);
					}

					if (availableLibVersions.size() == 0)
					{
						noConflict = false; // incompatible libs...
						reasons.add("Library dependency incompatibility for lib id '" + lib.id + "'.");
					}
				}
			}
		}
		return noConflict;
	}

	/**
	 * It will remove from the availableLibVersions list the versions that are not compatible with the given lib.
	 */
	protected void filterOutIncompatibleLibVersions(LibDependencyDeclaration lib, List<String> availableLibVersions)
	{
		Iterator<String> it = availableLibVersions.iterator();
		while (it.hasNext())
		{
			String ver = it.next();
			if (!VersionStringUtils.belongsToInterval(ver, lib.minVersion, lib.maxVersion))
			{
				it.remove();
			}
		}
	}

	protected void addToLibsMap(LibDependencyDeclaration[] libDependencies, Map<String, List<LibDependencyDeclaration>> libsMap)
	{
		for (LibDependencyDeclaration lib : libDependencies)
		{
			List<LibDependencyDeclaration> x = libsMap.get(lib.id);
			if (x == null)
			{
				x = new ArrayList<LibDependencyDeclaration>(1);
				libsMap.put(lib.id, x);
			}
			x.add(lib);
		}
	}

	protected void removeFromLibsMap(LibDependencyDeclaration[] libDependencies, Map<String, List<LibDependencyDeclaration>> libsMap)
	{
		for (LibDependencyDeclaration lib : libDependencies)
		{
			List<LibDependencyDeclaration> x = libsMap.get(lib.id);
			if (x != null)
			{
				x.remove(lib);
				if (x.size() == 0) libsMap.remove(lib.id);
			}
		}
	}

}
