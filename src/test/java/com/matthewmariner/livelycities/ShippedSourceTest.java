package com.matthewmariner.livelycities;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.runelite.client.plugins.Plugin;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * What may and may not appear in {@code src/main} — the half of this repo that becomes
 * the jar the Plugin Hub builds and serves.
 *
 * <p><b>Why a source scan rather than a note in a doc.</b> The hub's automated reviewer
 * refuses to look at a plugin that does file I/O. riktenx, on
 * <a href="https://github.com/runelite/plugin-hub/pull/12366">plugin-hub#12366</a>:
 * "file i/o will make your plugin require manually review. if you can not use it your
 * plugin can be automatically reviewed." Removing the file writing once is easy;
 * keeping it removed across every future milestone is what needs a guard, and there is
 * nothing else in this build that would notice a {@code new File(...)} reappearing in a
 * shipped class. Everything about the arrangement — {@link ReportWriter} in the test
 * source set, {@link LivelyCitiesDevReportsPlugin} loaded only by two Gradle tasks —
 * is undone by one import.
 *
 * <p><b>What this test does not claim.</b> Only that the shipped source names no
 * filesystem API. The reviewer's rule set is private — riktenx again: "i cannot share
 * that code" — so whether the classpath {@code InputStream} below trips it cannot be
 * checked from outside, and nothing here should be read as a promise that a submission
 * passes automated review.
 */
public class ShippedSourceTest
{
	/**
	 * Every filesystem entry point, spelled the ways a Java file can spell them.
	 *
	 * <p>Deliberately narrower than "no {@code java.io}": {@code java.io} is also how
	 * you read a classpath resource, which is not filesystem access and which this
	 * plugin cannot ship without — see
	 * {@link #theClasspathResourceReaderIsDeliberatelyStillAllowed()}. Banning the whole
	 * package would either break the dataset or teach the next person to weaken this
	 * list, and a guard that has to be weakened to get work done stops being a guard.
	 */
	private static final List<String> FORBIDDEN = Collections.unmodifiableList(Arrays.asList(
		// Internal form, with slashes: this is how a class file spells a type. The
		// java/io/File prefix covers File, FileWriter, FileReader, FileInputStream,
		// FileOutputStream, FileDescriptor and FilePermission in one entry — in the
		// constant pool they really are all one prefix, which they were not in source.
		"java/io/File",
		"java/io/RandomAccessFile",
		"java/io/PrintWriter",
		"java/nio/file/",
		"java/nio/channels/FileChannel",
		"javax/swing/JFileChooser",
		// Both take a String or File and write to it. Neither names a "File" anything,
		// which is how new Formatter("x.txt") walked past the source-text version.
		"java/util/Formatter",
		"java/util/Scanner"));

	/**
	 * The one exemption, named rather than pattern-matched.
	 *
	 * <p>{@code RegionDataLoader} opens {@code RegionData/<id>.json} with
	 * {@code getClassLoader().getResourceAsStream(...)}. That reads out of the jar the
	 * plugin is already running from — no path, no directory, no write — and it is how
	 * every hub plugin that ships a dataset gets at it. It is not on the list above and
	 * does not need to be; this constant exists so the next test can prove the list is a
	 * precise ban rather than an accidentally empty one.
	 */
	private static final String CLASSPATH_READER = "RegionDataLoader.java";

	/**
	 * The compiled classes, not the source text — and that difference is the test.
	 *
	 * <p>A source scan is an approximation of the program and it leaks. Review found
	 * three ordinary ways past the list below: {@code import java.io.PrintWriter; new
	 * PrintWriter("x.txt")} names nothing on it; {@code import java.io.*} defeats
	 * {@code "java.io.File"}, which was only ever catching {@code FileWriter} and
	 * {@code FileOutputStream} by being a <i>prefix</i> of their import lines; and
	 * {@code new Formatter("x.txt")} is in {@code java.util}. Lengthening the list
	 * would just move the leak.
	 *
	 * <p>By the time javac is done, every one of those — wildcard import,
	 * fully-qualified name, static import, even
	 * {@code Class.forName("java.io." + "FileWriter")} — has collapsed into the same
	 * UTF-8 entry in the class file's constant pool. So the scan reads
	 * {@code build/classes/java/main} instead. {@code test} depends on {@code classes},
	 * so those files exist whenever this runs, and {@link #shippedClasses()} fails
	 * loudly rather than scanning nothing if that ever stops being true.
	 *
	 * <p>Searching the whole file rather than parsing the pool properly means a string
	 * literal spelling {@code java/io/File} would also trip this. That is the right
	 * bias: a shipped class containing that text wants a human to look at it either way.
	 */
	@Test
	public void noShippedClassCanReachAFilesystem() throws IOException
	{
		List<String> offences = new ArrayList<>();

		for (Path classFile : shippedClasses())
		{
			String constants = new String(
				Files.readAllBytes(classFile), StandardCharsets.ISO_8859_1);
			for (String forbidden : FORBIDDEN)
			{
				if (constants.contains(forbidden))
				{
					offences.add(classFile.getFileName() + " references " + forbidden);
				}
			}
		}

		assertEquals("src/main ships to the Plugin Hub, and file I/O there costs the "
				+ "submission its automated review. Whatever wanted a file belongs in "
				+ "src/test/java beside ReportWriter and LivelyCitiesDevReportsPlugin, "
				+ "which the two Gradle dev tasks load and the hub's jar does not contain: "
				+ offences,
			Collections.emptyList(), offences);
	}

