package com.github.osndok.mrb.grinder.rpm;

import com.github.osndok.mrb.grinder.DependencyNotProcessedException;
import com.github.osndok.mrb.grinder.Main;
import com.github.osndok.mrb.grinder.MavenInfo;
import com.github.osndok.mrb.grinder.ObsoleteJarException;
import com.github.osndok.mrb.grinder.util.Exec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.module.ModuleKey;
import java.io.*;
import java.sql.*;

/**
 * The "Registry" is currently a flat file stashed in the target rpm repo that contains all the
 * maven artifact mappings (to major version numbers) that this software has successfully grinded
 * or obsoleted (b/c of a newer, pre-existing, but compatible version).
 *
 * This is required to solve an otherwise-insurmountable problem of "given a modules version number,
 * what is it's 'compatible' major version number [given the history of created rpms]".
 *
 * TODO: improve Registry performance on large data-sets with an embedded database, rather than scanning a flat file. Maybe we can hook directly into the yum sqlite database???
 */
public
class Registry
{
	static
	{
		try
		{
			Class.forName("org.sqlite.JDBC");
		}
		catch (ClassNotFoundException e)
		{
			throw new RuntimeException(e);
		}
	}

	private final
	File databaseFile;

	private final
	Connection connection;

	public
	Registry(RPMRepo rpmRepo) throws SQLException
	{
		this.databaseFile = new File(rpmRepo.getDirectory(), "repodata/maven-rpms.db");

		final
		boolean createTables=!(databaseFile.exists());
		this.connection = DriverManager.getConnection("jdbc:sqlite:"+databaseFile.getAbsolutePath());

		if (createTables)
		{
			createTables(connection);
		}
	}

	private static
	void createTables(Connection connection) throws SQLException
	{
		Statement s = connection.createStatement();
		try
		{
			s.executeUpdate(
				"CREATE TABLE processed ("+
					"groupId      TEXT NOT NULL,"+
					"artifactId   TEXT NOT NULL,"+
					"version      TEXT NOT NULL,"+
					"packaging    TEXT NOT NULL,"+
					"classifier   TEXT,"+
					"moduleName   TEXT NOT NULL,"+
					"majorVersion TEXT NOT NULL,"+
					"minorVersion TEXT,"+
					"jarHash      TEXT NOT NULL"+
				")"
			);
		}
		finally
		{
			s.close();
		}
	}

	private static final
	String MODULE_KEY="moduleName, majorVersion, minorVersion";

	private static final
	ModuleKey moduleKey(ResultSet rs) throws SQLException
	{
		return new ModuleKey(rs.getString(1), rs.getString(2), rs.getString(3));
	}

	private static final
	String MAVEN_INFO="groupId, artifactId, version";

	private static final
	MavenInfo mavenInfo(ResultSet rs) throws SQLException
	{
		return new MavenInfo(rs.getString(1), rs.getString(2), rs.getString(3));
	}

	public
	boolean contains(MavenInfo mavenInfo) throws IOException
	{
		return get(mavenInfo)!=null;
	}

	public
	ModuleKey get(MavenInfo mavenInfo) throws IOException
	{
		try
		{
			PreparedStatement ps = connection.prepareStatement("SELECT "+MODULE_KEY+" FROM processed WHERE groupId=? AND artifactId=? AND version=? AND classifier=? LIMIT 1;");

			try
			{
				ps.setString(1, mavenInfo.getGroupId());
				ps.setString(2, mavenInfo.getArtifactId());
				ps.setString(3, mavenInfo.getVersion());

				//NB: *Apparently* the sqlite-jdbc layer translates nulls to empty strings? or doesn't let you match nulls?
				if (mavenInfo.getClassifier()==null)
				{
					ps.setString(4, "");
				}
				else
				{
					ps.setString(4, mavenInfo.getClassifier());
				}

				ResultSet resultSet = ps.executeQuery();

				try
				{
					if (resultSet.next())
					{
						return moduleKey(resultSet);
					}
					else
					{
						return null;
					}
				}
				finally
				{
					resultSet.close();
				}
			}
			finally
			{
				ps.close();
			}
		}
		catch (SQLException e)
		{
			throw new IOException(e);
		}
		/*
		final
		File file = infoToMajorMap;

		if (!file.exists())
		{
			//file.createNewFile();
			return false;
		}

		BufferedReader br = new BufferedReader(new FileReader(file));
		try
		{
			String line;
			while ((line = br.readLine()) != null)
			{
				if (mavenInfo.majorVersionFromParsableLineMatch(line) != null)
				{
					return true;
				}
			}
		}
		finally
		{
			br.close();
		}

		return false;
		*/
	}

	public
	void shouldNotContain(MavenInfo mavenInfo) throws ObsoleteJarException, IOException
	{
		ModuleKey moduleKey=get(mavenInfo);

		if (moduleKey!=null)
		{
			throw new ObsoleteJarException(mavenInfo+" is already in Registry: "+databaseFile, moduleKey);
		}

		/*
		final
		File file = infoToMajorMap;

		if (!file.exists())
		{
			//file.createNewFile();
			return;
		}

		BufferedReader br = new BufferedReader(new FileReader(file));
		try
		{
			String line;
			while ((line = br.readLine())!=null)
			{
				String majorVersion=mavenInfo.majorVersionFromParsableLineMatch(line);

				if (majorVersion!=null)
				{
					ModuleKey moduleKey=new ModuleKey(mavenInfo.getModuleNameCandidate(), majorVersion, null);
					throw new ObsoleteJarException(mavenInfo+" is already in Registry: "+file, moduleKey);
				}
			}
		}
		finally
		{
			br.close();
		}
		*/
	}

