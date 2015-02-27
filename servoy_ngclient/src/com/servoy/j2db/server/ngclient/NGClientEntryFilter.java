package com.servoy.j2db.server.ngclient;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.sablo.WebEntry;
import org.sablo.specification.WebComponentSpecProvider;
import org.sablo.websocket.IWebsocketSessionFactory;
import org.sablo.websocket.WebsocketSessionManager;

import com.servoy.j2db.AbstractActiveSolutionHandler;
import com.servoy.j2db.FlattenedSolution;
import com.servoy.j2db.persistence.Form;
import com.servoy.j2db.persistence.IRepository;
import com.servoy.j2db.persistence.Solution;
import com.servoy.j2db.persistence.SolutionMetaData;
import com.servoy.j2db.server.ngclient.property.types.Types;
import com.servoy.j2db.server.ngclient.template.FormLayoutGenerator;
import com.servoy.j2db.server.ngclient.template.FormLayoutStructureGenerator;
import com.servoy.j2db.server.ngclient.template.FormTemplateGenerator;
import com.servoy.j2db.server.shared.ApplicationServerRegistry;
import com.servoy.j2db.server.shared.IApplicationServer;
import com.servoy.j2db.util.Debug;
import com.servoy.j2db.util.HTTPUtils;
import com.servoy.j2db.util.Settings;
import com.servoy.j2db.util.Utils;

/**
 * Filter and entrypoint for webapp
 * @author jcompagner
 */
@WebFilter(urlPatterns = { "/solutions/*" })
@SuppressWarnings("nls")
public class NGClientEntryFilter extends WebEntry
{
	public static final String SOLUTIONS_PATH = "solutions/";
	public static final String FORMS_PATH = "forms/";

	private String[] locations;
	private String[] services;

	public NGClientEntryFilter()
	{
		super(WebsocketSessionFactory.CLIENT_ENDPOINT);
	}

	@Override
	public void init(final FilterConfig fc) throws ServletException
	{
		//when started in developer - init is done in the ResourceProvider filter
		if (!ApplicationServerRegistry.get().isDeveloperStartup())
		{
			InputStream is = null;
			try
			{
				is = fc.getServletContext().getResourceAsStream("/WEB-INF/components.properties");
				Properties properties = new Properties();
				properties.load(is);
				locations = properties.getProperty("locations").split(";");
			}
			catch (Exception e)
			{
				Debug.error("Exception during init components.properties reading", e);
			}
			finally
			{
				Utils.closeInputStream(is);
			}
			try
			{
				is = fc.getServletContext().getResourceAsStream("/WEB-INF/services.properties");
				Properties properties = new Properties();
				properties.load(is);
				services = properties.getProperty("locations").split(";");
			}
			catch (Exception e)
			{
				Debug.error("Exception during init services.properties reading", e);
			}
			finally
			{
				Utils.closeInputStream(is);
			}
			Types.registerTypes();

			super.init(fc);
		}
	}

	@Override
	public String[] getWebComponentBundleNames()
	{
		return locations;
	}

	@Override
	public String[] getServiceBundleNames()
	{
		return services;
	}

	/**
	 * Get form script references, usefull for debugging
	 * @param fs the flattened solution
	 * @return the form script contributions
	 */
	private Collection<String> getFormScriptReferences(FlattenedSolution fs)
	{
		List<String> formScripts = new ArrayList<>();
		if (Boolean.valueOf(System.getProperty("servoy.generateformscripts", "false")).booleanValue())
		{
			Iterator<Form> it = fs.getForms(false);
			while (it.hasNext())
			{
				Form form = it.next();
				Solution sol = (Solution)form.getAncestor(IRepository.SOLUTIONS);
				formScripts.add("solutions/" + sol.getName() + "/forms/" + form.getName() + ".js");
			}
		}
		return formScripts;
	}