	/**
	 * The guard on the guard: a known-bad class really is caught.
	 *
	 * <p>Without this, {@link #noShippedClassCanReachAFilesystem()} passes when
	 * {@link #FORBIDDEN} is empty, when the scan reads the wrong directory, and when
	 * {@link #shippedClasses()} returns nothing — three ways to be green while checking
	 * nothing at all. Review proved the first of them: emptying the list left every
	 * test in this file passing, in a file whose own javadoc claimed that could not
	 * happen.
	 *
	 * <p>The positive control is this test class itself. It is compiled into
	 * {@code build/classes/java/test}, it genuinely uses {@code java.nio.file}, and it
	 * is therefore exactly the artifact the scan must reject — so pointing the same
	 * matcher at it and requiring an offence proves the matcher works without needing a
	 * deliberately broken file checked into {@code src/main}.
	 */
	@Test
	public void theScanActuallyCatchesAClassThatTouchesTheFilesystem() throws IOException
	{
		Path knownBad = Paths.get("build/classes/java/test",
			"com/matthewmariner/livelycities/ShippedSourceTest.class");
		assertTrue("expected the compiled form of this very test at " + knownBad.toAbsolutePath()
				+ " — without it there is no positive control and the scan could be"
				+ " matching nothing", Files.isRegularFile(knownBad));

		String constants = new String(
			Files.readAllBytes(knownBad), StandardCharsets.ISO_8859_1);

		List<String> hits = new ArrayList<>();
		for (String forbidden : FORBIDDEN)
		{
			if (constants.contains(forbidden))
			{
				hits.add(forbidden);
			}
		}

		assertFalse("this class uses java.nio.file, so the scan has to flag it. Zero hits"
				+ " means FORBIDDEN is empty or the matcher is broken, and"
				+ " noShippedClassCanReachAFilesystem is passing for no reason",
			hits.isEmpty());
	}

	/**
	 * And the scan is a ban on filesystems, not on {@code java.io} — proven by the file
	 * that still needs it.
	 *
	 * <p>Without this, the test above would keep passing if somebody "fixed" a future
	 * violation by deleting the dataset reader, and it would keep passing if the ban
	 * list were quietly emptied. Here the exemption is exercised: this file really does
	 * import {@code java.io}, really is in {@code src/main}, and really does ship.
	 */
	@Test
	public void theClasspathResourceReaderIsDeliberatelyStillAllowed() throws IOException
	{
		Path reader = shippedSources().stream()
			.filter(path -> CLASSPATH_READER.equals(path.getFileName().toString()))
			.findFirst()
			.orElseThrow(() -> new AssertionError(
				"expected to find " + CLASSPATH_READER + " under src/main/java"));

		String text = new String(Files.readAllBytes(reader), StandardCharsets.UTF_8);

		assertTrue(CLASSPATH_READER + " is the reason the ban above is a list of "
				+ "filesystem entry points rather than a blanket 'no java.io' — if it "
				+ "stopped importing java.io, that reasoning would be stale",
			text.contains("import java.io.InputStream;"));
		assertTrue("and what it does with it has to still be a classpath read: a path, a "
				+ "directory or a stream opened on a File would be filesystem access "
				+ "wearing the exemption's coat",
			text.contains("getClassLoader().getResourceAsStream("));
	}

