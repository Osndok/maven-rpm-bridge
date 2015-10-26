package com.github.osndok.mrb.grinder;

import com.github.osndok.mrb.grinder.util.MavenDepRange;

import java.io.Serializable;

/**
 * Created by robert on 10/30/14.
 */
public
class MavenInfo implements Serializable
{
	private final
	String groupId;

	private final
	String artifactId;

	private final
	String version;

	private final
	String classifier;

	private final
	boolean optional;

	private final
	String packaging;

	public
	MavenInfo(String groupId, String artifactId, String version)
	{
		if (groupId == null) throw new NullPointerException("groupId is missing");
		if (artifactId == null) throw new NullPointerException("artifactId is missing");
		if (version   ==null) throw new NullPointerException("version is missing");

		this.groupId = groupId;
		this.artifactId = artifactId;
		this.version = noVersionRanges(version);
		this.optional = false;
		this.classifier = null;
		this.packaging = "jar";
	}

	public
	MavenInfo(String groupId, String artifactId, String version, String classifier, String packaging, boolean optional)
	{
		if (groupId == null) throw new NullPointerException("groupIp is missing");
		if (artifactId == null) throw new NullPointerException("artifactId is missing");
		if (version   ==null) throw new NullPointerException("version is missing");

		this.groupId = groupId;
		this.artifactId = artifactId;
		this.version = noVersionRanges(version);
		this.optional=optional;
		this.packaging=packaging;
		this.classifier=("".equals(classifier)?null:classifier);
	}

	private static
	String noVersionRanges(String version)
	{
		if (MavenDepRange.isLikely(version))
		{
			try
			{
				return MavenDepRange.getAnyValidVersionFromRange(version);
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
		else
		{
			return version;
		}
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
		final
		String suffix=(classifier==null?"":"-"+classifier);

		if (isTooCommonOrSimpleToBeAModuleName(artifactId))
		{
			if (groupId.contains(artifactId))
			{
				return groupId+suffix;
			}
			else
			{
				return groupId + "-" + artifactId+suffix;
			}
		}
		else
		{
			return artifactId+suffix;
		}
	}

	private static
	boolean isTooCommonOrSimpleToBeAModuleName(String s)
	{
		final
		int l=s.length();

		//NB: should at least catch "api", "parent", "test"...
		//NB: the desire to have at least five-character words can be subverted with english suffixes (like 'tools' or 'utils', which have only 4 meaningful characters)
		//TODO: needs more analysis/time; e.g. list all artifactIds on maven central, to find those "too short", or "too common" (across groups).
		return l<5 || (l==5 && s.endsWith("s")) || s.equals("parent") || s.equals("plugin");
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

		if (classifier==null ? mavenInfo.classifier!=null : !classifier.equals(mavenInfo.classifier))
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

		if (classifier!=null)
		{
			result = 31 * result + classifier.hashCode();
		}

		return result;
	}

	private
	String stringValue;

	/**
	 * This must match the artifact spec as required by dependency:copy, et al.
	 * see parse() below...
	 *
	 * @return
	 */
	@Override
	public
	String toString()
	{
		if (stringValue==null)
		{
			if (classifier==null)
			{
				stringValue = groupId + ':' + artifactId + ':' + version;
			}
			else
			{
				//https://maven.apache.org/pom.html#Maven_Coordinates
				//"groupId:artifactId:packaging:classifier:version"
				stringValue = groupId + ':' + artifactId + ':' + packaging + ':' + classifier + ':' + version;
			}
		}

		return stringValue;
	}

	/**
	 * This must match the artifact spec as required by dependency:copy, et al.
	 * usage output indicates that the
	 * expected format is <groupId>:<artifactId>[:<extension>[:<classifier>]]:<version>
	 *                       -0-   :    -1-      :    -2-     :    -3-       :  -2/4-
	 *
	 * Yet dependency:copy seems to want it in this order:
	 *                    <groupId>:<artifactId>:<version>:<extension>:<classifier>
	 *                       -0-   :    -1-     :   -2-   :    -3-    :    -4-
	 * @return
	 */
	public static
	MavenInfo parse(String s)
	{
		final
		String[] bits=s.split(":");

		/*
		https://maven.apache.org/pom.html#Maven_Coordinates
		 */
		if (bits.length==3)
		{
			return new MavenInfo(bits[0], bits[1], bits[2]);
		}
		else
		if (bits.length==4)
		{
			String groupId=bits[0];
			String artifactId=bits[1];
			String packaging=bits[2];
			String version=bits[3];

			//https://maven.apache.org/pom.html#Maven_Coordinates
			//"groupId:artifactId:packaging:version"
			if (!looksLikeMavenPackaging(packaging))
			{
				throw new IllegalArgumentException("unparsable 4-segment maven identifier: "+s);
			}

			String classifier=null;
			boolean optional=false;

			return new MavenInfo(groupId, artifactId, version, classifier, packaging, optional);
		}
		else
		if (bits.length==5)
		{
			//https://maven.apache.org/pom.html#Maven_Coordinates
			//"groupId:artifactId:packaging:classifier:version"
			String groupId=bits[0];
			String artifactId=bits[1];
			String packaging=bits[2];
			String classifier=bits[3];
			String version=bits[4];
			boolean optional=false;

			if (!looksLikeMavenPackaging(packaging))
			{
				throw new IllegalArgumentException("unparsable 5-segment maven identifier: "+s);
			}

			//TODO: somewhere, I saw this pattern being used: "group:artifact:version:packaging:classifier"
			//... but changed this implementation to align with maven's documentation.

			//return new MavenInfo(bits[0], bits[1], bits[4], bits[2], bits[3], false);
			return new MavenInfo(groupId, artifactId, version, classifier, packaging, optional);
		}
		else
		{
			throw new IllegalArgumentException("unparsable maven identifier: "+s);
		}
	}

	private static
	boolean looksLikeMavenPackaging(String s)
	{
		return s.equals("jar")
			|| s.equals("war")
			|| s.equals("pom");
	}

	public
	boolean isOptional()
	{
		return optional;
	}

	private transient
	Boolean snapshot;

	public
	boolean isSnapshot()
	{
		if (snapshot==null)
		{
			snapshot=version.toLowerCase().contains("snap");
		}

		return snapshot;
	}

	public
	String getClassifier()
	{
		return classifier;
	}

	private transient
	String groupIdPath;

	public
	String getGroupIdPath()
	{
		if (groupIdPath==null)
		{
			final
			StringBuilder sb=new StringBuilder();

			final
			String groupId=this.groupId;

			final
			int l=groupId.length();

			for (int i=0; i<l; i++)
			{
				final
				char c=groupId.charAt(i);

				if (c=='.')
				{
					sb.append('/');
				}
				else
				{
					sb.append(c);
				}
			}

			groupIdPath=sb.toString();
		}

		return groupIdPath;
	}
}
