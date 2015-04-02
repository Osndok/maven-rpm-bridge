package com.github.osndok.mrb.grinder.webapps;

import com.allogy.infra.hyperjetty.common.ServletName;

import javax.module.ModuleKey;
import java.io.*;
import java.util.*;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

/**
 * Created by robert on 3/23/15.
 */
public abstract
class AbstractHyperjettyWebappFunctions
{
	private static final boolean ACTIVATE_ON_START = true;

	protected final
	String directory = "/usr/lib/hyperjetty";

	protected
	int servicePort;

	public abstract
	String getSubPackageName();

	protected
	String getConfigFilePath()
	{
		return "/usr/lib/hyperjetty/"+servicePort+"."+getSubPackageName();
	}

	protected
	String getMasterConfigFilePath()
	{
		return "/usr/lib/hyperjetty/"+servicePort+".config";
	}

	public static final String HJ_SOCKETS_DIRECTORY=System.getProperty("HJ_SOCKETS_DIRECTORY", "/sock");

	public static
	String getUDSSocketPath(ModuleKey moduleKey)
	{
		return HJ_SOCKETS_DIRECTORY+'/'+moduleKey;
	}

	/**
	 * TODO: BUG: the config file is overwritten for each HJ-compatible subpackage, so only one is compatible.
	 *
	 * @param moduleKey
	 * @param warFile
	 * @return
	 */
	protected
	Map<String, String> hyperJettyConfigFileContentsByPath(ModuleKey moduleKey, File warFile)
	{
		/* TEMPLATE:
----------REQUIRED/FUNCTIONALLY-----------------------------------
SERVICE_PORT=10088
NAME=capillary-wui
LOG_BASE=/var/log/hyperjetty/capillary-wui-20150323-36

----------REQUIRED/TECHNICALLY-----------------------------------
ORIGINAL_WAR=/tmp/capillary-wui.war
DATE_CREATED=2015-01-25 03\:44\:04.307 UTC

----------OPTIONAL/IMPORTANT-------------------------------------
JMX_PORT=11088
CONTEXT_PATH=/wui
PID=8066
VERSION=0.6.128
TAGS=testing,production,ppw

----------OPTIONAL/CONFIG-------------------------------------
JAVA_org.eclipse.jetty.server.Request.maxFormContentSize=524288
STACK_SIZE=1m
HEAP_SIZE=100m
PERM_SIZE=70m
OPTIONS=memcached-sessions

----------OPTIONAL/TRANSIENT-------------------------------------
#Comments like the following...
#Written by hyperjetty service class
#Mon Mar 23 18:18:37 UTC 2015
DATE_RESPAWNED=2015-03-23 18\:18\:36.923 UTC
DATE_STARTED=2015-03-23 18\:18\:37.077 UTC
RESPAWN_COUNT=13
SELF=/usr/lib/hyperjetty/10088.config

------------------------------------------------------------------
		 */
		final
		String configFilePath=getConfigFilePath();

		Properties p;
		{
			try
			{
				p = getEmbeddedAppProperties(warFile);
			}
			catch (IOException e)
			{
				throw new RuntimeException(e);
			}

			if (p==null)
			{
				p=new Properties();
			}
		}

		p.setProperty("SELF", configFilePath);
		p.setProperty("SERVICE_PORT", Integer.toString(servicePort));
		p.setProperty("DATE_CREATED", new Date().toString());

		String name=p.getProperty("NAME");

		if (name==null)
		{
			name=guessName(warFile, moduleKey.getModuleName());
			p.setProperty("NAME", name);
		}

		maybeSet(p, "VERSION", moduleKey.getMajorVersion() + "." + moduleKey.getMinorVersion());
		maybeSet(p, "LOG_BASE", "/var/log/hyperjetty/"+name);
		maybeSet(p, "JMX_PORT", Integer.toString(servicePort+1000));
		maybeSet(p, "CONTEXT_PATH", "/");

		if (ACTIVATE_ON_START)
		{
			//Any non-negative integer, that is unlikely to be a java process...
			p.setProperty("PID", "12345");
		}
		else
		{
			//Code for "stopped on purpose"
			p.setProperty("PID", "-1");
		}

		final
		Map<String,String> retval=new HashMap<>();
		{
			retval.put(configFilePath, toString(p));
		}

		return retval;
	}

