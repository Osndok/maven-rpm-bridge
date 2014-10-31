package com.github.osndok.mrb.grinder;

/**
 * Created by robert on 10/30/14.
 */
public
class MavenInfo
{
	private final
	String groupId;

	private final
	String artifactId;

	private final
	String version;

	public
	MavenInfo(String groupId, String artifactId, String version)
	{
		if (groupId   ==null) throw new NullPointerException("groupIp is missing");
		if (artifactId==null) throw new NullPointerException("artifactId is missing");
		if (version   ==null) throw new NullPointerException("version is missing");

		this.groupId = groupId;
		this.artifactId = artifactId;
		this.version = version;
	}

	public
	String getGroupId()
	{
		return groupId;
	}

	public
	String getArtifactId()
	{
		return artifactId;
	}

	public
	String getVersion()
	{
		return version;
	}

	public
	String getModuleNameCandidate()
	{
		if (isTooCommonOrSimpleToBeAModuleName(artifactId))
		{
			return groupId+"-"+artifactId;
		}
		else
		{
			return artifactId;
		}
	}

	private static
	boolean isTooCommonOrSimpleToBeAModuleName(String s)
	{
		//NB: should at least catch "api", "parent", "test"...
		//TODO: list all artifactIds on maven central, to find those "too short", or "too common" (across groups).
		return s.length()<5 || s.equals("parent");
	}

	@Override
	public
	boolean equals(Object o)
	{
		if (this == o)
		{
			return true;
		}

		if (o == null || getClass() != o.getClass())
		{
			return false;
		}

		MavenInfo mavenInfo = (MavenInfo) o;

		if (!artifactId.equals(mavenInfo.artifactId))
		{
			return false;
		}

		if (!groupId.equals(mavenInfo.groupId))
		{
			return false;
		}

		if (!version.equals(mavenInfo.version))
		{
			return false;
		}

		return true;
	}

	@Override
	public
	int hashCode()
	{
		int result = groupId.hashCode();
		result = 31 * result + artifactId.hashCode();
		result = 31 * result + version.hashCode();
		return result;
	}

	@Override
	public
	String toString()
	{
		return "MavenInfo{" +
				   "groupId='" + groupId + '\'' +
				   ", artifactId='" + artifactId + '\'' +
				   ", version='" + version + '\'' +
				   '}';
	}

}
