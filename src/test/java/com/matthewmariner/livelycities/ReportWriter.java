package com.matthewmariner.livelycities;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Writes one of this plugin's developer reports to disk.
 *
 * <p><b>This class lives in the test source set, and that is the point.</b> It is the
 * only class in the project that touches a filesystem, and {@code src/test/java} is
 * not packaged into the jar the Plugin Hub builds — so the shipped plugin contains no
 * {@code java.io.File}, no {@code java.nio.file}, and nothing that can write anywhere.
 * A hub maintainer put the reason plainly on
 * <a href="https://github.com/runelite/plugin-hub/pull/12366">plugin-hub#12366</a>:
 * "file i/o will make your plugin require manually review. if you can not use it your
 * plugin can be automatically reviewed." The capability itself is not lost — both
 * {@code ./gradlew runWithTimings} and {@code ./gradlew auditCacheIds} run on
 * {@code sourceSets.test.runtimeClasspath}, so {@link LivelyCitiesDevReportsPlugin}
 * and this writer are on the classpath of exactly the two launches that want them, and
 * of nothing a hub user can ever start. That arrangement is the one riktenx suggested
 * on <a href="https://github.com/runelite/plugin-hub/pull/13208">plugin-hub#13208</a>:
 * "you can either add a separate debug plugin in the test source set (which won't ship
 * with your plugin and won't get looked at but you can use it during development) or
 * just remove it".
 *
 * <p>Two rules still apply here and are unchanged by the move: write only under the
 * caller-supplied directory — every caller passes a subdirectory of
 * {@code RuneLite.RUNELITE_DIR} — and never on the client thread, which
 * {@link LivelyCitiesDevReportsPlugin} guarantees by dispatching to a background thread
 * after the client-thread pass that produced the text has already finished.
 *
 * <p>It takes finished text rather than a report object so that the second caller
 * did not have to be a second writer. {@code CacheIdAudit} and {@link FrameTimings}
 * both produce a stable, sorted, diffable {@code String} on the client thread — both
 * still in {@code src/main}, because measuring and auditing are not file I/O — and hand
 * it here; adding a third is a file name and nothing else. It was called
 * {@code CacheAuditReportWriter} while it had one caller.
 */
final class ReportWriter
{
	/**
	 * The suffix a half-written report carries while it is being written. Named so a
	 * human who finds one after a crash can tell what it is, and so
	 * {@code ReportWriterTest} can assert none are left behind.
	 */
	static final String TEMP_SUFFIX = ".part";

	private ReportWriter()
	{
	}

	/**
	 * Writes {@code text} to {@code fileName}, replacing whatever was there.
	 *
	 * <p><b>Through a temporary file and a rename, rather than straight at the
	 * target.</b> {@code new FileOutputStream(file)} truncates in place, so the report
	 * spends the whole write as a partial file — and since {@link FrameTimings} arrived
	 * this method has two callers that can be in flight at once. The frame report is
	 * dispatched both from the 300-tick cadence in
	 * {@link LivelyCitiesDevReportsPlugin#onGameTick} and from that plugin's
	 * {@code shutDown}, each through {@code CompletableFuture.runAsync} on the common
	 * pool, so a client closed just as a periodic report begins puts two writers on one
	 * path with nothing between them. Truncating writers interleave into a file that is
	 * neither report. The cache audit ran once per session and never had this window;
	 * it inherits the fix anyway.
	 *
	 * <p>Writing somewhere else and renaming means the target is only ever replaced
	 * whole: a rename within one directory is atomic, so a concurrent writer or a
	 * concurrent reader sees the old report or the new one and never half of either.
	 * Serialising the two callers behind a lock would close the same window, but this
	 * also survives the process dying mid-write, which a lock does not.
	 *
	 * <p>The temporary file is created in {@code outputDir} rather than in the system
	 * temp directory, for two reasons: the rename has to stay inside one filesystem to
	 * be atomic at all, and {@code AGENTS.md}'s file-I/O rule says this plugin writes
	 * only under the caller-supplied directory.
	 *
	 * @param outputDir the plugin's own subdirectory, created if it does not
	 *                  exist yet
	 * @param fileName  the report's file name — see
	 *                  {@link LivelyCitiesDevReportsPlugin#CACHE_AUDIT_REPORT_FILE_NAME}
	 *                  and {@link LivelyCitiesDevReportsPlugin#FRAME_REPORT_FILE_NAME}.
	 *                  Both constants live with the writer rather than with the two
	 *                  classes that produce the text, because after the move
	 *                  {@code src/main} names no files at all.
	 * @param text      the finished report, already a snapshot
	 * @return the file written
	 */
	static File write(File outputDir, String fileName, String text) throws IOException
	{
		if (!outputDir.isDirectory() && !outputDir.mkdirs() && !outputDir.isDirectory())
		{
			throw new IOException("could not create " + outputDir);
		}

		File file = new File(outputDir, fileName);
		Path target = file.toPath();

		// A distinct name per call, so two writers in flight cannot scribble on each
		// other's draft either — the whole point would be lost if they shared one.
		Path draft = Files.createTempFile(outputDir.toPath(), fileName, TEMP_SUFFIX);
		try
		{
			try (Writer writer = new OutputStreamWriter(
				Files.newOutputStream(draft), StandardCharsets.UTF_8))
			{
				writer.write(text);
			}

			try
			{
				Files.move(draft, target,
					StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			}
			catch (AtomicMoveNotSupportedException e)
			{
				// Not every filesystem promises it, and a report nobody can write is a
				// worse outcome than a narrower window. A plain replace still beats
				// truncating in place: the exposure shrinks from the length of the write
				// to the length of the rename.
				Files.move(draft, target, StandardCopyOption.REPLACE_EXISTING);
			}
		}
		catch (IOException | RuntimeException e)
		{
			// A draft left behind would accumulate one file per failure in a directory
			// the user is expected to read.
			try
			{
				Files.deleteIfExists(draft);
			}
			catch (IOException cleanupFailed)
			{
				e.addSuppressed(cleanupFailed);
			}
			throw e;
		}

		return file;
	}
}
