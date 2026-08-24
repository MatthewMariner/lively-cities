package com.matthewmariner.livelycities;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The one piece of this project that touches a filesystem — and, since the move, one
 * that lives in the test source set beside the class it tests rather than in the jar
 * the Plugin Hub builds. Points at a throwaway temp directory rather than the real
 * {@code ~/.runelite}, the same discipline this project already applies to region data
 * fixtures.
 *
 * <p>The fixtures are still cache-audit reports, because that is the report with a
 * fixture already built; what is under test is the writing, and the writer has not
 * known what a report <i>is</i> since {@link FrameTimings} became its second caller.
 */
public class ReportWriterTest
{
	/**
	 * Taken from the production constant rather than spelled out again, so a rename
	 * cannot leave this file asserting about a file name nobody writes.
	 */
	private static final String REPORT_FILE_NAME =
		LivelyCitiesDevReportsPlugin.CACHE_AUDIT_REPORT_FILE_NAME;

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

		File written = ReportWriter.write(outputDir, REPORT_FILE_NAME, report.toReportText());

		assertEquals(new File(outputDir, REPORT_FILE_NAME), written);
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

		ReportWriter.write(outputDir, REPORT_FILE_NAME, emptyReport().toReportText());
		File secondReportFile = ReportWriter.write(
			outputDir, REPORT_FILE_NAME, reportWithOneFailure().toReportText());

		String contents = new String(Files.readAllBytes(secondReportFile.toPath()), java.nio.charset.StandardCharsets.UTF_8);
		assertEquals("a stale failing id from a fixed run must not linger in the file",
			reportWithOneFailure().toReportText(), contents);
	}

	/**
	 * The report is the only thing left in the directory afterwards.
	 *
	 * <p>The write goes through a draft file now (see {@link ReportWriter}'s javadoc),
	 * and a draft that survives the write is litter in a directory whose whole purpose
	 * is for a human to open it and read one file.
	 */
	@Test
	public void aWriteLeavesTheReportAndNothingElseBehind()
	{
		File outputDir = new File(tempRoot, "lively-cities");

		tryWrite(outputDir);
		tryWrite(outputDir);

		String[] left = outputDir.list();
		assertNotNull(left);
		assertEquals("only the report may survive the write: " + Arrays.toString(left),
			Collections.singletonList(REPORT_FILE_NAME),
			Arrays.asList(left));
	}

	/**
	 * Two writers on one path never produce a file that is neither report.
	 *
	 * <p><b>The window this closes is new.</b>
	 * {@code LivelyCitiesDevReportsPlugin.reportFrameTimings()} is reachable
	 * from two places — the 300-tick cadence and {@code shutDown} — and both dispatch
	 * through {@code CompletableFuture.runAsync}, so closing the client as a periodic
	 * report begins puts two writers on {@code frame-timings.txt} at once. The old
	 * implementation opened the target with {@code new FileOutputStream(file)}, which
	 * truncates in place: for the length of the write the report on disk is a prefix of
	 * itself, and two of them interleave into a file that is neither.
	 *
	 * <p>So a reader runs flat out against two writers alternating between two texts,
	 * and every single thing it manages to read has to be one of those two texts,
	 * whole. The texts are large enough that a truncating writer is caught within the
	 * first few rounds; with the rename it cannot be caught at all, because a reader
	 * either opens the old file or the new one and both are complete. That asymmetry is
	 * the point — this test cannot pass by being lucky, only by the window being shut.
	 */
	@Test
	public void aReaderRacingTwoWritersNeverSeesAHalfWrittenReport() throws Exception
	{
		File outputDir = new File(tempRoot, "lively-cities");
		String first = filler('a');
		String second = filler('b');

		// One complete report to start from, so "the file does not exist yet" is not
		// one of the states the reader has to tolerate.
		ReportWriter.write(outputDir, REPORT_FILE_NAME, first);
		File report = new File(outputDir, REPORT_FILE_NAME);

		AtomicBoolean writing = new AtomicBoolean(true);
		List<String> torn = Collections.synchronizedList(new ArrayList<>());
		AtomicInteger reads = new AtomicInteger();

		Thread reader = new Thread(() ->
		{
			while (writing.get())
			{
				String seen;
				try
				{
					seen = new String(Files.readAllBytes(report.toPath()), StandardCharsets.UTF_8);
				}
				catch (IOException e)
				{
					// The target disappearing is itself a failure of the contract: a
					// rename replaces it without ever unlinking it first.
					torn.add("unreadable: " + e);
					continue;
				}

				reads.incrementAndGet();
				if (!seen.equals(first) && !seen.equals(second))
				{
					torn.add("length " + seen.length());
				}
			}
		});

		reader.start();
		try
		{
			for (int round = 0; round < ROUNDS; round++)
			{
				ReportWriter.write(outputDir, REPORT_FILE_NAME,
					round % 2 == 0 ? second : first);
			}
		}
		finally
		{
			writing.set(false);
			reader.join();
		}

		assertTrue("the reader has to have actually read something", reads.get() > 0);
		assertEquals("every read must see one whole report or the other, never a prefix "
				+ "and never a mixture: " + torn,
			Collections.emptyList(), torn);

		String[] left = outputDir.list();
		assertNotNull(left);
		assertEquals("and no draft may survive the race: " + Arrays.toString(left),
			1, left.length);
	}

	/** Big enough that a truncating writer is caught, small enough to stay quick. */
	private static final int FILLER_CHARS = 400_000;

	private static final int ROUNDS = 60;

	private static String filler(char c)
	{
		char[] chars = new char[FILLER_CHARS];
		Arrays.fill(chars, c);
		return new String(chars);
	}

	private File tryWrite(File outputDir)
	{
		try
		{
			return ReportWriter.write(outputDir, REPORT_FILE_NAME, emptyReport().toReportText());
		}
		catch (IOException e)
		{
			throw new AssertionError(e);
		}
	}

	private static CacheIdAudit.Report emptyReport()
	{
		CacheIdAudit.DatasetIds dataset = new CacheIdAudit.DatasetIds(
			new TreeSet<>(), new TreeSet<>(), new TreeSet<>(), new TreeMap<>(),
			Collections.emptyList(), 0);
		return CacheIdAudit.run(new FakeClient(), dataset);
	}

	private static CacheIdAudit.Report reportWithOneFailure()
	{
		FakeClient client = new FakeClient();
		client.setUnloadable(42);
		TreeSet<Integer> modelIds = new TreeSet<>();
		modelIds.add(42);
		CacheIdAudit.DatasetIds dataset = new CacheIdAudit.DatasetIds(
			modelIds, new TreeSet<>(), new TreeSet<>(), new TreeMap<>(),
			Collections.emptyList(), 0);
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
