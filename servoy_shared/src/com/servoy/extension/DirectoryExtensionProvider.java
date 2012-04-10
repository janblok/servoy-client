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

package com.servoy.extension;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import com.servoy.j2db.util.Debug;

/**
 * This class provides extension info & data taken from all extension packages within an OS directory.
 * 
 * @author acostescu
 */
@SuppressWarnings("nls")
public class DirectoryExtensionProvider extends CachedExtensionProvider
{

	public static final String EXTENSION_PACKAGE_FILE_EXTENSION = ".exp";

	protected final File file;
	protected boolean thinkDir;

	protected boolean extensionXMLsParsed = false;
	protected Map<String, Map<String, File>> extensionVersionToFile; // <extensionid, <version, File>>
	protected List<String> warnings;


	public DirectoryExtensionProvider(File file, boolean thinkDir)
	{
		if (!file.exists() || !file.canRead() || (thinkDir && !file.isDirectory()) || (!thinkDir && file.isFile()))
		{
			throw new IllegalArgumentException("'" + file + "' is not a valid/accessible directory/file.");
		}
		this.file = file;
		this.thinkDir = thinkDir;
	}

	@Override
	protected DependencyMetadata[] getDependencyMetadataImpl(ExtensionDependencyDeclaration extensionDependency)
	{
		DependencyMetadata[] result;
		addCachedDependencyMetadataVersionInterval(extensionDependency.id, new VersionInterval(VersionStringUtils.UNBOUNDED, VersionStringUtils.UNBOUNDED));
		if (extensionXMLsParsed)
		{
			result = null;
		}
		else
		{
			parseExtensionXMLs();
			result = super.getDependencyMetadata(extensionDependency);
		}
		return result;
	}

	protected void parseExtensionXMLs()
	{
		extensionXMLsParsed = true;
		File[] fileList = thinkDir ? file.listFiles() : new File[] { file };

		if (fileList != null)
		{
			extensionVersionToFile = new HashMap<String, Map<String, File>>();
			warnings = new ArrayList<String>();
			for (File f : fileList)
			{
				if (f.exists() && f.isFile() && f.getName().endsWith(EXTENSION_PACKAGE_FILE_EXTENSION))
				{
					parseExtensionXML(f);
				}
			}
		}
	}

	protected void parseExtensionXML(File f)
	{
		ZipFile zipFile = null;
		try
		{
			zipFile = new ZipFile(f);
			ZipEntry extensionFile = zipFile.getEntry("extension.xml");
			if (extensionFile != null)
			{
				boolean adheresToSchema = runOnEntry(zipFile, extensionFile, new ValidateAgainstSchema(f.getName()));

				if (adheresToSchema)
				{
					runOnEntry(zipFile, extensionFile, new ParseDependencyMetadata(f.getName()));
				}
			}
			else
			{
				warnings.add("Reading extension package '" + f.getName() + "' failed; it will be ignored. Reason: missing 'extension.xml'.");
			}
		}
		catch (ZipException e)
		{
			warnings.add("Reading extension package '" + f.getName() + "' failed; it will be ignored. Reason: " + e.getMessage());
			Debug.trace("Reading extension package '" + f.getName() + "' failed; it will be ignored.", e);
		}
		catch (IOException e)
		{
			warnings.add("Reading extension package '" + f.getName() + "' failed; it will be ignored. Reason: " + e.getMessage());
			Debug.trace("Reading extension package '" + f.getName() + "' failed; it will be ignored.", e);
		}
		finally
		{
			if (zipFile != null)
			{
				try
				{
					zipFile.close();
				}
				catch (IOException e)
				{
					// ignore
				}
			}
		}
	}

	protected boolean runOnEntry(ZipFile zipFile, ZipEntry extensionFile, EntryInputStreamRunner runner) throws IOException
	{
		InputStream is = null;
		BufferedInputStream bis = null;
		try
		{
			is = zipFile.getInputStream(extensionFile);
			bis = new BufferedInputStream(is);

			return runner.runOnEntryInputStream(bis);
		}
		finally
		{
			if (bis != null)
			{
				try
				{
					bis.close();
				}
				catch (IOException e)
				{
					/* ignore; we are probably already throwing another one */
				}
			}
			else if (is != null)
			{
				try
				{
					is.close();
				}
				catch (IOException e)
				{
					/* ignore; we are probably already throwing another one */
				}
			}
		}
	}

	/**
	 * If problems were encountered while reading contents of given directory, they will be remembered and returned by this method.
	 * @return any problems encountered that might be of interest to the user.
	 */
	public String[] getWarnings()
	{
		return (warnings == null || warnings.size() == 0) ? null : warnings.toArray(new String[warnings.size()]);
	}

	@Override
	public void flushCache()
	{
		extensionXMLsParsed = false;
		extensionVersionToFile = null;
		warnings = null;
		super.flushCache();
	}

	public interface EntryInputStreamRunner
	{

		boolean runOnEntryInputStream(InputStream is);

	}

