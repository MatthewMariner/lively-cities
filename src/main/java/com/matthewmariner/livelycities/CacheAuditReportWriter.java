package com.matthewmariner.livelycities;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

/**
 * Writes a {@link CacheIdAudit.Report} to disk.
 *
 * <p>The only piece of this durability tooling that touches a filesystem, kept
 * to one small class so it is the one place that has to obey
 * {@code AGENTS.md}'s file-I/O rule (write only under the caller-supplied
 * directory — {@code LivelyCitiesPlugin} passes a subdirectory of
 * {@code RuneLite.RUNELITE_DIR}) and the threading rule (never on the client
 * thread — {@code LivelyCitiesPlugin} calls this from a background thread, after
 * the client-thread pass that builds the report has already finished).
 */
final class CacheAuditReportWriter
{
	static final String FILE_NAME = "model-id-audit.txt";

	private CacheAuditReportWriter()
	{
	}

	/**
	 * @param outputDir the plugin's own subdirectory, created if it does not
	 *                  exist yet
	 * @return the file written
	 */
	static File write(File outputDir, CacheIdAudit.Report report) throws IOException
	{
		if (!outputDir.isDirectory() && !outputDir.mkdirs() && !outputDir.isDirectory())
		{
			throw new IOException("could not create " + outputDir);
		}

		File file = new File(outputDir, FILE_NAME);
		try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))
		{
			writer.write(report.toReportText());
		}

		return file;
	}
}
