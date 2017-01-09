/**
* Copyright © 1998-2016, Glodon Inc. All Rights Reserved.
*/
package org.suren.autotest.web.framework.selenium;

import static org.suren.autotest.web.framework.settings.DriverConstants.DRIVER_CHROME;
import static org.suren.autotest.web.framework.settings.DriverConstants.DRIVER_FIREFOX;
import static org.suren.autotest.web.framework.settings.DriverConstants.DRIVER_IE;
import static org.suren.autotest.web.framework.settings.DriverConstants.DRIVER_OPERA;
import static org.suren.autotest.web.framework.settings.DriverConstants.DRIVER_SAFARI;
import static org.suren.autotest.web.framework.settings.DriverConstants.ENGINE_CONFIG_FILE_NAME;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.IOUtils;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Platform;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.UnsupportedCommandException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriver.Window;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxProfile;
import org.openqa.selenium.ie.InternetExplorerDriver;
import org.openqa.selenium.opera.OperaDriver;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.safari.SafariDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.suren.autotest.web.framework.core.AutoTestException;
import org.suren.autotest.web.framework.util.BrowserUtil;
import org.suren.autotest.web.framework.util.PathUtil;

/**
 * 浏览器引擎封装类
 * @author suren
 * @since jdk1.6 2016年6月29日
 */
@Component
public class SeleniumEngine
{
	private static final Logger logger = LoggerFactory.getLogger(SeleniumEngine.class);

	private Properties enginePro = new Properties(); //引擎参数集合
	
	private Map<String, DesiredCapabilities> engineCapMap = new HashMap<String, DesiredCapabilities>();
	
	private WebDriver	driver;
	private String		driverStr;
	private String 		remoteStr;
	private long		timeout;
	private boolean		fullScreen;
	private boolean 	maximize;
	private int			width;
	private int			height;
	private int			toolbarHeight;
	
	/**
	 * 浏览器引擎初始化
	 */
	public void init()
	{
		InputStream  inputStream = null;
		try
		{
			ClassLoader classLoader = this.getClass().getClassLoader();
			
			loadDefaultEnginePath(classLoader, enginePro); //加载默认配置
			
			System.getProperties().putAll(enginePro);
		}
		finally
		{
			IOUtils.closeQuietly(inputStream);
		}
		
		initCapMap();
		
		String curDriverStr = getDriverStr();
		DesiredCapabilities capability = engineCapMap.get(curDriverStr);
		if(capability == null)
		{
			throw new RuntimeException(String.format("Unknow type driver [%s].", curDriverStr));
		}
		
		if(getRemoteStr() != null)
		{
			try
			{
				driver = new RemoteWebDriver(new URL(getRemoteStr()), capability);
			}
			catch (MalformedURLException e)
			{
				throw new AutoTestException();
			}
		}
		else if(DRIVER_CHROME.equals(curDriverStr))
		{
			driver = new ChromeDriver(capability);
		}
		else if(DRIVER_IE.equals(curDriverStr))
		{
			driver = new InternetExplorerDriver(capability);
		}
		else if(DRIVER_FIREFOX.equals(curDriverStr))
		{
			String proFile = System.getProperty("firefox.profile", null);
			FirefoxProfile profile = new FirefoxProfile(proFile != null ? new File(proFile) : null);
			fireFoxPreSet(profile);
			driver = new FirefoxDriver(null, profile, capability);
		}
		else if(DRIVER_SAFARI.equals(curDriverStr))
		{
			driver = new SafariDriver(capability);
		}
		else if(DRIVER_OPERA.equals(curDriverStr))
		{
			driver = new OperaDriver(capability);
		}
		
		if(timeout > 0)
		{
			driver.manage().timeouts().implicitlyWait(timeout, TimeUnit.SECONDS);
		}
		
		Window window = driver.manage().window();
		if(fullScreen)
		{
			try
			{
//				window.fullscreen();
			}
			catch(UnsupportedCommandException e)
			{
				logger.error("Unsupported fullScreen command.", e);
			}
		}
		
		if(maximize)
		{
			window.maximize();
		}
		
		if(getWidth() > 0)
		{
			window.setSize(new Dimension(getWidth(), window.getSize().getHeight()));
		}
		
		if(getHeight() > 0)
		{
			window.setSize(new Dimension(window.getSize().getWidth(), getHeight()));
		}
	}
	
