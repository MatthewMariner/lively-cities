package com.matthewmariner.livelycities;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.File;
import java.io.IOException;
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
import static org.junit.Assert.assertTrue;

/**
 * {@code docs/CITY-TOP-UP-CHECK.md} against the records it describes.
 *
 * <h2>Why a document gets a test</h2>
 *
 * <p>That file is not prose about the plugin. It is the <b>work order</b> for the one
 * thing this dataset still needs and that no command can do: a human walking to 33
 * tiles and looking at what is standing on them. Its Ground Markers block is pasted
 * straight into the client, and its entries are what the walker compares against what
 * they see. A tile in it that is not the tile in the JSON sends somebody to the wrong
 * square and gets a figure ticked that nobody looked at.
 *
 * <p>An independent review on 2026-08-29 read it against the data and found five
 * defects, four of them numeric and one of them a whole missing line — "Hollis" was
 * the only talking figure in the pass whose remark the entry did not print. None of
 * them would have failed anything, because nothing was reading this file. This is the
 * thing that reads it.
 *
 * <h2>What is checked, and what deliberately is not</h2>
 *
 * <p>Everything a walker acts on: which figures exist, where each one stands, which
 * way it faces, what its box is, what its Examine line says, what it says out loud,
 * and every one of the 57 Ground Markers. Not checked: the prose. Judgements like
 * "the likeliest of the six to be inside a wall" are exactly the human reasoning the
 * file exists to record, and a test that tried to verify them would be inventing a
 * rule rather than enforcing one.
 *
 * <p><b>This is a check on the file's shape as well as its content</b>, and that is
 * intended. If a future edit reformats an entry so the patterns below stop matching,
 * this goes red rather than quietly checking 32 figures instead of 33 — every parse
 * here is followed by a count. A document whose format is a contract with a person
 * holding a mouse is allowed to have a format.
 */
public class CityTopUpCheckTest
{
	private static final String DOC = "docs/CITY-TOP-UP-CHECK.md";

	/** The uuid prefix the top-up's records carry; {@link AuthoredRecordsTest} owns it. */
	private static final String MARKER = "add1";

	private static final int FIGURES = 33;
	private static final int NEW_BOXES = 12;

	/** Ground Markers' own colour strings, as the block spells them. */
	private static final String YELLOW = "#FFFFFF00";
	private static final String CYAN = "#FF00FFFF";

	/**
	 * One checklist entry: everything from {@code - [ ] **Name** — } up to the next
	 * entry, the next heading, or the next rule.
	 */
	private static final Pattern ENTRY = Pattern.compile(
		"^- \\[ \\] \\*\\*(.+?)\\*\\* — (.*?)(?=^- \\[ \\]|^#|^---)",
		Pattern.MULTILINE | Pattern.DOTALL);

	private static final Pattern TILE = Pattern.compile("^`(\\d+), (\\d+), (\\d+)`");
	private static final Pattern FACING = Pattern.compile("facing (north|south|east|west)");
	private static final Pattern BOX = Pattern.compile(
		"\\*\\*wanders\\*\\* `(\\d+),(\\d+) \\.\\. (\\d+),(\\d+)`");
	private static final Pattern EXAMINE = Pattern.compile(
		"Examine: \"(.*?)\"", Pattern.DOTALL);

	/**
	 * The {@code Says:} line and any continuation of it.
	 *
	 * <p>A continuation is a following line indented to the entry's own six spaces and
	 * <i>not</i> starting a bolded note, which is how the wrapped three-remark entry
	 * ("Nadir") is read whole while the {@code **Box note:**} beneath it is not swept
	 * in. Entries with nothing to say carry no {@code Says:} line at all, and that is a
	 * claim as much as the text is — see
	 * {@link #everyFiguresRemarksArePrintedInItsEntry()}.
	 */
	private static final Pattern SAYS = Pattern.compile(
		"Says: ((?:[^\\n]*)(?:\\n {6}(?!\\*)[^\\n]*)*)");

	private static final Pattern QUOTED = Pattern.compile("\"(.*?)\"", Pattern.DOTALL);

	private static final Pattern MARKERS = Pattern.compile(
		"```json\\n(\\[.*?\\])\\n```", Pattern.DOTALL);

