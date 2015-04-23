package com.github.osndok.mrb.grinder.rpm;

import com.github.osndok.mrb.grinder.MavenInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.module.ModuleKey;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * This is used to route private groupIds (as matched by prefix) to separate rpm
 * repositories for better organization.
 *
 * Created by robert on 3/25/15.
 */
public
class RPMManifold
{
	public static final String JAVAX_MODULE_EXEC = "mrb-which-repo";

	public static
	RPMRepo getRepoFor(MavenInfo mavenInfo)
	{
		return getRepoFor(mavenInfo.getGroupId());
	}

	public static
	RPMRepo getRepoFor(String groupId)
	{
		return getInstance().repoFor(groupId);
	}

	private
	RPMRepo repoFor(String groupId)
	{
		int longestPrefixLength=0;
		RPMRepo retval=defaultRepo;

		for (Map.Entry<String, RPMRepo> me : reposByPrefix.entrySet())
		{
			final
			String groupPrefix=me.getKey();

			final
			RPMRepo rpmRepo=me.getValue();

			int prefixLength=groupPrefix.length();

			if (prefixLength>longestPrefixLength && groupId.startsWith(groupPrefix))
			{
				log.debug("{} matches: {} / {} / {} / {}", rpmRepo, prefixLength, longestPrefixLength, groupPrefix,
							 groupId);
				longestPrefixLength=prefixLength;
				retval=rpmRepo;
			}
			else
			{
				log.debug("{} does *not* match: {} / {} / {} / {}", rpmRepo, prefixLength, longestPrefixLength, groupPrefix, groupId);
			}
		}

		log.info("{} -> {}", groupId, retval);

		return retval;
	}

	private static final
	Logger log = LoggerFactory.getLogger(RPMManifold.class);

	private static
	RPMManifold INSTANCE;

	public static
	RPMManifold getInstance()
	{
		if (INSTANCE == null)
		{
			synchronized (RPMManifold.class)
			{
				if (INSTANCE == null)
				{
					try
					{
						INSTANCE = createInstance();
					}
					catch (IOException e)
					{
						throw new RuntimeException(e);
					}
				}
			}
		}

		return INSTANCE;
	}

	private static
	RPMManifold createInstance() throws IOException
	{
		final
		File MRB_CONFIG = new File(stringFromEnvironmentOrSystemProperty("MRB_CONFIG", "/etc/mrb.props"));

		final
		Properties config = readPropertiesFile(MRB_CONFIG);

		final
		String DEFAULT_REPO = stringFromEnvironmentSystemOrApplicationProperties("DEFAULT_REPO", "/repos/mrb",
																					config);

		final
		RPMRepo defaultRepo = new RPMRepo(new File(DEFAULT_REPO));

		final
		RPMManifold rpmManifold = new RPMManifold(defaultRepo);

		//The expected pattern is "repo.*.prefix" and a matching "repo.*.path".
		//Probably just enumerated ("repo.1.prefix"), but could be named ("repo.private.prefix").
		for (String prefixKey : config.stringPropertyNames())
		{
			if (prefixKey.startsWith("repo.") && prefixKey.endsWith(".prefix"))
			{
				final
				String pathKey = pathKeyFromPrefixKey(prefixKey);

				final
				String prefixValue = config.getProperty(prefixKey);

				final
				String pathValue = config.getProperty(pathKey);

				if (pathValue==null)
				{
					throw new IllegalStateException(MRB_CONFIG+" does not contain matching path entry: "+pathKey);
				}

				rpmManifold.addRoute(prefixValue, pathValue);
			}
			else
			if (prefixKey.endsWith(".path"))
			{
				log.debug("not a routing rule: {}", prefixKey);
			}
			else
			{
				log.warn("not a routing rule: {}", prefixKey);
			}
		}

		return rpmManifold;
	}

	private
	void addRoute(String prefix, String repoPath) throws IOException
	{
		reposByPrefix.put(prefix, new RPMRepo(new File(repoPath)));
	}

	private static
	String pathKeyFromPrefixKey(String prefixKey)
	{
		final
		int lastPeriod=prefixKey.lastIndexOf('.');

		return prefixKey.substring(0, lastPeriod)+".path";
	}