	/**
	 * 初始化所有浏览器的配置
	 */
	private void initCapMap()
	{
		{
			DesiredCapabilities capability = DesiredCapabilities.firefox();
			capability.setCapability("marionette", true);
			engineCapMap.put(DRIVER_FIREFOX, capability);
		}
		
		//chrome://version/
		{
			DesiredCapabilities capability = DesiredCapabilities.chrome();
			
			ChromeOptions options = new ChromeOptions();
			Iterator<Object> chromeKeys = enginePro.keySet().iterator();
			while(chromeKeys.hasNext())
			{
				String key = chromeKeys.next().toString();
				if(!key.startsWith("chrome"))
				{
					continue;
				}
				
				if(key.startsWith("chrome.args"))
				{
					String arg = key.replace("chrome.args.", "") + "=" + enginePro.getProperty(key);
					if(arg.endsWith("="))
					{
						arg = arg.substring(0, arg.length() - 1);
					}
					options.addArguments(arg);
					logger.info(String.format("chrome arguments : [%s]", arg));
				}
				else if(key.startsWith("chrome.cap.proxy.http"))
				{
					String val = enginePro.getProperty(key);
					
					Proxy proxy = new Proxy();
					proxy.setHttpProxy(val);
					capability.setCapability("proxy", proxy);
				}
			}
			capability.setCapability(ChromeOptions.CAPABILITY, options);
		
			engineCapMap.put(DRIVER_CHROME, capability);
		}
		
		{
			DesiredCapabilities capability = DesiredCapabilities.internetExplorer();
			capability.setCapability(
					InternetExplorerDriver.INTRODUCE_FLAKINESS_BY_IGNORING_SECURITY_DOMAINS, true);
			engineCapMap.put(DRIVER_IE, capability);
		}
		
		{
			String proFile = System.getProperty("firefox.profile", null);
			FirefoxProfile profile = new FirefoxProfile(proFile != null ? new File(proFile) : null);
			fireFoxPreSet(profile);
		}
		
		{
			DesiredCapabilities capability = DesiredCapabilities.safari();
			engineCapMap.put(DRIVER_SAFARI, capability);
		}
		
		{
			DesiredCapabilities capability = DesiredCapabilities.operaBlink();
			engineCapMap.put(DRIVER_OPERA, capability);
		}
	}

	/**
	 * 设定firefox首选项
	 * @param profile
	 */
	private void fireFoxPreSet(FirefoxProfile profile)
	{
		BrowserUtil browserUtil = new BrowserUtil();
		Map<String, Boolean> boolMap = browserUtil.getFirefoxPreBoolMap();
		Iterator<String> boolIt = boolMap.keySet().iterator();
		while(boolIt.hasNext())
		{
			String key = boolIt.next();
			
			profile.setPreference(key, boolMap.get(key));
		}
		
		Map<String, Integer> intMap = browserUtil.getFirefoxPreIntMap();
		Iterator<String> intIt = intMap.keySet().iterator();
		while(intIt.hasNext())
		{
			String key = intIt.next();
			
			profile.setPreference(key, intMap.get(key));
		}
		
		Map<String, Integer> strMap = browserUtil.getFirefoxPreIntMap();
		Iterator<String> strIt = intMap.keySet().iterator();
		while(strIt.hasNext())
		{
			String key = strIt.next();
			
			profile.setPreference(key, strMap.get(key));
		}
	}

	/**
	 * 加载默认的engine
	 * @param enginePro
	 */
	private void loadDefaultEnginePath(ClassLoader classLoader, Properties enginePro)
	{
//		String remoteHome = enginePro.getProperty("webdriver.remote.home", "http://surenpi.com/webdriver/");
		URL ieDriverURL = null,chromeDrvierURL = null;
		// 实现对多个操作系统的兼容性设置
		Platform current = Platform.getCurrent();
		if(Platform.MAC.is(current))
		{
			chromeDrvierURL = classLoader.getResource("chromedriver");
		}
		else if (Platform.WINDOWS.is(current))
		{
			ieDriverURL = classLoader.getResource("IEDriverServer.exe");
			chromeDrvierURL = classLoader.getResource("chromedriver.exe");
		} else if (Platform.LINUX.is(current)) {
			chromeDrvierURL = classLoader.getResource("chromedriver");
		} else {
			logger.error("Your platform is: [ " + Platform.getCurrent() + " ]" + " Unfortunately we doesn't support now.");
		}
		
		enginePro.put("webdriver.ie.driver", getLocalFilePath(ieDriverURL));
		enginePro.put("webdriver.chrome.driver", getLocalFilePath(chromeDrvierURL));
		
		Enumeration<URL> resurceUrls = null;
		URL defaultResourceUrl = null;
		try
		{
			resurceUrls = classLoader.getResources(ENGINE_CONFIG_FILE_NAME);
			defaultResourceUrl = classLoader.getResource(ENGINE_CONFIG_FILE_NAME);
		}
		catch (IOException e)
		{
			logger.error("Engine properties loading error.", e);
		}
		
		if(resurceUrls == null)
		{
			return;
		}

		try
		{
			loadFromURL(enginePro, defaultResourceUrl);
		}
		catch (IOException e)
		{
			logger.error("loading engine error.", e);
		}
		
		while(resurceUrls.hasMoreElements())
		{
			URL url = resurceUrls.nextElement();
			if(url.equals(defaultResourceUrl))
			{
				continue;
			}

			try
			{
				loadFromURL(enginePro, url);
			}
			catch (IOException e)
			{
				logger.error("loading engine error.", e);
			}
		}
	}
	
