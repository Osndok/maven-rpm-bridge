package com.github.osndok.mrb.plugin.b;

import javax.module.ReactorClients;
import javax.module.ReactorEntry;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Callable;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;

/**
 * Created by robert on 4/8/15.
 */
@ReactorEntry(directory="/usr/local/etc/fake-reactor.d", key="TEST_KEY", value="TEST_VALUE")
public
class Reactive implements Callable<Boolean>
{
	@Override
	public
	Boolean call() throws Exception
	{
		final
		ReactorClients reactorClients=new ReactorClients("/usr/local/etc/fake-reactor.d");

		final
		List<String> names = reactorClients.names();

		if (names.size()!=1)
		{
			System.err.println("expecting one client (myself), found: "+names.size());
			return FALSE;
		}

		final
		String myName=names.get(0);

		final
		Properties properties = reactorClients.get(myName);

		if (properties==null)
		{
			System.err.println("unable to get my own properties");
			return FALSE;
		}

		for (String key : properties.stringPropertyNames())
		{
			System.out.print(key);
			System.out.print('=');
			System.out.println(properties.getProperty(key));
		}

		return TRUE;
	}
}