	private
	String guessName(File warFile, String moduleName)
	{
		try
		{
			return _XXX_guessNameFromWar(warFile.getName());
		}
		catch (Throwable t)
		{
			System.err.println("okay?: "+t);
			return moduleName;
		}
	}

	//TODO: copied from hyperjetty, and contains one of the few *actual* links into it's code base, that 3rd parties will probably not want!
	private
	String _XXX_guessNameFromWar(final String warBaseName)
	{
		String retval;
		{
			int period = warBaseName.lastIndexOf('.');
			if (period > 0)
			{
				retval = warBaseName.substring(0, period);
			}
			else
			{
				retval = warBaseName;
			}
		}

		return ServletName.filter(retval);
	}

	private
	String toString(Properties p)
	{
		final
		ByteArrayOutputStream out=new ByteArrayOutputStream();

		try
		{
			p.store(out, "written from mrb webapp grinder, " + getClass());

			return out.toString("UTF-8");
		}
		catch (RuntimeException e)
		{
			throw e;
		}
		catch (Exception e)
		{
			throw new RuntimeException(e);
		}
	}

	protected
	Collection<String> buildRequiresHyperjettyConfigurator()
	{
		//TODO: in order to get accurate memory requirements, context path, java defines, option packages, et al. We need to splinter-off the hyperjetty-config class, including the getEmbeddedAppProperties function.
		return null;
	}

	private
	PrintStream log = System.err;

	//FIXME: code copied from hyperjetty, should be 'linked' in case it changes and for completeness/correctness.
	private
	Properties getEmbeddedAppProperties(File warFile) throws IOException
	{
		JarFile jarFile = new JarFile(warFile);
		try
		{
			ZipEntry zipEntry = jarFile.getEntry("WEB-INF/app.properties");
			if (zipEntry == null)
			{
				log.println("No app.properties");
				return null;
			}
			InputStream inputStream = jarFile.getInputStream(zipEntry);
			if (inputStream == null)
			{
				log.println("cannot get inputstream for app.properties");
				return null;
			}
			else
			{
				try
				{
					Properties retval = new Properties();
					retval.load(inputStream);
					log.println("read " + retval.size() + " properties from embedded app.properties file");
					return retval;
				}
				finally
				{
					inputStream.close();
				}
			}
		}
		finally
		{
			jarFile.close();
		}
	}

	private static
	void maybeSet(Properties p, String key, String ifNotPresent)
	{
		//String key=keyCode.toString();
		if (!p.containsKey(key))
		{
			p.setProperty(key, ifNotPresent);
		}
	}

	protected
	String getPostInstallPhase(ModuleKey moduleKey)
	{
		final
		File myConfigFile=new File(getConfigFilePath());

		final
		File masterConfig=new File(getMasterConfigFilePath());

		final
		StringBuilder sb=new StringBuilder();

		sb.append("ln -sf ").append(myConfigFile.getName()).append(" .").append(masterConfig.getAbsolutePath()).append('\n');

		sb.append("mkdir -p ").append(HJ_SOCKETS_DIRECTORY).append('\n');

		/*
		 * Hmm... should we replace an existing UDS socket link?
		 * -?-
		 * No, because it *might* contain a stream of production traffic (a live rpm upgrade in production?).
		 * No, because it might overwrite an intentionally-set UDS directive (a configuration, of sorts).
		 * Yes, because it will update an incorrectly set port number.
		 * Yes, because one expects that installing the rpm will make it work.
		 */
		sb.append("ln -sf hj/").append(servicePort).append(" .").append(getUDSSocketPath(moduleKey)).append('\n');

		return sb.toString();
	}

	protected
	String getPostUninstallPhase(ModuleKey moduleKey)
	{
		return "\n"+
				   "if [ $1 -eq 0 ]; then\n"+
				   "\trm -fv "+getUDSSocketPath(moduleKey)+' '+getMasterConfigFilePath()+"\n"+
				   "fi\n"
			;
	}

}