	private void loadFromURL(Properties enginePro, URL url) throws IOException
	{
		try(InputStream inputStream = url.openStream())
		{
			enginePro.load(inputStream);
		}
	}
	
	private String getLocalFilePath(URL url)
	{
		if(url == null)
		{
			return "";
		}
		
		File driverFile = null;
		String protocol = url.getProtocol();
		if("jar".equals(protocol) || "http".equals(protocol))
		{
			File rootFile = PathUtil.getRootDir();
			
			OutputStream output = null;
			driverFile = new File(rootFile, "surenpi.com." + new File(url.getFile()).getName());
			if(driverFile.exists())
			{
				return driverFile.getAbsolutePath();
			}
			
			try(InputStream inputStream = url.openStream())
			{
				output = new FileOutputStream(driverFile);
				IOUtils.copy(inputStream, output);
			}
			catch (IOException e)
			{
				logger.error("Driver file copy error.", e);
			}
			finally
			{
				IOUtils.closeQuietly(output);
			}
		}
		else
		{
			try
			{
				driverFile = new File(URLDecoder.decode(url.getFile(), "utf-8"));
			}
			catch (UnsupportedEncodingException e)
			{
				logger.error(e.getMessage(), e);
			}
		}
		
		if(driverFile != null)
		{
			return driverFile.getAbsolutePath();
		}
		else
		{
			return "";
		}
	}
	
	/**
	 * 转为为初始化的驱动
	 * @param driver
	 * @return
	 */
	public WebDriver turnToRootDriver(WebDriver driver)
	{
		return driver.switchTo().defaultContent();
	}

	/**
	 * 打开指定地址
	 * @param url
	 */
	public void openUrl(String url)
	{
		driver.get(url);
	}

	/**
	 * 关闭浏览器引擎
	 */
	public void close()
	{
		if(driver != null)
		{
			driver.quit();
		}
	}

	/**
	 * @return 引擎对象
	 */
	public WebDriver getDriver()
	{
		return driver;
	}

	/**
	 * @return 引擎名称
	 */
	public String getDriverStr()
	{
		return driverStr;
	}

	/**
	 * 设置引擎名称
	 * @param driverStr
	 */
	public void setDriverStr(String driverStr)
	{
		this.driverStr = driverStr;
	}

	public String getRemoteStr()
	{
		return remoteStr;
	}

	public void setRemoteStr(String remoteStr)
	{
		this.remoteStr = remoteStr;
	}

	/**
	 * @return 超时时间
	 */
	public long getTimeout()
	{
		return timeout;
	}

	/**
	 * 设定超时时间
	 * @param timeout
	 */
	public void setTimeout(long timeout)
	{
		this.timeout = timeout;
	}

	/**
	 * @return 全屏返回true，否则返回false
	 */
	public boolean isFullScreen()
	{
		return fullScreen;
	}

	/**
	 * 设置是否要全屏
	 * @param fullScreen
	 */
	public void setFullScreen(boolean fullScreen)
	{
		this.fullScreen = fullScreen;
	}

	/**
	 * @return 浏览器宽度
	 */
	public int getWidth()
	{
		return width;
	}

	/**
	 * 设置浏览器宽度
	 * @param width
	 */
	public void setWidth(int width)
	{
		this.width = width;
	}

	/**
	 * @return 浏览器高度
	 */
	public int getHeight()
	{
		return height;
	}

	/**
	 * 设置浏览器高度
	 * @param height
	 */
	public void setHeight(int height)
	{
		this.height = height;
	}

	/**
	 * @return the maximize
	 */
	public boolean isMaximize()
	{
		return maximize;
	}

	/**
	 * @param maximize the maximize to set
	 */
	public void setMaximize(boolean maximize)
	{
		this.maximize = maximize;
	}
	
	/**
	 * 计算工具栏高度
	 * @return
	 */
	public int computeToolbarHeight()
	{
		JavascriptExecutor jsExe = (JavascriptExecutor) driver;
		Object objectHeight = jsExe.executeScript("return window.outerHeight - window.innerHeight;");
		if(objectHeight instanceof Long)
		{
			toolbarHeight = ((Long) objectHeight).intValue();
		}

		return toolbarHeight;
	}

	/**
	 * @return the toolbarHeight
	 */
	public int getToolbarHeight()
	{
		return toolbarHeight;
	}
}
