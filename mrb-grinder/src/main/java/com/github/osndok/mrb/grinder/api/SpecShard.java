package com.github.osndok.mrb.grinder.api;

import java.util.Collection;
import java.util.Map;

/**
 * Created by robert on 12/5/14.
 */
public
interface SpecShard
{
	String getSubPackageName();
	String getSubPackageDescription();
	Collection<String> getRpmRequiresLines();
	Map<String,String> getFileContentsByPath();
	Map<String,String> getScriptletBodiesByType();
}