	@Override
	public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException
	{
		try
		{
			if (Utils.getAsBoolean(Settings.getInstance().getProperty("servoy.internal.reloadSpecsAllTheTime", "false")))
			{
				WebComponentSpecProvider.reload();
			}

			HttpServletRequest request = (HttpServletRequest)servletRequest;
			String uri = request.getRequestURI();
			if (uri != null && (uri.endsWith(".html") || uri.endsWith(".js")))
			{
				String solutionName = getSolutionNameFromURI(uri);
				if (solutionName != null)
				{
					String clientUUID = request.getParameter("sessionId");
					INGClientWebsocketSession wsSession = null;
					if (clientUUID != null)
					{
						wsSession = (INGClientWebsocketSession)WebsocketSessionManager.getSession(WebsocketSessionFactory.CLIENT_ENDPOINT, clientUUID);
					}
					FlattenedSolution fs = null;
					boolean closeFS = false;
					if (wsSession != null)
					{
						fs = wsSession.getClient().getFlattenedSolution();
					}
					if (fs == null)
					{
						try
						{
							closeFS = true;
							IApplicationServer as = ApplicationServerRegistry.getService(IApplicationServer.class);
							fs = new FlattenedSolution((SolutionMetaData)ApplicationServerRegistry.get().getLocalRepository().getRootObjectMetaData(
								solutionName, IRepository.SOLUTIONS), new AbstractActiveSolutionHandler(as)
							{
								@Override
								public IRepository getRepository()
								{
									return ApplicationServerRegistry.get().getLocalRepository();
								}
							});
						}
						catch (Exception e)
						{
							Debug.error("error loading solution: " + solutionName + " for clientid: " + clientUUID, e);
						}
					}

					if (fs != null)
					{
						try
						{
							String formName = getFormNameFromURI(uri);
							if (formName != null)
							{
								Form f = fs.getForm(formName);
								if (f == null && wsSession != null) f = wsSession.getClient().getFormManager().getPossibleForm(formName);
								Form form = (f != null ? fs.getFlattenedForm(f) : null);
								if (form != null)
								{
									if (HTTPUtils.checkAndSetUnmodified(((HttpServletRequest)servletRequest), ((HttpServletResponse)servletResponse),
										fs.getLastModifiedTime())) return;

									HTTPUtils.setNoCacheHeaders((HttpServletResponse)servletResponse);

									boolean html = uri.endsWith(".html");
									PrintWriter w = servletResponse.getWriter();
									if (html && form.isResponsiveLayout())
									{
										((HttpServletResponse)servletResponse).setContentType("text/html");
										FormLayoutStructureGenerator.generateLayout(form,
											wsSession != null ? new ServoyDataConverterContext(wsSession.getClient()) : new ServoyDataConverterContext(fs), w,
											Utils.getAsBoolean(request.getParameter("design")), Utils.getAsBoolean(request.getParameter("highlight")));
									}
									else if (uri.endsWith(".html"))
									{
										((HttpServletResponse)servletResponse).setContentType("text/html");
										FormLayoutGenerator.generateRecordViewForm(w, form,
											wsSession != null ? new ServoyDataConverterContext(wsSession.getClient()) : new ServoyDataConverterContext(fs),
											Utils.getAsBoolean(request.getParameter("design")), Utils.getAsBoolean(request.getParameter("highlight")));
									}
									else if (uri.endsWith(".js"))
									{
										((HttpServletResponse)servletResponse).setContentType("text/" + (html ? "html" : "javascript"));
										new FormTemplateGenerator(wsSession != null ? new ServoyDataConverterContext(wsSession.getClient())
											: new ServoyDataConverterContext(fs), false, Utils.getAsBoolean(request.getParameter("design"))).generate(form,
											formName, "form_recordview_js.ftl", w);
									}
									w.flush();
									return;
								}
							}
							else
							{
								//prepare for possible index.html lookup
								Map<String, String> variableSubstitution = new HashMap<String, String>();
								variableSubstitution.put("orientation", String.valueOf(fs.getSolution().getTextOrientation()));
								super.doFilter(servletRequest, servletResponse, filterChain, Arrays.asList("css/servoy.css"), getFormScriptReferences(fs),
									variableSubstitution);
								return;
							}
						}
						finally
						{
							if (closeFS) fs.close(null);
						}
					}
				}
			}
			Debug.log("No solution found for this request, calling the default filter: " + uri);
			super.doFilter(servletRequest, servletResponse, filterChain, null, null, null);
		}
		catch (RuntimeException | Error e)
		{
			Debug.error(e);
			throw e;
		}
	}

	/**
	 * Get the form name from url
	 * @param uri
	 * @return the name or null
	 */
	private String getFormNameFromURI(String uri)
	{
		int formIndex = uri.indexOf(FORMS_PATH);
		if (formIndex > 0)
		{
			String formName = uri.substring(formIndex + FORMS_PATH.length());
			formName = formName.replace(".html", "");
			formName = formName.replace(".js", "");
			return formName;
		}
		return null;
	}

	/**
	 * Get the solution name from an url
	 * @param uri
	 * @return the name or null
	 */
	private String getSolutionNameFromURI(String uri)
	{
		int solutionIndex = uri.indexOf(SOLUTIONS_PATH);
		if (solutionIndex > 0)
		{
			return uri.substring(solutionIndex + SOLUTIONS_PATH.length(), uri.indexOf("/", solutionIndex + SOLUTIONS_PATH.length() + 1));
		}
		return null;
	}

	@Override
	protected IWebsocketSessionFactory createSessionFactory()
	{
		return new WebsocketSessionFactory();
	}

	@Override
	protected URL getIndexPageResource(HttpServletRequest request) throws IOException
	{
		String uri = request.getRequestURI();
		if (uri != null && uri.endsWith("index.html"))
		{
			return getClass().getResource("index.html");
		}
		return super.getIndexPageResource(request);
	}
}
