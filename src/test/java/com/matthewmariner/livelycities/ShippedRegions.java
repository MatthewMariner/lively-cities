package com.matthewmariner.livelycities;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Enumerates the region ids actually present in
 * {@code src/main/resources/RegionData/}.
 *
 * <p>Discovered from the classpath rather than hard-coded, so adding a region
 * file automatically brings it under test — including the check that every
 * animation name it uses resolves.
 */
final class ShippedRegions
{
	private ShippedRegions()
	{
	}

	static List<Integer> ids()
	{
		URL url = ShippedRegions.class.getClassLoader().getResource(RegionDataLoader.DEFAULT_RESOURCE_PREFIX);
		if (url == null)
		{
			throw new IllegalStateException(
				"RegionData/ is not on the test classpath — main resources are not being processed");
		}

		File directory;
		try
		{
			directory = new File(url.toURI());
		}
		catch (URISyntaxException e)
		{
			throw new IllegalStateException("could not resolve " + url, e);
		}

		File[] files = directory.listFiles((dir, name) -> name.endsWith(".json"));
		if (files == null || files.length == 0)
		{
			throw new IllegalStateException("no region files under " + directory);
		}

		List<Integer> ids = new ArrayList<>(files.length);
		for (File file : files)
		{
			String base = file.getName();
			ids.add(Integer.parseInt(base.substring(0, base.length() - ".json".length())));
		}

		Collections.sort(ids);
		return ids;
	}
}