	// this is done separately, although schema is used when parsing also, because in
	// that case we only receive some hard-to-differentiate error and parsing continues,
	// but we actually want to parse only if XML is valid from the schema's point of view
	protected class ValidateAgainstSchema implements EntryInputStreamRunner
	{

		private final String zipFileName;

		public ValidateAgainstSchema(String zipFileName)
		{
			this.zipFileName = zipFileName;
		}

		public boolean runOnEntryInputStream(InputStream is)
		{
			// verify that XML adheres to our schema
			boolean adheresToSchema = false;
			SchemaFactory factory = SchemaFactory.newInstance("http://www.w3.org/2001/XMLSchema");
			if (factory != null)
			{
				try
				{
					Schema schema = factory.newSchema(ExtensionProvider.class.getResource("servoy-extension.xsd"));
					Validator validator = schema.newValidator();
					Source source = new StreamSource(is);

					try
					{
						validator.validate(source);
						adheresToSchema = true;
					}
					catch (SAXException ex)
					{
						warnings.add("Invalid 'extension.xml' in package '" + zipFileName + "'; .xsd validation failed. Reason: " + ex.getMessage());
						Debug.trace("Invalid 'extension.xml' in package '" + zipFileName + "'; .xsd validation failed.", ex);
					}
					catch (IOException ex)
					{
						warnings.add("Invalid 'extension.xml' in package '" + zipFileName + "'; .xsd validation failed. Reason: " + ex.getMessage());
						Debug.trace("Invalid 'extension.xml' in package '" + zipFileName + "'; .xsd validation failed.", ex);
					}
				}
				catch (SAXException ex)
				{
					warnings.add("Unable to validate 'extension.xml' against the .xsd. Please report this problem to Servoy.");
					Debug.error("Error compiling 'servoy-extension.xsd'.");
				}
			}
			else
			{
				warnings.add("Unable to validate 'extension.xml' against the .xsd. Please report this problem to Servoy.");
				Debug.error("Cannot find schema factory.");
			}

			return adheresToSchema;
		}

	}

	protected class ParseDependencyMetadata implements EntryInputStreamRunner
	{

		private final String zipFileName;

		public ParseDependencyMetadata(String zipFileName)
		{
			this.zipFileName = zipFileName;
		}