	public
	void append(MavenInfo mavenInfo, ModuleKey moduleKey, File jarFile) throws IOException
	{
		String jarHash= Exec.toString("sha256sum", jarFile.getAbsolutePath()).substring(0, 64);

		String majorVersion=moduleKey.getMajorVersion();

		if (majorVersion==null)
		{
			majorVersion="snapshot";
		}

		try
		{
			PreparedStatement ps = connection.prepareStatement("INSERT INTO processed (groupId,artifactId,version,packaging,classifier,moduleName,majorVersion,minorVersion,jarHash) VALUES (?,?,?,?,?,?,?,?,?)");
			ps.setString(1, mavenInfo.getGroupId());
			ps.setString(2, mavenInfo.getArtifactId());
			ps.setString(3, mavenInfo.getVersion());
			ps.setString(4, "jar");

			if (mavenInfo.getClassifier()==null)
			{
				ps.setString(5, "");
			}
			else
			{
				ps.setString(5, mavenInfo.getClassifier());
			}

			ps.setString(6, moduleKey.getModuleName());
			ps.setString(7, majorVersion);

			if (moduleKey.getMinorVersion()==null)
			{
				ps.setString(8, "");
			}
			else
			{
				ps.setString(8, moduleKey.getMinorVersion());
			}

			ps.setString(9, jarHash);
			ps.executeUpdate();
		}
		catch (SQLException e)
		{
			throw new IOException(e);
		}

		ModuleKey justInserted=get(mavenInfo);

		if (justInserted==null)
		{
			throw new AssertionError("just inserted "+mavenInfo+" / "+moduleKey+", but cannot fetch it");
		}

		/*
		final
		File file=infoToMajorMap;

		final
		String line=mavenInfo.toParsableLine(moduleKey.getMajorVersion());

		final
		boolean append=true;

		final
		FileWriter out=new FileWriter(file, append);

		try
		{
			//We rely on the fact that short writes (<512-4096 bytes) are atomic if "O_APPEND" is true.
			out.write(line);
		}
		finally
		{
			out.close();
		}
		*/
	}

	public
	String getMajorVersionFor(MavenInfo mavenInfo, RPMRepo rpmRepo) throws DependencyNotProcessedException, IOException
	{
		ModuleKey moduleKey=get(mavenInfo);
		//String retval=majorFromFirstLineThatMatches(mavenInfo);

		if (moduleKey!=null) return moduleKey.getMajorVersion();

		log.warn("unable to locate dependency: {}", mavenInfo);

		//TODO: make this into a depth counter (e.g. to bomb out [eventually] on circular dependencies), and on by default?
		if (Main.RECURSIVE)
		{
			try
			{
				return new Main(rpmRepo).grindMavenArtifact(mavenInfo).getMajorVersion();
			}
			catch (ObsoleteJarException e)
			{
				//This can happen, for example, if we have a newer jar than the given dep... so it's not always an error.
				log.warn("did not find item in registry, but claimedly obsolete: {}", e.toString());

				//Is it in the registry now?
				//retval=majorFromFirstLineThatMatches(mavenInfo);
				moduleKey=get(mavenInfo);

				if (moduleKey==null)
				{
					throw new IOException("did not find item in registry (before *or* after), but obsolete?", e);
				}
				else
				{
					return moduleKey.getMajorVersion();
				}
			}
		}
		else
		{
			throw new DependencyNotProcessedException(mavenInfo);
		}
	}

	/*
	private
	String majorFromFirstLineThatMatches(MavenInfo mavenInfo) throws IOException
	{
		final
		File file=infoToMajorMap;

		final
		BufferedReader br=new BufferedReader(new FileReader(file));

		try
		{
			String line;
			while ((line=br.readLine())!=null)
			{
				String retval=mavenInfo.majorVersionFromParsableLineMatch(line);

				if (retval!=null)
				{
					return retval;
				}
			}
		}
		finally
		{
			br.close();
		}

		return null;
	}
	*/

	private static final Logger log = LoggerFactory.getLogger(Registry.class);

	public
	MavenInfo getMavenInfoFor(File jarFile) throws IOException
	{
		String jarHash= Exec.toString("sha256sum", jarFile.getAbsolutePath()).substring(0, 64);

		try
		{
			PreparedStatement ps=connection.prepareStatement("SELECT "+MAVEN_INFO+" FROM processed WHERE jarHash=?;");
			try
			{
				ps.setString(1, jarHash);

				ResultSet resultSet = ps.executeQuery();
				try
				{
					if (resultSet.next())
					{
						return mavenInfo(resultSet);
					}
					else
					{
						return null;
					}
				}
				finally
				{
					resultSet.close();
				}
			}
			finally
			{
				ps.close();
			}
		}
		catch (SQLException e)
		{
			throw new IOException(e);
		}
		/*
		final
		File file=jarToInfoMap;

		if (!file.exists())
		{
			return null;
		}

		final
		BufferedReader br=new BufferedReader(new FileReader(file));

		try
		{
			String line;
			while ((line=br.readLine())!=null)
			{
				if (line.startsWith(jarFileName))
				{
					return mavenInfoFromRestOfLine(line.substring(jarFileName.length()+1));
				}
			}
		}
		finally
		{
			br.close();
		}

		return null;
		*/
	}

	/*
	private
	MavenInfo mavenInfoFromRestOfLine(String s) throws IOException
	{
		String[] bits=s.split(":");

		if (bits.length!=3)
		{
			throw new IOException("Expecting exactly three fields, got "+bits.length+" for: "+s);
		}

		String groupId=bits[0].trim();
		String artifactId=bits[1].trim();
		String version=bits[2].trim();

		return new MavenInfo(groupId, artifactId, version);
	}
	*/
}