	/**
	 * The dev client loads the plugin that writes the reports.
	 *
	 * <p>The last link in the chain the two Gradle tasks start:
	 * {@code sourceSets.test.runtimeClasspath} puts the reporter on the classpath, the
	 * system property opens its gate, and this list is what actually hands it to
	 * RuneLite. Drop it here and {@code ./gradlew runWithTimings} launches a client that
	 * measures perfectly and writes nothing.
	 *
	 * <p><b>It asserts on {@code builtinPlugins()}, not on the list</b>, and the
	 * difference is the whole test. An earlier version read {@code BUILTIN_PLUGINS}
	 * directly, which meant {@code main} could stop using the list altogether —
	 * hardcoding {@code loadBuiltin(LivelyCitiesPlugin.class)} and leaving the list
	 * sitting there intact and unread — and this stayed green while the reporter was
	 * never loaded. That is precisely the failure described above, reachable without
	 * turning anything red. {@code builtinPlugins()} is the only path from the list to
	 * {@code loadBuiltin}, so asserting on it asserts on the hand-off.
	 */
	@Test
	public void theDevClientLoadsTheReporterThatWritesTheReports() throws IOException
	{
		List<Class<? extends Plugin>> loaded =
			Arrays.asList(LivelyCitiesPluginTest.builtinPlugins());

		assertTrue("LivelyCitiesPluginTest.main has to load the reporter alongside the "
				+ "plugin: " + loaded,
			loaded.contains(LivelyCitiesDevReportsPlugin.class));
		assertTrue("and the plugin itself, obviously",
			loaded.contains(LivelyCitiesPlugin.class));

		// And main() has to actually hand that list over. Asserting only on the list
		// leaves the real failure reachable: hardcode loadBuiltin(LivelyCitiesPlugin
		// .class), leave BUILTIN_PLUGINS sitting there correct and unread, and every
		// assertion above still passes while the reporter is never loaded. Nothing
		// short of running main() can observe the call, and main() starts a client —
		// so the source text is the evidence, the same way this class reads src/main
		// and FrameTimingsTest reads build.gradle.
		String source = new String(Files.readAllBytes(
			new File("src/test/java/com/matthewmariner/livelycities/LivelyCitiesPluginTest.java")
				.toPath()), StandardCharsets.UTF_8);

		assertTrue("LivelyCitiesPluginTest.main has to pass builtinPlugins() to"
				+ " loadBuiltin — a hardcoded argument list would leave BUILTIN_PLUGINS"
				+ " correct, unread, and load nothing",
			source.contains("loadBuiltin(builtinPlugins())"));
	}

	/**
	 * Every {@code .class} compiled from {@code src/main/java} — the jar's actual
	 * contents, nested and synthetic classes included.
	 *
	 * <p>Fails loudly rather than scanning an empty list: a scan of nothing passes, and
	 * "passed" is the answer this test must never give by accident.
	 */
	private static List<Path> shippedClasses() throws IOException
	{
		Path root = Paths.get("build/classes/java/main");
		assertTrue("expected compiled classes at " + root.toAbsolutePath()
				+ " — the test task depends on the main classes, so if these are missing"
				+ " the build layout changed and this test has to change with it",
			Files.isDirectory(root));

		try (Stream<Path> walk = Files.walk(root))
		{
			List<Path> classes = walk
				.filter(path -> path.getFileName().toString().endsWith(".class"))
				.sorted()
				.collect(Collectors.toList());

			assertTrue("no .class files under " + root.toAbsolutePath()
					+ " — a scan of nothing would pass", !classes.isEmpty());
			return classes;
		}
	}

	/**
	 * Every {@code .java} file under {@code src/main/java}.
	 *
	 * <p>Gradle runs tests with the project directory as the working directory — the
	 * same assumption {@code FrameTimingsTest} makes when it reads {@code build.gradle}
	 * — and this fails loudly rather than scanning an empty list if that ever changes.
	 */
	private static List<Path> shippedSources() throws IOException
	{
		File root = new File("src/main/java");
		assertTrue("expected to find " + root.getAbsolutePath()
				+ " — if the test working directory moved, this test needs to move with it",
			root.isDirectory());

		try (Stream<Path> paths = Files.walk(root.toPath()))
		{
			List<Path> sources = paths
				.filter(path -> path.getFileName().toString().endsWith(".java"))
				.sorted()
				.collect(Collectors.toList());

			assertTrue("a scan that found no source files proves nothing",
				sources.size() > 20);
			return sources;
		}
	}
}