	@Test
	public void theChecklistNamesEveryAuthoredFigureExactlyOnce() throws IOException
	{
		TreeSet<String> listed = new TreeSet<>();
		for (Entry entry : entries())
		{
			assertTrue("the checklist names '" + entry.name + "' twice",
				listed.add(entry.name));
		}

		TreeSet<String> authored = new TreeSet<>();
		for (ShippedCitizens.Entry citizen : ShippedCitizens.all())
		{
			if (citizen.uuid.startsWith(MARKER))
			{
				authored.add(citizen.name);
			}
		}

		// Both directions in one comparison: a figure in the data with no entry is a
		// tile nobody is sent to, and an entry with no figure is a walk to an empty
		// square. Both have happened to this file — the pass shipped a "Brother Alfric"
		// entry that outlived the record for as long as nothing compared the two.
		assertEquals("the checklist and the authored records have to be the same set",
			authored, listed);
		assertEquals("and there have to be 33 of them", FIGURES, listed.size());
	}

	/**
	 * Tile, facing and wander box, entry by entry.
	 *
	 * <p>The facing words are checked because a walker uses them to decide whether
	 * what they are looking at is the figure the entry describes. The mapping is the
	 * game's own JAU convention — 0 south, 512 west, 1024 north, 1536 east — and it is
	 * spelled out here rather than derived, because a table that derived it from the
	 * same constant the record uses would agree with itself whatever either said.
	 */
	@Test
	public void everyFigureStandsWhereItsEntrySaysAndFacesTheWayItSays() throws IOException
	{
		List<String> wrong = new ArrayList<>();
		int checked = 0;
		int boxes = 0;

		for (Entry entry : entries())
		{
			EntityDefinition record = record(entry.name);
			checked++;

			Matcher tile = TILE.matcher(entry.body);
			if (!tile.find())
			{
				wrong.add(entry.name + " has no `x, y, plane` tile");
				continue;
			}

			int x = Integer.parseInt(tile.group(1));
			int y = Integer.parseInt(tile.group(2));
			int plane = Integer.parseInt(tile.group(3));
			if (record.getWorldLocation().getX() != x
				|| record.getWorldLocation().getY() != y
				|| record.getWorldLocation().getPlane() != plane)
			{
				wrong.add(entry.name + " is listed at " + x + "," + y + "," + plane
					+ " and stands at " + record.getWorldLocation());
			}

			Matcher facing = FACING.matcher(entry.body);
			if (!facing.find())
			{
				wrong.add(entry.name + " does not say which way it faces");
			}
			else if (!facing.group(1).equals(compass(record.getOrientation())))
			{
				wrong.add(entry.name + " is listed facing " + facing.group(1)
					+ " and is oriented " + record.getOrientation() + " ("
					+ compass(record.getOrientation()) + ")");
			}

			Matcher box = BOX.matcher(entry.body);
			EntityDefinition.WanderBox authored = record.getWanderBox();
			if (box.find())
			{
				boxes++;
				if (authored == null)
				{
					wrong.add(entry.name + " is listed as a wanderer and carries no box");
				}
				else if (authored.getMinX() != Integer.parseInt(box.group(1))
					|| authored.getMinY() != Integer.parseInt(box.group(2))
					|| authored.getMaxX() != Integer.parseInt(box.group(3))
					|| authored.getMaxY() != Integer.parseInt(box.group(4)))
				{
					wrong.add(entry.name + " is listed pacing " + box.group(1) + ","
						+ box.group(2) + " .. " + box.group(3) + "," + box.group(4)
						+ " and paces " + authored.getMinX() + "," + authored.getMinY()
						+ " .. " + authored.getMaxX() + "," + authored.getMaxY());
				}
			}
			else if (authored != null)
			{
				wrong.add(entry.name + " carries a wander box the entry does not mention, "
					+ "so its cyan corner markers are missing from the import block");
			}
		}

		assertTrue("checklist entries that disagree with the shipped record: " + wrong,
			wrong.isEmpty());
		assertEquals("every figure has to have been checked", FIGURES, checked);
		assertEquals("and the twelve new wander boxes have to be the twelve listed",
			NEW_BOXES, boxes);
	}

