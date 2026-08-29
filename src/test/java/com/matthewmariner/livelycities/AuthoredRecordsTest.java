package com.matthewmariner.livelycities;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Which records in the vendored dataset are ours, what they are made of, and the fact
 * that {@code NOTICE} says both correctly.
 *
 * <h2>Why this is a test and not a paragraph</h2>
 *
 * <p>{@code src/main/resources/RegionData/} is somebody else's work, redistributed
 * under BSD-2. Adding records to it is a modification, and the licence's whole point
 * is that a reader can tell what was changed. {@code NOTICE} item 8 makes claims a
 * reader has no way to check for themselves: that exactly thirty-three records were
 * authored by this project in the 2026-08-29 top-up, that they are the ones whose
 * {@code uuid} begins {@value #MARKER}, and that every one of them wears a
 * {@code modelIds} array copied whole out of a record already in the dataset with
 * that record's palette re-dealt.
 *
 * <p>All of those rot the moment somebody adds a thirty-fourth citizen, and none of
 * them would fail anything. So this reads the table out of {@code NOTICE} itself and
 * checks it against the shipped files: the region ids, the counts, the names, and —
 * record by record — the kit. A disclosure that has drifted from the data is a red
 * build rather than a quiet inaccuracy in a licence file.
 *
 * <h2>Why the uuid and not a field</h2>
 *
 * <p>The cameos are marked with {@code "cameo": true}, but that flag means "opt-in
 * content behind a checkbox" — {@code EntityScene} and {@link CitizenEcho} both read
 * it — so putting it on an ordinary townsperson who is on by default would be a lie
 * with behaviour attached. A new field would change the wire format for the sake of
 * bookkeeping. The uuid is already required, already unique, and is the one thing
 * about a record that never changes ({@code EntityDefinition.stableHash}), so a
 * reserved prefix costs nothing and is greppable from outside the build.
 *
 * <h2>Both rosters, not just the citizen one</h2>
 *
 * <p>This class reads the region files itself rather than going through
 * {@link ShippedCitizens}, which is deliberately citizens-only (see its javadoc).
 * Every count here is a claim about <i>records this project authored</i>, and a
 * marked record parked in {@code sceneryRoster} would be invisible to a
 * citizens-only reader — including to the "exactly thirty-three" count that is the
 * headline of the disclosure. It is read from both rosters and the scenery half is
 * then asserted empty, so "there are none" is a measured fact rather than a
 * consequence of not having looked.
 *
 * <h2>Requires a fresh test run</h2>
 *
 * <p>{@code NOTICE} is declared an input of the {@code test} task in
 * {@code build.gradle} for this class's sake. Without that declaration, editing
 * {@code NOTICE} and running {@code ./gradlew test} reports nothing and exits 0 —
 * the task is up to date, because none of its inputs appeared to change, and the
 * disclosure check never runs. If that declaration is ever removed, this class is
 * only honest under {@code ./gradlew clean test} or {@code --rerun-tasks}.
 */
public class AuthoredRecordsTest
{
	/** The uuid prefix every record this project authored in the top-up carries. */
	private static final String MARKER = "add1";

	/** What {@code NOTICE} item 8 claims, and what the region files have to show. */
	private static final int AUTHORED = 33;

	/**
	 * The disclosure's own table, parsed back out of {@code NOTICE}.
	 *
	 * <p>Lines look like {@code 10290   2   Ardougne monastery      Anselm, Brother
	 * Edwy} — region id, count, a place label, then the names. The label is prose and
	 * is not checked; the other three are. Every row is one line, which is why the
	 * Draynor row runs long rather than wrapping: a continuation line is one more thing
	 * for this pattern to get subtly wrong, and getting it wrong here means silently
	 * checking twelve rows instead of thirteen.
	 */
	private static final Pattern ROW = Pattern.compile(
		"^ {7}(\\d{5}) {3}(\\d+) {3}\\S.*?(?: {2,})([A-Z][^\\n]*)$",
		Pattern.MULTILINE);

	@Test
	public void everyRecordThisProjectAuthoredIsIdentifiableFromTheDataAlone()
	{
		TreeMap<Integer, List<String>> ours = ourRecordsByRegion();

		int authored = 0;
		for (List<String> names : ours.values())
		{
			authored += names.size();
		}

		assertEquals("records carrying the '" + MARKER + "' uuid marker", AUTHORED, authored);
		assertEquals("region files they were added to", 13, ours.size());

		int upstream = 0;
		for (ShippedCitizens.Entry citizen : ShippedCitizens.all())
		{
			if (!citizen.uuid.startsWith(MARKER))
			{
				upstream++;
			}
		}

		// The other half of the claim, and the half that would rot silently: the
		// marker is only a marker if nothing else wears it. 109 is the roster as it
		// stood before the top-up, cameos included.
		assertEquals("citizens that predate the top-up and must not carry the marker",
			109, upstream);
		assertEquals("and the two halves are the whole roster",
			142, authored + upstream);
	}

	/**
	 * Nothing this project authored hides in {@code sceneryRoster}.
	 *
	 * <p>Not a hypothetical tidiness rule. Every other assertion in this class, and
	 * the {@code grep} {@code NOTICE} hands the reader, is about citizens; a marked
	 * record in the other roster would be authored content this project ships and
	 * discloses nowhere, and it would leave "exactly thirty-three" true of the
	 * citizen roster while being false of the dataset. It would also be silent
	 * behaviourally — scenery has no name, no examine text and no remarks, and
	 * {@link CitizenEcho} refuses to derive from it — so nothing else would notice.
	 */
	@Test
	public void everyAuthoredRecordIsACitizenAndNotAPieceOfScenery()
	{
		List<String> inTheWrongRoster = new ArrayList<>();
		int marked = 0;

		for (Record record : shippedRecords())
		{
			if (!record.isOurs())
			{
				continue;
			}

			marked++;
			if (!"citizenRoster".equals(record.rosterKey))
			{
				inTheWrongRoster.add(record.describe());
			}
		}

		assertTrue("records this project authored that ship in sceneryRoster: "
			+ inTheWrongRoster, inTheWrongRoster.isEmpty());

		// Counted over both rosters, so a record moved from one to the other changes
		// this number even though it changes neither roster's own total by itself.
		assertEquals("marked records across the whole dataset, both rosters",
			AUTHORED, marked);
	}

	/**
	 * An authored townsperson must not be flagged as a cameo.
	 *
	 * <p>{@code "cameo": true} is a behavioural flag — it gates a record behind the
	 * {@code cameos} checkbox and stops {@link CitizenEcho} deriving from it — and
	 * {@code NOTICE} tells a reader it is what identifies the six likenesses. Putting
	 * it on one of these thirty-three would both hide an ordinary citizen behind a
	 * checkbox nobody expects and make that sentence in the licence file wrong.
	 */
	@Test
	public void noAuthoredTownspersonIsFlaggedAsACameoOrDressedFromAnNpc()
	{
		List<String> offenders = new ArrayList<>();

		for (ShippedCitizens.Entry citizen : ShippedCitizens.all())
		{
			if (!citizen.uuid.startsWith(MARKER))
			{
				continue;
			}

			if (citizen.cameo)
			{
				offenders.add(citizen.name + " is flagged as a cameo");
			}

			// Not a rule about what is possible, but about what this pass did: every
			// one of the thirty-three wears raw modelIds copied from a shipped record,
			// which is what NOTICE item 8 claims and what keeps the distinct-model-id
			// figure at 324.
			if (citizen.npcAppearanceId != 0)
			{
				offenders.add(citizen.name + " is dressed from NPC "
					+ citizen.npcAppearanceId + " rather than from copied modelIds");
			}
		}

		assertTrue("authored townsfolk must be ordinary citizens: " + offenders,
			offenders.isEmpty());
	}

	/**
	 * <b>The rule the whole top-up rests on, enforced rather than trusted.</b>
	 *
	 * <p>{@code NOTICE} item 8 says it in one sentence: every one of the thirty-three
	 * reuses a {@code modelIds} array copied whole out of a record already in this
	 * dataset, with that record's own {@code modelRecolorFind} kept as-is and its
	 * {@code modelRecolorReplace} rotated so the copy is not simply its donor standing
	 * somewhere else. That is what keeps the distinct-model-id figure at 324 — the
	 * number that decides how much of this plugin a cache renumbering can break — and
	 * it is what stops the dataset acquiring twins.
	 *
	 * <p>Until this test it was held by nothing but the discipline of whoever wrote
	 * the records. A review on 2026-08-29 made three edits to prove it, and <b>all
	 * three survived the whole suite</b>:
	 *
	 * <ol>
	 *   <li>setting one new record's {@code modelRecolorReplace} to its donor's exact
	 *       array, making it a pixel-for-pixel twin of a shipped citizen;</li>
	 *   <li>reversing one new record's {@code modelIds} order — a hand-assembled kit
	 *       that appears nowhere in the dataset, with the distinct-id count unmoved
	 *       because reversing an array changes no member of it;</li>
	 *   <li>swapping one model id for another already in the dataset, producing a
	 *       combination that exists in no record and is therefore a body nobody has
	 *       ever seen rendered.</li>
	 * </ol>
	 *
	 * <p>The three assertions below are one per mutation, in that order.
	 *
	 * <p><b>Why "a rotation" and not "some other palette".</b> The re-deal is not a
	 * loose "make it look different" — it is {@link CitizenEcho#redeal}, the exact
	 * operation the {@code Crowded} derivation uses on the same arrays, and the
	 * rotation has to be one {@link CitizenEcho#distinctDeals} would offer, which is
	 * how a rotation that comes out as the identity (a palette of one repeated colour)
	 * is excluded. Stating the rule in the code's own vocabulary rather than
	 * re-implementing it means this test cannot drift from what the plugin does, and
	 * it means the authored records and the derived ones are provably the same kind of
	 * thing: an existing citizen's wardrobe, dealt round.
	 */
	@Test
	public void everyAuthoredKitIsAWholeCopyOfAShippedKitWithThePaletteReDealt()
	{
		List<Record> all = shippedRecords();

		List<String> noDonor = new ArrayList<>();
		List<String> notReDealt = new ArrayList<>();
		List<String> donorIsNotACitizen = new ArrayList<>();
		int checked = 0;

		for (Record ours : all)
		{
			if (!ours.isOurs())
			{
				continue;
			}

			checked++;

			List<Record> donors = new ArrayList<>();
			for (Record candidate : all)
			{
				if (candidate.isOurs())
				{
					// A record from this pass is not evidence that a kit predates it.
					continue;
				}

				if (Arrays.equals(candidate.modelIds, ours.modelIds)
					&& Arrays.equals(candidate.recolorFind, ours.recolorFind))
				{
					donors.add(candidate);
				}
			}

			if (donors.isEmpty())
			{
				noDonor.add(ours.describe() + " wears modelIds/modelRecolorFind that appear "
					+ "in no record predating the top-up: " + Arrays.toString(ours.modelIds));
				continue;
			}

			Record reDealtFrom = null;
			for (Record donor : donors)
			{
				if (isADistinctReDealOf(ours.recolorReplace, donor.recolorReplace))
				{
					reDealtFrom = donor;
					break;
				}
			}

			if (reDealtFrom == null)
			{
				List<String> tried = new ArrayList<>();
				for (Record donor : donors)
				{
					tried.add(donor.describe() + " "
						+ Arrays.toString(shorts(donor.recolorReplace)));
				}
				notReDealt.add(ours.describe() + " carries "
					+ Arrays.toString(shorts(ours.recolorReplace))
					+ ", which is not a distinct re-deal of any donor's palette: " + tried);
				continue;
			}

			// True of every donor this pass actually used, and worth pinning: scenery
			// carries no name, no examine text and no remarks, so a citizen wearing a
			// market stall's kit would be a body the dataset has no other example of.
			if (!"citizenRoster".equals(reDealtFrom.rosterKey))
			{
				donorIsNotACitizen.add(ours.describe() + " copies " + reDealtFrom.describe());
			}
		}

		assertTrue("authored kits that are not a whole copy of an existing kit — a "
			+ "hand-assembled modelIds array is a body nobody has seen rendered, and a "
			+ "new combination is new cache-breakage surface: " + noDonor, noDonor.isEmpty());
		assertTrue("authored kits whose palette is their donor's, not a re-deal of it — "
			+ "which is a pixel-for-pixel twin of a shipped citizen: " + notReDealt,
			notReDealt.isEmpty());
		assertTrue("authored kits copied from scenery: " + donorIsNotACitizen,
			donorIsNotACitizen.isEmpty());

		// The sample guard this file's siblings all carry: the three rules above are
		// inside the loop, so a scan that found no marked records would pass having
		// asked nothing at all.
		assertEquals("every authored record has to have been put through the rule",
			AUTHORED, checked);
	}

	/**
	 * The licence file's table has to be the dataset's table.
	 *
	 * <p>Region by region, count by count, name by name. This is the assertion the
	 * whole class exists for: {@code NOTICE} is the document a licensee reads, and
	 * nothing else in this build would notice it going stale.
	 *
	 * <p><b>A row's count is checked against more than its own name list.</b> It used
	 * to be checked against that alone, which left a hole one duplicate wide: a row
	 * reading {@code 10290   3   Ardougne monastery   Anselm, Brother Edwy, Brother
	 * Edwy} has a count of three and three names, so it passed, and the name
	 * comparison below is between sets, so the duplicate collapsed and matched the two
	 * records that actually ship. The disclosure would have overstated what this
	 * project added and nothing would have said so. Names within a row are now
	 * asserted distinct, and the row counts are asserted to sum to the thirty-three
	 * the prose above the table claims.
	 */
	@Test
	public void theNoticeTableMatchesWhatWasActuallyAdded() throws IOException
	{
		String notice = new String(
			Files.readAllBytes(new File("NOTICE").toPath()), StandardCharsets.UTF_8);

		assertTrue("NOTICE has to state the marker, or a reader cannot find these records",
			notice.contains("beginning \"" + MARKER + "\""));

		TreeMap<Integer, List<String>> declared = new TreeMap<>();
		int declaredTotal = 0;
		Matcher matcher = ROW.matcher(notice);
		while (matcher.find())
		{
			int region = Integer.parseInt(matcher.group(1));
			int count = Integer.parseInt(matcher.group(2));
			List<String> names = new ArrayList<>();
			for (String name : matcher.group(3).split(","))
			{
				String trimmed = name.trim().replaceAll("\\s+", " ");
				if (!trimmed.isEmpty())
				{
					names.add(trimmed);
				}
			}

			assertEquals("NOTICE row for region " + region + " names " + names.size()
				+ " citizen(s) but claims " + count, count, names.size());
			assertEquals("NOTICE row for region " + region + " lists a name twice, which "
					+ "makes its count larger than the number of records it names: " + names,
				names.size(), new TreeSet<>(names).size());
			assertFalse("region " + region + " is listed twice in NOTICE",
				declared.containsKey(region));
			declared.put(region, names);
			declaredTotal += count;
		}

		assertEquals("the parser has to find the whole table — a regex that matched "
				+ "nothing would pass while checking nothing", 13, declared.size());
		assertEquals("the row counts have to sum to the total NOTICE claims in prose",
			AUTHORED, declaredTotal);

		TreeMap<Integer, List<String>> actual = ourRecordsByRegion();
		assertEquals("the regions NOTICE lists have to be the regions that changed",
			actual.keySet(), declared.keySet());

		for (Integer region : actual.keySet())
		{
			assertEquals("citizens NOTICE lists for " + region,
				new TreeSet<>(declared.get(region)), new TreeSet<>(actual.get(region)));
		}
	}

	/**
	 * @return whether {@code candidate} is {@code donor}'s palette dealt round by one
	 * of the rotations {@link CitizenEcho#distinctDeals} would offer — which excludes
	 * the identity, and excludes a rotation that lands back on the palette it started
	 * from because every colour in it is the same
	 */
	private static boolean isADistinctReDealOf(int[] candidate, int[] donor)
	{
		if (candidate.length != donor.length || donor.length < 2)
		{
			return false;
		}

		short[] mine = shorts(candidate);
		short[] theirs = shorts(donor);
		for (int deal : CitizenEcho.distinctDeals(theirs))
		{
			if (Arrays.equals(CitizenEcho.redeal(theirs, deal), mine))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * The recolour values as the render path sees them.
	 *
	 * <p>{@code EntityDefinition} narrows these to {@code short} because that is what
	 * {@code ModelData#recolor} takes, and several shipped palettes hold values above
	 * {@code Short.MAX_VALUE}. The narrowing is one-to-one over the range the dataset
	 * uses (every value is below 65536), so comparing after it neither merges two
	 * distinct colours nor splits one — and it is the comparison that matches what
	 * actually renders.
	 */
	private static short[] shorts(int[] values)
	{
		short[] out = new short[values.length];
		for (int i = 0; i < values.length; i++)
		{
			out[i] = (short) values[i];
		}
		return out;
	}

	/**
	 * @return every marked record, grouped by the file it ships in, read straight
	 * from the JSON rather than from a validated {@link EntityDefinition} — the
	 * disclosure is about what is written in the files, not about what survived
	 * validation
	 */
	private static TreeMap<Integer, List<String>> ourRecordsByRegion()
	{
		TreeMap<Integer, List<String>> ours = new TreeMap<>();
		for (Record record : shippedRecords())
		{
			if (record.isOurs())
			{
				ours.computeIfAbsent(record.fileRegionId, k -> new ArrayList<>())
					.add(record.name);
			}
		}
		return ours;
	}

	/**
	 * Every record in every shipped region file, both rosters, exactly as authored.
	 *
	 * <p>Read here rather than through {@link ShippedCitizens} or
	 * {@link ShippedModelIds} because this class needs three things neither of them
	 * carries together: the uuid (which roster a record is in is decided by the
	 * marker), the raw {@code modelIds}, and both recolour arrays.
	 */
	private static List<Record> shippedRecords()
	{
		List<Record> out = new ArrayList<>();

		for (int regionId : ShippedRegions.ids())
		{
			String resource = RegionDataLoader.DEFAULT_RESOURCE_PREFIX + regionId + ".json";
			InputStream in = AuthoredRecordsTest.class.getClassLoader()
				.getResourceAsStream(resource);
			if (in == null)
			{
				throw new IllegalStateException("missing " + resource);
			}

			try (Reader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)))
			{
				JsonObject root = TestGson.injected().fromJson(reader, JsonObject.class);
				collect(root, "citizenRoster", regionId, out);
				collect(root, "sceneryRoster", regionId, out);
			}
			catch (IOException e)
			{
				throw new IllegalStateException("could not read " + resource, e);
			}
		}

		if (out.isEmpty())
		{
			throw new IllegalStateException("no records found in the shipped dataset");
		}

		return out;
	}

	private static void collect(JsonObject root, String rosterKey, int regionId, List<Record> into)
	{
		JsonElement roster = root.get(rosterKey);
		if (roster == null || !roster.isJsonArray())
		{
			return;
		}

		for (JsonElement element : roster.getAsJsonArray())
		{
			if (!element.isJsonObject())
			{
				continue;
			}

			JsonObject record = element.getAsJsonObject();
			into.add(new Record(
				regionId,
				rosterKey,
				string(record, "uuid"),
				string(record, "name"),
				ints(record, "modelIds"),
				ints(record, "modelRecolorFind"),
				ints(record, "modelRecolorReplace")));
		}
	}

	private static String string(JsonObject record, String key)
	{
		JsonElement value = record.get(key);
		return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
	}

	private static int[] ints(JsonObject record, String key)
	{
		JsonElement value = record.get(key);
		if (value == null || !value.isJsonArray())
		{
			return new int[0];
		}

		JsonArray array = value.getAsJsonArray();
		int[] out = new int[array.size()];
		for (int i = 0; i < array.size(); i++)
		{
			out[i] = array.get(i).getAsInt();
		}
		return out;
	}

	/** One roster record, from either roster, exactly as it is written in the file. */
	private static final class Record
	{
		final int fileRegionId;
		final String rosterKey;
		final String uuid;
		final String name;
		final int[] modelIds;
		final int[] recolorFind;
		final int[] recolorReplace;

		Record(
			int fileRegionId,
			String rosterKey,
			String uuid,
			String name,
			int[] modelIds,
			int[] recolorFind,
			int[] recolorReplace)
		{
			this.fileRegionId = fileRegionId;
			this.rosterKey = rosterKey;
			this.uuid = uuid;
			this.name = name;
			this.modelIds = modelIds;
			this.recolorFind = recolorFind;
			this.recolorReplace = recolorReplace;
		}

		boolean isOurs()
		{
			return uuid != null && uuid.startsWith(MARKER);
		}

		String describe()
		{
			return "'" + (name == null ? rosterKey : name) + "' in " + fileRegionId + ".json";
		}
	}
}
