package com.servoy.j2db.server.ngclient;

import java.awt.Dimension;
import java.awt.print.PageFormat;
import java.io.IOException;
import java.net.URL;
import java.rmi.RemoteException;
import java.sql.Types;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.wicket.util.string.AppendingStringBuffer;
import org.json.JSONArray;
import org.json.JSONObject;
import org.sablo.IChangeListener;
import org.sablo.specification.WebComponentSpecification;
import org.sablo.specification.WebServiceSpecProvider;
import org.sablo.websocket.IServerService;
import org.sablo.websocket.WebsocketEndpoint;

import com.servoy.base.persistence.constants.IValueListConstants;
import com.servoy.j2db.ApplicationException;
import com.servoy.j2db.IApplication;
import com.servoy.j2db.IBasicFormManager;
import com.servoy.j2db.IDataRendererFactory;
import com.servoy.j2db.IFormController;
import com.servoy.j2db.IServiceProvider;
import com.servoy.j2db.J2DBGlobals;
import com.servoy.j2db.Messages;
import com.servoy.j2db.dataprocessing.CustomValueList;
import com.servoy.j2db.dataprocessing.FoundSetManager;
import com.servoy.j2db.dataprocessing.IUserClient;
import com.servoy.j2db.dataprocessing.SwingFoundSetFactory;
import com.servoy.j2db.persistence.RepositoryException;
import com.servoy.j2db.persistence.Solution;
import com.servoy.j2db.persistence.SolutionMetaData;
import com.servoy.j2db.persistence.ValueList;
import com.servoy.j2db.scripting.IExecutingEnviroment;
import com.servoy.j2db.scripting.PluginScope;
import com.servoy.j2db.server.headlessclient.AbstractApplication;
import com.servoy.j2db.server.ngclient.component.WebFormController;
import com.servoy.j2db.server.ngclient.scripting.WebServiceScriptable;
import com.servoy.j2db.server.shared.ApplicationServerRegistry;
import com.servoy.j2db.server.shared.IApplicationServer;
import com.servoy.j2db.server.shared.WebCredentials;
import com.servoy.j2db.ui.ItemFactory;
import com.servoy.j2db.util.Ad;
import com.servoy.j2db.util.Debug;
import com.servoy.j2db.util.RendererParentWrapper;
import com.servoy.j2db.util.SecuritySupport;
import com.servoy.j2db.util.ServoyException;
import com.servoy.j2db.util.ServoyScheduledExecutor;
import com.servoy.j2db.util.Settings;
import com.servoy.j2db.util.Utils;

// TODO we should add a subclass between ClientState and SessionClient, (remove all "session" and wicket related stuff out of SessionClient)
// then we can extend that one.
public class NGClient extends AbstractApplication implements INGApplication, IChangeListener, IServerService
{
	private static final long serialVersionUID = 1L;

	private final INGClientWebsocketSession wsSession;

	private transient volatile ServoyScheduledExecutor scheduledExecutorService;

	private volatile NGRuntimeWindowManager runtimeWindowManager;

	private Map<Object, Object> uiProperties;

	public static final String APPLICATION_SERVICE = "$applicationService";
	public static final String APPLICATION_SERVER_SERVICE = "applicationServerService";

	public NGClient(INGClientWebsocketSession wsSession) throws Exception
	{
		super(new WebCredentials());

		this.wsSession = wsSession;
		getWebsocketSession().registerServerService(APPLICATION_SERVER_SERVICE, this);
		settings = Settings.getInstance();
		try
		{
			applicationSetup();
			applicationInit();
			applicationServerInit();
		}
		catch (Exception e)
		{
			// if exception directly do a shutdown, so that this client doesn't hang.
			try
			{
				shutDown(true);
			}
			catch (Exception e2)
			{
				Debug.error("Cannot shutdown properly after client init failed", e2);
			}
			throw e;
		}
	}