	@Test
	public void everyFiguresExamineTextIsQuotedExactly() throws IOException
	{
		List<String> wrong = new ArrayList<>();
		int checked = 0;

		for (Entry entry : entries())
		{
			Matcher examine = EXAMINE.matcher(entry.body);
			if (!examine.find())
			{
				wrong.add(entry.name + " has no Examine line");
				continue;
			}

			checked++;
			String quoted = unwrap(examine.group(1));
			String actual = record(entry.name).getExamineText();
			if (!quoted.equals(actual))
			{
				wrong.add(entry.name + " is quoted as \"" + quoted + "\" and reads \""
					+ actual + "\"");
			}
		}

		assertTrue("Examine lines that are not what the record says: " + wrong,
			wrong.isEmpty());
		assertEquals("every figure has to have been checked", FIGURES, checked);
	}

	/**
	 * The {@code Says:} lines, including the entries that must not have one.
	 *
	 * <p>The absence is the half that failed. "Hollis" carries
	 * {@code "remarks": ["They are not all mine."]} and his entry printed no
	 * {@code Says:} line — the only talking figure in the pass whose line was left
	 * out, and invisible precisely because a missing line looks like a silent figure.
	 * So this asserts both directions: every remark appears, in order, and an entry
	 * with a {@code Says:} line has a record with remarks to justify it.
	 */
	@Test
	public void everyFiguresRemarksArePrintedInItsEntry() throws IOException
	{
		List<String> wrong = new ArrayList<>();
		int withRemarks = 0;
		int checked = 0;

		for (Entry entry : entries())
		{
			checked++;
			List<String> printed = new ArrayList<>();
			Matcher says = SAYS.matcher(entry.body);
			if (says.find())
			{
				Matcher quoted = QUOTED.matcher(says.group(1));
				while (quoted.find())
				{
					printed.add(unwrap(quoted.group(1)));
				}
			}

			List<String> authored = Arrays.asList(record(entry.name).getRemarks());
			if (!authored.isEmpty())
			{
				withRemarks++;
			}

			if (!printed.equals(authored))
			{
				wrong.add(entry.name + " prints " + printed + " and says " + authored);
			}
		}

		assertTrue("entries whose Says: lines are not the record's remarks: " + wrong,
			wrong.isEmpty());
		assertEquals("every figure has to have been checked", FIGURES, checked);

		// A sample guard with teeth: if every one of the 33 were silent, the loop above
		// would compare 33 empty lists to 33 empty lists and prove nothing at all.
		assertEquals("figures in this pass that have something to say", 10, withRemarks);
	}

	/**
	 * The Ground Markers block — the part of this file that is executable.
	 *
	 * <p>It is pasted into the client and it is what puts a square on the ground. A
	 * marker whose coordinates have drifted from the record does not look wrong; it
	 * looks like a figure that failed to spawn, which is the single most confusing
	 * outcome this walk can produce.
	 *
	 * <p>Region coordinates rather than world ones, because that is the format Ground
	 * Markers imports: region id {@code ((x >> 6) << 8) | (y >> 6)}, and the offsets
	 * inside it are the low six bits of each.
	 */
	@Test
	public void everyGroundMarkerLandsOnTheTileItIsLabelledWith() throws IOException
	{
		Matcher block = MARKERS.matcher(read());
		assertTrue("the Ground Markers import block is gone, and it is the reason this "
			+ "file is usable at all", block.find());

		JsonArray markers = TestGson.injected().fromJson(block.group(1), JsonArray.class);

		TreeMap<String, int[]> figures = new TreeMap<>();
		TreeMap<String, int[]> corners = new TreeMap<>();
		for (JsonElement element : markers)
		{
			JsonObject marker = element.getAsJsonObject();
			String label = marker.get("label").getAsString();
			int[] at = new int[]{
				marker.get("regionId").getAsInt(),
				marker.get("regionX").getAsInt(),
				marker.get("regionY").getAsInt(),
				marker.get("z").getAsInt()};

			if (YELLOW.equals(marker.get("color").getAsString()))
			{
				assertEquals("two yellow markers labelled " + label, null,
					figures.put(label, at));
			}
			else
			{
				assertEquals("expected only yellow and cyan markers", CYAN,
					marker.get("color").getAsString());
				assertEquals("two cyan markers labelled " + label, null,
					corners.put(label, at));
			}
		}

		assertEquals("one yellow marker per figure", FIGURES, figures.size());
		assertEquals("two cyan corners per new wander box", NEW_BOXES * 2, corners.size());
		assertEquals("and 57 markers in total, which is what the block's caption promises",
			FIGURES + NEW_BOXES * 2, markers.size());

		List<String> wrong = new ArrayList<>();
		for (Entry entry : entries())
		{
			EntityDefinition record = record(entry.name);
			int[] marker = figures.get(entry.name);
			if (marker == null)
			{
				wrong.add(entry.name + " has no yellow marker");
			}
			else
			{
				expect(wrong, entry.name, marker,
					record.getWorldLocation().getX(),
					record.getWorldLocation().getY(),
					record.getWorldLocation().getPlane());
			}

			EntityDefinition.WanderBox box = record.getWanderBox();
			if (box == null)
			{
				continue;
			}

			int[] sw = corners.get(entry.name + " box SW");
			int[] ne = corners.get(entry.name + " box NE");
			if (sw == null || ne == null)
			{
				wrong.add(entry.name + " is a wanderer with no cyan box corners");
				continue;
			}

			int plane = record.getWorldLocation().getPlane();
			expect(wrong, entry.name + " box SW", sw, box.getMinX(), box.getMinY(), plane);
			expect(wrong, entry.name + " box NE", ne, box.getMaxX(), box.getMaxY(), plane);
		}

		assertTrue("markers that do not land on the tile they name: " + wrong,
			wrong.isEmpty());
	}

