package com.matthewmariner.livelycities;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The one piece of the durability tooling that touches a filesystem. Points at
 * a throwaway temp directory rather than the real {@code ~/.runelite} — the
 * same discipline this project already applies to region data fixtures.
 */
public class CacheAuditReportWriterTest
{
	private File tempRoot;

	@Before
	public void setUp() throws IOException
	{
		tempRoot = Files.createTempDirectory("lively-cities-audit-test").toFile();
	}

	@After
	public void tearDown()
	{
		deleteRecursively(tempRoot);
	}

	@Test
	public void writesTheReportTextVerbatimUnderTheGivenDirectory() throws IOException
	{
		File outputDir = new File(tempRoot, "lively-cities");
		CacheIdAudit.Report report = emptyReport();

		File written = CacheAuditReportWriter.write(outputDir, report);

		assertEquals(new File(outputDir, CacheAuditReportWriter.FILE_NAME), written);
		assertTrue(written.isFile());
		String contents = new String(Files.readAllBytes(written.toPath()), java.nio.charset.StandardCharsets.UTF_8);
		assertEquals(report.toReportText(), contents);
	}

	/**
	 * The plugin's real call site passes a subdirectory that does not exist yet
	 * — {@code RuneLite.RUNELITE_DIR}, not this plugin's own folder inside it.
	 */
	@Test
	public void createsTheOutputDirectoryWhenItDoesNotExistYet()
	{
		File outputDir = new File(tempRoot, "does-not-exist-yet/lively-cities");
		assertTrue(!outputDir.exists());

		File written = tryWrite(outputDir);

		assertTrue(outputDir.isDirectory());
		assertTrue(written.isFile());
	}

	@Test
	public void writingTwiceOverwritesRatherThanAppending() throws IOException
	{
		File outputDir = new File(tempRoot, "lively-cities");

		CacheAuditReportWriter.write(outputDir, emptyReport());
		File secondReportFile = CacheAuditReportWriter.write(outputDir, reportWithOneFailure());

		String contents = new String(Files.readAllBytes(secondReportFile.toPath()), java.nio.charset.StandardCharsets.UTF_8);
		assertEquals("a stale failing id from a fixed run must not linger in the file",
			reportWithOneFailure().toReportText(), contents);
	}

	private File tryWrite(File outputDir)
	{
		try
		{
			return CacheAuditReportWriter.write(outputDir, emptyReport());
		}
		catch (IOException e)
		{
			throw new AssertionError(e);
		}
	}

	private static CacheIdAudit.Report emptyReport()
	{
		CacheIdAudit.DatasetIds dataset = new CacheIdAudit.DatasetIds(
			new TreeSet<>(), new TreeSet<>(), new TreeMap<>(), Collections.emptyList(), 0);
		return CacheIdAudit.run(new FakeClient(), dataset);
	}

	private static CacheIdAudit.Report reportWithOneFailure()
	{
		FakeClient client = new FakeClient();
		client.setUnloadable(42);
		TreeSet<Integer> modelIds = new TreeSet<>();
		modelIds.add(42);
		CacheIdAudit.DatasetIds dataset = new CacheIdAudit.DatasetIds(
			modelIds, new TreeSet<>(), new TreeMap<>(), Collections.emptyList(), 0);
		return CacheIdAudit.run(client, dataset);
	}

	private static void deleteRecursively(File file)
	{
		File[] children = file.listFiles();
		if (children != null)
		{
			for (File child : children)
			{
				deleteRecursively(child);
			}
		}
		file.delete();
	}
}