		public boolean runOnEntryInputStream(InputStream is)
		{
			SchemaFactory factory = SchemaFactory.newInstance("http://www.w3.org/2001/XMLSchema");
			if (factory != null)
			{
				Schema schema = null;
				try
				{
					// prepare to verify that XML adheres to our schema; because of this schema defined default values will be set as well when parsing
					schema = factory.newSchema(ExtensionProvider.class.getResource("servoy-extension.xsd"));
				}
				catch (SAXException ex)
				{
					warnings.add("Unable to validate 'extension.xml' against the .xsd. Please report this problem to Servoy.");
					Debug.error("Error compiling 'servoy-extension.xsd'.");
				}

				if (schema != null)
				{
					DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
					if (dbf != null)
					{
						try
						{
							dbf.setNamespaceAware(true);
							dbf.setSchema(schema);
							DocumentBuilder db = dbf.newDocumentBuilder();
							db.setErrorHandler(new ParseDependencyMetadataErrorHandler(zipFileName));

							Document doc = db.parse(is);
							Element root = doc.getDocumentElement(); // "servoy-extension" tag
							root.normalize();

							// as this was already validated by schema, we need less null-checks and less structure checks
							try
							{
								String extensionId = root.getElementsByTagName("extension-id").item(0).getTextContent();
								String version = root.getElementsByTagName("version").item(0).getTextContent();

								NodeList dependencies = root.getElementsByTagName("dependencies");

								ServoyDependencyDeclaration sdd = null;
								List<ExtensionDependencyDeclaration> edds = new ArrayList<ExtensionDependencyDeclaration>();
								List<LibDependencyDeclaration> ldds = new ArrayList<LibDependencyDeclaration>();

								if (dependencies != null && dependencies.getLength() == 1)
								{
									Element dependenciesNode = (Element)dependencies.item(0);
									dependencies = dependenciesNode.getElementsByTagName("servoy");

									Element element;
									if (dependencies != null && dependencies.getLength() == 1)
									{
										element = ((Element)dependencies.item(0));
										sdd = new ServoyDependencyDeclaration(element.getElementsByTagName("min-version").item(0).getTextContent(),
											element.getElementsByTagName("max-version").item(0).getTextContent());
									}

									int i = 0;
									dependencies = dependenciesNode.getElementsByTagName("extension");
									while (dependencies != null && dependencies.getLength() > i)
									{
										element = ((Element)dependencies.item(i++));

										String minVersion = getMinMaxVersion(element, "min-version");
										String maxVersion = getMinMaxVersion(element, "max-version");

										edds.add(new ExtensionDependencyDeclaration(element.getElementsByTagName("id").item(0).getTextContent(), minVersion,
											maxVersion));
									}

									i = 0;
									dependencies = dependenciesNode.getElementsByTagName("lib");
									while (dependencies != null && dependencies.getLength() > i)
									{
										element = ((Element)dependencies.item(i++));

										String libVersion = element.getElementsByTagName("version").item(0).getTextContent();
										String minVersion = getMinMaxVersion(element, "min-version");
										String maxVersion = getMinMaxVersion(element, "max-version");

										if (minVersion == VersionStringUtils.UNBOUNDED && maxVersion == VersionStringUtils.UNBOUNDED)
										{
											// no min/max specified for lib so it is a fixed version dependency
											minVersion = maxVersion = libVersion;
										}
										ldds.add(new LibDependencyDeclaration(element.getElementsByTagName("id").item(0).getTextContent(), libVersion,
											minVersion, maxVersion));
									}
								}

								// cache dependency info about this version of the extension
								boolean added = cacheDependencyMetadataVersion(new DependencyMetadata(extensionId, version, sdd, (edds.size() > 0)
									? edds.toArray(new ExtensionDependencyDeclaration[edds.size()]) : null, (ldds.size() > 0)
									? ldds.toArray(new LibDependencyDeclaration[ldds.size()]) : null));

								if (added)
								{
									// tell cache that any version of this extension is already cached (because all available packages will be cached)
									addCachedDependencyMetadataVersionInterval(extensionId, new VersionInterval(VersionStringUtils.UNBOUNDED,
										VersionStringUtils.UNBOUNDED));
								}
								else
								{
									warnings.add("More then one package contains extension ('" + extensionId + "', " + version + "). Ignoring package: " +
										zipFileName);
								}
							}
							catch (IllegalArgumentException e)
							{
								warnings.add("Incorrect content when parsing 'extension.xml' in package '" + zipFileName + "'. Reason: " + e.getMessage());
							}
						}
						catch (ParserConfigurationException e)
						{
							warnings.add("Cannot parse 'extension.xml' in package '" + zipFileName + "'. Reason: " + e.getMessage());
							Debug.trace("Cannot parse 'extension.xml' in package '" + zipFileName + "'.", e);
						}
						catch (SAXException e)
						{
							warnings.add("Cannot parse 'extension.xml' in package '" + zipFileName + "'. Reason: " + e.getMessage());
							Debug.trace("Cannot parse 'extension.xml' in package '" + zipFileName + "'.", e);
						}
						catch (IOException e)
						{
							warnings.add("Cannot parse 'extension.xml' in package '" + zipFileName + "'. Reason: " + e.getMessage());
							Debug.trace("Cannot parse 'extension.xml' in package '" + zipFileName + "'.", e);
						}
					}
					else
					{
						warnings.add("Unable to parse 'extension.xml'. Please report this problem to Servoy.");
						Debug.error("Cannot find document builder factory.");
					}
				}
			}
			else
			{
				warnings.add("Unable to validate 'extension.xml' against the .xsd. Please report this problem to Servoy.");
				Debug.error("Cannot find schema factory.");
			}
			return true;
		}

		protected String getMinMaxVersion(Element element, String nodeName)
		{
			String minMaxVersion = VersionStringUtils.UNBOUNDED;

			NodeList verNode = element.getElementsByTagName(nodeName);
			if (verNode != null && verNode.getLength() == 1)
			{
				minMaxVersion = verNode.item(0).getTextContent();
				NamedNodeMap attrs = verNode.item(0).getAttributes();
				if (attrs != null)
				{
					Node attr = attrs.getNamedItem("inclusive");
					if (attr != null && "false".equals(attr.getNodeValue()))
					{
						minMaxVersion = VersionStringUtils.createExclusiveVersionString(minMaxVersion);
					}
				}
			}

			return minMaxVersion;
		}

	}

	protected class ParseDependencyMetadataErrorHandler implements ErrorHandler
	{

		private final String zipFileName;

		public ParseDependencyMetadataErrorHandler(String zipFileName)
		{
			this.zipFileName = zipFileName;
		}

		public void warning(SAXParseException exception) throws SAXException
		{
			warnings.add("Warning when parsing 'extension.xml' in package '" + zipFileName + "'. Warning: " + exception.getMessage());
			Debug.trace("Warning when parsing 'extension.xml' in package '" + zipFileName + "'.", exception);
		}

		public void error(SAXParseException exception) throws SAXException
		{
			warnings.add("Error when parsing 'extension.xml' in package '" + zipFileName + "'. Error: " + exception.getMessage());
			Debug.trace("Error when parsing 'extension.xml' in package '" + zipFileName + "'.", exception);

		}

		public void fatalError(SAXParseException exception) throws SAXException
		{
			warnings.add("Cannot parse 'extension.xml' in package '" + zipFileName + "'. Reason: " + exception.getMessage());
			Debug.trace("Cannot parse 'extension.xml' in package '" + zipFileName + "'.", exception);
		}

	}

}