	@Override
	protected IExecutingEnviroment createScriptEngine()
	{
		IExecutingEnviroment scriptEngine = super.createScriptEngine();
		WebComponentSpecification[] serviceSpecifications = WebServiceSpecProvider.getInstance().getWebServiceSpecifications();
		PluginScope scope = (PluginScope)scriptEngine.getSolutionScope().get("plugins", scriptEngine.getSolutionScope());
		scope.setLocked(false);
		for (WebComponentSpecification serviceSpecification : serviceSpecifications)
		{
			scope.put(serviceSpecification.getName(), scope, new WebServiceScriptable(this, serviceSpecification));
		}
		scope.setLocked(true);
		return scriptEngine;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.servoy.j2db.server.headlessclient.AbstractApplication#getLocale()
	 */
	@Override
	public Locale getLocale()
	{
		if (locale == null) initLocaleAndTimeZone();
		return super.getLocale();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.servoy.j2db.server.headlessclient.AbstractApplication#getTimeZone()
	 */
	@Override
	public TimeZone getTimeZone()
	{
		if (timeZone == null) initLocaleAndTimeZone();
		return super.getTimeZone();
	}

	private void initLocaleAndTimeZone()
	{
		Object retValue;
		try
		{
			retValue = this.getWebsocketSession().getService(NGClient.APPLICATION_SERVICE).executeServiceCall("getUtcOffsetsAndLocale", null);
		}
		catch (IOException e)
		{
			Debug.error(e);
			return;
		}
		if (retValue instanceof JSONObject)
		{
			if (timeZone == null)
			{
				String utc = ((JSONObject)retValue).optString("utcOffset");
				if (utc != null)
				{
					// apparently it is platform dependent on whether you get the
					// offset in a decimal form or not. This parses the decimal
					// form of the UTC offset, taking into account several
					// possibilities
					// such as getting the format in +2.5 or -1.2

					int dotPos = utc.indexOf('.');
					if (dotPos >= 0)
					{
						String hours = utc.substring(0, dotPos);
						String hourPart = utc.substring(dotPos + 1);

						if (hours.startsWith("+"))
						{
							hours = hours.substring(1);
						}
						int offsetHours = Integer.parseInt(hours);
						int offsetMins = (int)(Double.parseDouble(hourPart) * 6);

						// construct a GMT timezone offset string from the retrieved
						// offset which can be parsed by the TimeZone class.

						AppendingStringBuffer sb = new AppendingStringBuffer("GMT");
						sb.append(offsetHours > 0 ? "+" : "-");
						sb.append(Math.abs(offsetHours));
						sb.append(":");
						if (offsetMins < 10)
						{
							sb.append("0");
						}
						sb.append(offsetMins);
						timeZone = TimeZone.getTimeZone(sb.toString());
					}
					else
					{
						int offset = Integer.parseInt(utc);
						if (offset < 0)
						{
							utc = utc.substring(1);
						}
						timeZone = TimeZone.getTimeZone("GMT" + ((offset > 0) ? "+" : "-") + utc);
					}

					String dstOffset = ((JSONObject)retValue).optString("utcDstOffset");
					if (timeZone != null && dstOffset != null)
					{
						TimeZone dstTimeZone = null;
						dotPos = dstOffset.indexOf('.');
						if (dotPos >= 0)
						{
							String hours = dstOffset.substring(0, dotPos);
							String hourPart = dstOffset.substring(dotPos + 1);

							if (hours.startsWith("+"))
							{
								hours = hours.substring(1);
							}
							int offsetHours = Integer.parseInt(hours);
							int offsetMins = (int)(Double.parseDouble(hourPart) * 6);

							// construct a GMT timezone offset string from the
							// retrieved
							// offset which can be parsed by the TimeZone class.

							AppendingStringBuffer sb = new AppendingStringBuffer("GMT");
							sb.append(offsetHours > 0 ? "+" : "-");
							sb.append(Math.abs(offsetHours));
							sb.append(":");
							if (offsetMins < 10)
							{
								sb.append("0");
							}
							sb.append(offsetMins);
							dstTimeZone = TimeZone.getTimeZone(sb.toString());
						}
						else
						{
							int offset = Integer.parseInt(dstOffset);
							if (offset < 0)
							{
								dstOffset = dstOffset.substring(1);
							}
							dstTimeZone = TimeZone.getTimeZone("GMT" + ((offset > 0) ? "+" : "-") + dstOffset);
						}
						// if the dstTimezone (1 July) has a different offset then
						// the real time zone (1 January) try to combine the 2.
						if (dstTimeZone != null && dstTimeZone.getRawOffset() != timeZone.getRawOffset())
						{
							int dstSaving = dstTimeZone.getRawOffset() - timeZone.getRawOffset();
							String[] availableIDs = TimeZone.getAvailableIDs(timeZone.getRawOffset());
							for (String availableID : availableIDs)
							{
								TimeZone zone = TimeZone.getTimeZone(availableID);
								if (zone.getDSTSavings() == dstSaving)
								{
									// this is a best guess... still the start and end of the DST should
									// be needed to know to be completely correct, or better yet
									// not just the GMT offset but the TimeZone ID should be transfered
									// from the browser.
									timeZone = zone;
									break;
								}
							}
						}
					}
				}
			}
			if (locale == null)
			{
				String browserLocale = ((JSONObject)retValue).optString("locale");
				if (browserLocale != null)
				{
					String[] languageAndCountry = browserLocale.split("-");
					if (languageAndCountry.length == 1)
					{
						locale = new Locale(languageAndCountry[0]);
					}
					else if (languageAndCountry.length == 2)
					{
						locale = new Locale(languageAndCountry[0], languageAndCountry[1]);
					}
				}
			}
		}
	}

	public void loadSolution(String solutionName) throws RepositoryException
	{
		try
		{
			SolutionMetaData solutionMetaData = getApplicationServer().getSolutionDefinition(solutionName, getSolutionTypeFilter());
			if (solutionMetaData == null)
			{
				throw new IllegalArgumentException(Messages.getString("servoy.exception.solutionNotFound", new Object[] { solutionName })); //$NON-NLS-1$
			}
			loadSolution(solutionMetaData);
		}
		catch (RemoteException e)
		{
			throw new RepositoryException(e);
		}
	}

	@Override
	protected int getSolutionTypeFilter()
	{
		return super.getSolutionTypeFilter() | SolutionMetaData.NG_CLIENT_ONLY;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.servoy.j2db.ClientState#getFormManager()
	 */
	@Override
	public INGFormManager getFormManager()
	{
		return (INGFormManager)super.getFormManager();
	}

//	public synchronized Map<String, Map<String, Map<String, Object>>> getAllComponentsChanges()
//	{
//		Map<String, Map<String, Map<String, Object>>> changes = new HashMap<>(8);
//		if (isShutDown()) return changes;
//		for (IFormController fc : getFormManager().getCachedFormControllers())
//		{
//			if (fc.isFormVisible())
//			{
//				Map<String, Map<String, Object>> formChanges = ((WebFormUI)fc.getFormUI()).getAllComponentsChanges();
//				if (formChanges.size() > 0)
//				{
//					changes.put(fc.getName(), formChanges);
//				}
//			}
//		}
//		return changes;
//	}

	@Override
	protected void solutionLoaded(Solution s)
	{
		super.solutionLoaded(s);
		getWebsocketSession().solutionLoaded(s);
	}

	@Override
	public INGClientWebsocketSession getWebsocketSession()
	{
		return wsSession;
	}

	@Override
	public void valueChanged()
	{
		getWebsocketSession().valueChanged();
	}

	@Override
	protected void doInvokeLater(Runnable r)
	{
		wsSession.getEventDispatcher().addEvent(r);
	}

	@Override
	public boolean isEventDispatchThread()
	{
		return wsSession.getEventDispatcher().isEventDispatchThread();
	}

	@Override
	public void invokeAndWait(Runnable r)
	{
		FutureTask<Object> future = new FutureTask<Object>(r, null);
		wsSession.getEventDispatcher().addEvent(future);
		try
		{
			future.get(); // blocking
		}
		catch (InterruptedException e)
		{
			Debug.trace(e);
		}
		catch (ExecutionException e)
		{
			e.getCause().printStackTrace();
			Debug.error(e.getCause());
		}
	}

	@Override
	protected boolean startApplicationServerConnection()
	{
		try
		{
			applicationServer = ApplicationServerRegistry.getService(IApplicationServer.class);
			return true;
		}
		catch (Exception ex)
		{
			reportError(Messages.getString("servoy.client.error.finding.dataservice"), ex); //$NON-NLS-1$
			return false;
		}
	}

	@Override
	protected void loadSolution(SolutionMetaData solutionMeta) throws RepositoryException
	{
		if (loadSolutionsAndModules(solutionMeta))
		{
			J2DBGlobals.firePropertyChange(this, "solution", null, getSolution()); //$NON-NLS-1$
		}
	}

	@Override
	protected IBasicFormManager createFormManager()
	{
		return new NGFormManager(this);
	}

	@Override
	public IChangeListener getChangeListener()
	{
		return this;
	}

	@Override
	protected void createFoundSetManager()
	{
		foundSetManager = new FoundSetManager(this, new SwingFoundSetFactory());
		foundSetManager.init();
	}

	@Override
	public ScheduledExecutorService getScheduledExecutor()
	{
		if (scheduledExecutorService == null && !isShutDown())
		{
			synchronized (this)
			{
				if (scheduledExecutorService == null)
				{
					scheduledExecutorService = new ServoyScheduledExecutor(1, 4, 1)
					{
						private IServiceProvider prev;

						@Override
						protected void beforeExecute(Thread t, Runnable r)
						{
							super.beforeExecute(t, r);
							prev = J2DBGlobals.getServiceProvider();
							if (prev != NGClient.this)
							{
								// if this happens it is a webclient in developer..
								// and the provider is not set for this web client. so it must be set.
								J2DBGlobals.setServiceProvider(NGClient.this);
							}
						}

						@Override
						protected void afterExecute(Runnable r, Throwable t)
						{
							super.afterExecute(r, t);
							J2DBGlobals.setServiceProvider(prev);
						}
					};
				}
			}
		}
		return scheduledExecutorService;
	}

	@SuppressWarnings("nls")
	@Override
	public Dimension getScreenSize()
	{
		try
		{
			Object retValue = this.getWebsocketSession().getService(NGClient.APPLICATION_SERVICE).executeServiceCall("getScreenSize", null);
			if (retValue instanceof JSONObject)
			{
				int orientation = ((JSONObject)retValue).optInt("orientation", 0);
				int width = ((JSONObject)retValue).optInt("width", -1);
				int height = ((JSONObject)retValue).optInt("height", -1);
				if (orientation == 90 || orientation == -90)
				{
					return new Dimension(height, width);
				}
				return new Dimension(width, height);
			}
		}
		catch (IOException e)
		{
			Debug.error(e);
		}
		return null;
	}

	@Override
	public URL getServerURL()
	{
		try
		{
			Object retValue = this.getWebsocketSession().getService(NGClient.APPLICATION_SERVICE).executeServiceCall("getLocation", null);
			if (retValue instanceof String)
			{
				String url = (String)retValue;
				int index = url.indexOf("/solutions/");
				if (index != -1)
				{
					url = url.substring(0, index);
				}
				if (!url.toLowerCase().startsWith("http"))
				{
					url = "http://" + url;
				}
				return new URL(url);
			}
		}
		catch (IOException e)
		{
			Debug.error(e);
		}
		return super.getServerURL();
	}

	@Override
	public int getApplicationType()
	{
		return IApplication.NG_CLIENT;
	}

	@Override
	public String getClientOSName()
	{
		try
		{
			Object retValue = this.getWebsocketSession().getService(NGClient.APPLICATION_SERVICE).executeServiceCall("getUserAgentAndPlatform", null);
			if (retValue instanceof JSONObject)
			{
				String userAgent = ((JSONObject)retValue).optString("userAgent");
				if (userAgent != null)
				{
					if (userAgent.indexOf("NT 6.1") != -1) return "Windows 7";
					if (userAgent.indexOf("NT 6.0") != -1) return "Windows Vista";
					if (userAgent.indexOf("NT 5.1") != -1 || userAgent.indexOf("Windows XP") != -1) return "Windows XP";
					if (userAgent.indexOf("Linux") != -1) return "Linux";
					if (userAgent.indexOf("Mac") != -1) return "Mac OS";
				}
				String platform = ((JSONObject)retValue).optString("platform");
				if (platform != null) return platform;
			}
		}
		catch (IOException e)
		{
			Debug.error(e);
		}
		return super.getClientOSName();
	}

	@Override
	public int getClientPlatform()
	{
		try
		{
			Object retValue = this.getWebsocketSession().getService(NGClient.APPLICATION_SERVICE).executeServiceCall("getUserAgentAndPlatform", null);
			if (retValue instanceof JSONObject)
			{
				String platform = ((JSONObject)retValue).optString("platform");
				if (platform != null)
				{
					return Utils.getPlatform(platform);
				}
			}
		}
		catch (IOException e)
		{
			Debug.error(e);
		}
		return super.getClientPlatform();
	}

	@Override
	public String getApplicationName()
	{
		return "Servoy NGClient";
	}

	@Override
	public boolean putClientProperty(Object name, Object val)
	{
		if (uiProperties == null)
		{
			uiProperties = new HashMap<Object, Object>();
		}
		uiProperties.put(name, val);
		return true;
	}

	@Override
	public Object getClientProperty(Object name)
	{
		return (uiProperties == null) ? null : uiProperties.get(name);
	}

	@Override
	public void setTitle(String title)
	{
		getRuntimeWindowManager().getCurrentWindow().setTitle(title);
	}

	@Override
	public ItemFactory getItemFactory()
	{
		// Not used in NGClient
		return null;
	}

	@Override
	public IDataRendererFactory getDataRenderFactory()
	{
		// Not used in NGClient
		return null;
	}

	@Override
	public RendererParentWrapper getPrintingRendererParent()
	{
		// Not used in NGClient
		return null;
	}

	@Override
	public PageFormat getPageFormat()
	{
		// Not used in NGClient
		return null;
	}

	@Override
	public void setPageFormat(PageFormat currentPageFormat)
	{
		// Not used in NGClient
	}

	@Override
	public String getUserProperty(String name)
	{
		try
		{
			return (String)getWebsocketSession().getService(NGClient.APPLICATION_SERVICE).executeServiceCall("getUserProperty", new Object[] { name });
		}
		catch (IOException e)
		{
			Debug.error("Error getting getting property '" + name + "'", e);
		}
		return null;
	}

	@Override
	public void setUserProperty(String name, String value)
	{
		try
		{
			getWebsocketSession().getService(NGClient.APPLICATION_SERVICE).executeServiceCall("setUserProperty", new Object[] { name, value });
		}
		catch (IOException e)
		{
			Debug.error("Error getting setting property '" + name + "' value: " + value, e);
		}

	}

	@SuppressWarnings("nls")
	@Override
	public String[] getUserPropertyNames()
	{
		JSONArray result;
		try
		{
			result = (JSONArray)getWebsocketSession().getService(NGClient.APPLICATION_SERVICE).executeServiceCall("getUserPropertyNames", null);
			String[] names = new String[result.length()];
			for (int i = 0; i < names.length; i++)
			{
				names[i] = result.optString(i);
			}
			return names;
		}
		catch (IOException e)
		{
			Debug.error("Error getting user property names", e);
		}
		return new String[0];
	}

	@Override
	public void looseFocus()
	{
		// TODO call request focus on a div in a client?
	}

	@Override
	public boolean showURL(String url, String target, String target_options, int timeout, boolean onRootFrame)
	{
		this.getWebsocketSession().getService(NGClient.APPLICATION_SERVICE).executeAsyncServiceCall("showUrl",
			new Object[] { url, target, target_options, timeout });
		return true;
	}

	@Override
	public void setStatusText(String text, String tooltip)
	{
		this.getWebsocketSession().getService(NGClient.APPLICATION_SERVICE).executeAsyncServiceCall("setStatusText", new Object[] { text });
	}

	@Override
	public NGRuntimeWindowManager getRuntimeWindowManager()
	{
		if (runtimeWindowManager == null)
		{
			synchronized (this)
			{
				if (runtimeWindowManager == null) runtimeWindowManager = new NGRuntimeWindowManager(this);
			}
		}
		return runtimeWindowManager;
	}

	@Override
	public synchronized void shutDown(boolean force)
	{
		super.shutDown(force);
		if (scheduledExecutorService != null)
		{
			scheduledExecutorService.shutdownNow();
			try
			{
				scheduledExecutorService.awaitTermination(10, TimeUnit.SECONDS);
			}
			catch (InterruptedException e)
			{
			}
			scheduledExecutorService = null;
		}
		getWebsocketSession().closeSession();
	}

	private transient Object[] adsInfo = null;//cache to expensive to get each time

	@Override
	protected boolean registerClient(IUserClient uc) throws Exception
	{
		boolean registered = false;
		try
		{
			registered = super.registerClient(uc);
			ApplicationServerRegistry.get().setServerProcess(getClientID());
			if (!registered)
			{
				showInfoPanel();
			}
		}
		catch (final ApplicationException e)
		{
			if (e.getErrorCode() == ServoyException.NO_LICENSE)
			{
				Map<String, Object> noLicense = new HashMap<>();
				noLicense.put("noLicense", getLicenseAndMentenanceDetail());
				WebsocketEndpoint.get().sendMessage(noLicense, null, true);
			}
			else if (e.getErrorCode() == ServoyException.MAINTENANCE_MODE)
			{
				Map<String, Object> mentenance = new HashMap<>();
				mentenance.put("maintenanceMode", getLicenseAndMentenanceDetail());
				WebsocketEndpoint.get().sendMessage(mentenance, null, true);
			}
			throw e;
		}
		return registered;
	}

	protected void showInfoPanel()
	{
		((NGClientWebsocketSession)wsSession).setClient(this);
		invokeLater(new Runnable()
		{
			@Override
			public void run()
			{
				if (adsInfo == null) adsInfo = Ad.getAdInfo();
				final int w = Utils.getAsInteger(adsInfo[1]);
				final int h = Utils.getAsInteger(adsInfo[2]);
				if (w > 50 && h > 50)
				{
					final URL url = (URL)adsInfo[0];
					final int t = Utils.getAsInteger(adsInfo[3]);
					getWebsocketSession().getService(NGClient.APPLICATION_SERVICE).executeAsyncServiceCall("showInfoPanel",
						new Object[] { url.toString(), w, h, t, getI18NMessage("servoy.button.close") });
				}
			}
		});
	}

	private Map<String, Object> getLicenseAndMentenanceDetail()
	{
		Map<String, Object> detail = new HashMap<>();
		String url = Settings.getInstance().getProperty("servoy.webclient.pageexpired.url");
		if (url != null)
		{
			detail.put("redirectUrl", url);
			String redirectTimeout = Settings.getInstance().getProperty("servoy.webclient.pageexpired.redirectTimeout");
			detail.put("redirectTimeout", Utils.getAsInteger(redirectTimeout));
		}
		return detail;
	}

	@Override
	public void setValueListItems(String name, Object[] displayValues, Object[] realValues, boolean autoconvert)
	{
		ValueList vl = getFlattenedSolution().getValueList(name);
		if (vl != null && vl.getValueListType() == IValueListConstants.CUSTOM_VALUES)
		{
			CustomValueList valueList = new CustomValueList(this, vl, vl.getCustomValues(), (vl.getAddEmptyValue() == IValueListConstants.EMPTY_VALUE_ALWAYS),
				Types.OTHER, null);
			int guessedType = Types.OTHER;
			if (autoconvert && realValues != null)
			{
				guessedType = guessValuelistType(realValues);
			}
			else if (autoconvert && displayValues != null)
			{
				guessedType = guessValuelistType(displayValues);
			}
			valueList.setValueType(guessedType);
			valueList.fillWithArrayValues(displayValues, realValues);
			IBasicFormManager fm = getFormManager();
			List<IFormController> cachedFormControllers = fm.getCachedFormControllers();
			for (IFormController form : cachedFormControllers)
			{
				((WebFormController)form).getFormUI().refreshValueList(valueList);
			}
		}
	}

	@Override
	public void showDefaultLogin() throws ServoyException
	{
		try
		{
			getWebsocketSession().getService(NGClient.APPLICATION_SERVICE).executeServiceCall("showDefaultLogin", null);
		}
		catch (IOException ex)
		{
			Debug.error(ex);
		}
	}

	/*
	 * @see org.sablo.websocket.IServerService#executeMethod(java.lang.String, org.json.JSONObject)
	 */
	@Override
	public Object executeMethod(String methodName, JSONObject args) throws Exception
	{
		switch (methodName)
		{
			case "login" :
				try
				{
					credentials.setUserName(args.optString("username"));
					credentials.setPassword(args.optBoolean("encrypted") ? SecuritySupport.decrypt(Settings.getInstance(), args.optString("password"))
						: args.optString("password"));
					authenticate(null, null, new Object[] { credentials.getUserName(), credentials.getPassword() });
					if (getClientInfo().getUserUid() != null)
					{
						//loadSolution(getSolution().getName());
						wsSession.onOpen(getSolution().getName());
						if (args.optBoolean("remember"))
						{
							JSONObject r = new JSONObject();
							r.put("username", credentials.getUserName());
							r.put("password", SecuritySupport.encrypt(Settings.getInstance(), credentials.getPassword()));
							return r;
						}
						else return Boolean.TRUE;
					}
				}
				catch (Exception ex)
				{
					Debug.error(ex);
				}
				return Boolean.FALSE;
		}

		return null;
	}

}