	private static
	Properties readPropertiesFile(File file) throws IOException
	{
		final
		Properties p=new Properties();

		if (file.exists())
		{
			if (!file.canRead())
			{
				throw new AssertionError("unable to read "+file);
			}

			final
			InputStream inputStream=new FileInputStream(file);

			try
			{
				p.load(inputStream);
			}
			finally
			{
				inputStream.close();
			}
		}
		else
		{
			//This is okay... they might have just specified the default/output directory.
		}

		return p;
	}

	private final
	RPMRepo defaultRepo;

	private final
	Map<String, RPMRepo> reposByPrefix = new LinkedHashMap<>(5);

	private
	RPMManifold(RPMRepo defaultRepo)
	{
		this.defaultRepo = defaultRepo;
	}

	public static final
	void main(String[] args)
	{
		boolean WITH_PREFIX = generallyFalseBooleanFromEnvironmentOrSystemProperty("WITH_PREFIX");
		boolean NO_PREFIX = generallyFalseBooleanFromEnvironmentOrSystemProperty("NO_PREFIX");

		if (args.length == 0)
		{
			System.err.println(String.format("usage: %s groupId [groupId [groupId ...]]", JAVAX_MODULE_EXEC));
			System.err.println("theory: given a maven group id, return the configured/target rpm repo therefor");
			System.exit(1);
		}
		else if (WITH_PREFIX || (args.length > 1 && !NO_PREFIX))
		{
			for (String arg : args)
			{
				System.out.print(arg);
				System.out.print('\t');
				System.out.println(getRepoFor(arg).getDirectory().getAbsolutePath());
			}

			System.exit(0);
		}
		else
		{
			for (String arg : args)
			{
				System.out.println(getRepoFor(arg).getDirectory().getAbsolutePath());
			}

			System.exit(0);
		}
	}

	private static
	String stringFromEnvironmentOrSystemProperty(String key, String _default)
	{
		String value = System.getProperty(key);

		if (value == null)
		{
			value = System.getenv(key);
		}

		if (value == null)
		{
			return _default;
		}
		else
		{
			return value;
		}
	}

	private static
	String stringFromEnvironmentSystemOrApplicationProperties(String key, String _default, Properties properties)
	{
		String value = System.getProperty(key);

		if (value == null)
		{
			value = System.getenv(key);

			if (value == null)
			{
				value = properties.getProperty(key);
			}
		}

		if (value == null)
		{
			return _default;
		}
		else
		{
			return value;
		}
	}

	private static
	boolean generallyFalseBooleanFromEnvironmentOrSystemProperty(String key)
	{
		String value = System.getProperty(key);

		if (value == null)
		{
			value = System.getenv(key);
		}

		if (value == null)
		{
			return false;
		}
		else
		{
			return stringToBoolean(value);
		}
	}

	private static
	boolean stringToBoolean(String s)
	{
		if (s.isEmpty())
		{
			return false;
		}

		final
		char c = s.charAt(0);

		return (c == '1' || c == 't' || c == 'T' || c == 'y' || c == 'Y');
	}


	public static
	MavenInfo getMavenInfoFromAnyRegistry(File file) throws IOException
	{
		final
		String jarHash = RPMRegistry.getJarHash(file);

		return getInstance().getMavenInfoFromAnyRegistry(jarHash);
	}

	private
	MavenInfo getMavenInfoFromAnyRegistry(String jarHash) throws IOException
	{
		for (RPMRepo rpmRepo : reposByPrefix.values())
		{
			final
			MavenInfo mavenInfo=rpmRepo.getRpmRegistry().getMavenInfoFor(jarHash);

			if (mavenInfo!=null)
			{
				return mavenInfo;
			}
		}

		return defaultRepo.getRpmRegistry().getMavenInfoFor(jarHash);
	}

	public
	RPM getAnyRpmMatching(ModuleKey moduleKey)
	{
		for (RPMRepo rpmRepo : reposByPrefix.values())
		{
			final
			RPM rpm=rpmRepo.get(moduleKey);

			if (rpm!=null)
			{
				return rpm;
			}
		}

		return defaultRepo.get(moduleKey);
	}
}