	private static void expect(List<String> wrong, String label, int[] marker,
		int x, int y, int plane)
	{
		int regionId = ((x >> 6) << 8) | (y >> 6);
		int regionX = x & 63;
		int regionY = y & 63;

		if (marker[0] != regionId || marker[1] != regionX || marker[2] != regionY
			|| marker[3] != plane)
		{
			wrong.add(label + " marks " + Arrays.toString(marker) + " and should mark ["
				+ regionId + ", " + regionX + ", " + regionY + ", " + plane + "] for tile "
				+ x + "," + y + "," + plane);
		}
	}

	/** @return the compass word this file uses for a JAU orientation */
	private static String compass(int orientation)
	{
		switch (orientation)
		{
			case 0:
				return "south";
			case 512:
				return "west";
			case 1024:
				return "north";
			case 1536:
				return "east";
			default:
				return "orientation " + orientation;
		}
	}

	/** @return quoted text with its line wrapping collapsed back to single spaces */
	private static String unwrap(String quoted)
	{
		return quoted.replaceAll("\\s+", " ").trim();
	}

	private static EntityDefinition record(String name) throws IOException
	{
		EntityDefinition found = byName().get(name);
		if (found == null)
		{
			throw new IOException("no shipped record named '" + name + "'");
		}
		return found;
	}

	private static TreeMap<String, EntityDefinition> byName()
	{
		RegionDataLoader loader = new RegionDataLoader(TestGson.injected());
		TreeMap<String, EntityDefinition> byName = new TreeMap<>();

		for (int regionId : ShippedRegions.ids())
		{
			RegionDefinition region = loader.loadRegion(regionId);
			if (region == null)
			{
				continue;
			}

			for (EntityDefinition entity : region.getEntities())
			{
				if (entity.getUuid().toString().startsWith(MARKER))
				{
					byName.put(entity.getName(), entity);
				}
			}
		}

		return byName;
	}

	private static List<Entry> entries() throws IOException
	{
		List<Entry> out = new ArrayList<>();
		Matcher matcher = ENTRY.matcher(read());
		while (matcher.find())
		{
			out.add(new Entry(matcher.group(1), matcher.group(2)));
		}

		assertEquals("the checklist parser has to find every entry — a pattern that "
			+ "matched nothing would pass every assertion in this file while reading no "
			+ "part of the document at all", FIGURES, out.size());
		return out;
	}

	private static String read() throws IOException
	{
		File file = new File(DOC);
		assertTrue(file.getAbsolutePath() + " is missing, and it is the walk this "
			+ "dataset is waiting on", file.isFile());
		return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
	}

	private static final class Entry
	{
		final String name;
		final String body;

		Entry(String name, String body)
		{
			this.name = name;
			this.body = body;
		}
	}
}
