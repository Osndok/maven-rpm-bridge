package com.github.osndok.mrb.grinder.api;

/**
 * Created by robert on 12/5/14.
 */
public
interface WarProcessingPlugin
{
	SpecShard getSpecShard(WarFileInfo warFileInfo, SpecSourceAllocator specSourceAllocator);
}
