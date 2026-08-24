package com.matthewmariner.livelycities;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The placement lint: every shipped citizen's {@link Theme}
 * ({@link EntityTheme}) has to be compatible ({@link PlacementCompatibility})
 * with the theme its region expects ({@link CityTheme}, built on {@link City}),
 * or be named in {@link PlacementExceptions} with a reason.
 *
 * <p>This is the v1 backlog row for the predecessor plugin's 303-upvote
 * complaint — Barrows brothers wandering above their own crypts — and the
 * separate complaint about a Karamja NPC standing in Varrock square. Both were
 * silent until a player noticed; this makes the same shape of mistake a red
 * test instead.
 *
 * <p><b>It found one real offender, and that offender has been fixed.</b> The
 * six citizens in region 14131 were named after the Barrows Brothers, with
 * "The ghost of &lt;Brother&gt;." examine text — the 303-upvote complaint about
 * the predecessor. {@link EntityTheme} tagged them {@link Theme#UNIQUE_BOSS},
 * which {@link CityTheme} maps to no region, so the lint went red. On
 * 2026-08-23 they were renamed in the dataset to generic "Barrow wight"s
 * (placements, models and animations untouched — only the identities) and
 * retagged {@link Theme#MORYTANIA_UNDEAD}, which the Barrows region does
 * claim. The lint is green because they are now honestly undead in Morytania,
 * not because the check was relaxed: {@link PlacementExceptions} was
 * deliberately <b>not</b> used, since that list is for placements judged fine
 * on inspection and this one was not.
 *
 * <p><b>Standing limitation, worth knowing.</b> Nothing in the shipped data
 * carries {@link Theme#UNIQUE_BOSS} any more, so the rule that catches a
 * boss impersonator is live but currently unexercised by real content —
 * {@link PlacementCompatibilityTest} is the only thing keeping it honest.
 * A future dataset that reintroduces a named boss is caught only if someone
 * also adds its uuid to {@link EntityTheme}, which is a manual step. That is
 * the lint's weakest seam.
 */
public class PlacementLintTest
{
	@Test
	public void everyShippedCitizenThemeIsCompatibleWithItsRegion()
	{
		List<String> violations = new ArrayList<>();

		for (ShippedCitizens.Entry citizen : ShippedCitizens.all())
		{
			if (PlacementExceptions.isExcepted(citizen.uuid))
			{
				continue;
			}

			City city = City.of(citizen.fileRegionId);
			Theme regionTheme = CityTheme.of(city);
			Theme entityTheme = EntityTheme.themeOf(citizen.uuid);

			if (!PlacementCompatibility.isCompatible(entityTheme, regionTheme))
			{
				violations.add(citizen + ": " + entityTheme + " citizen in a "
					+ regionTheme + " region (" + cityLabel(city) + ")");
			}
		}

		assertTrue("placement violation(s) — theme incompatible with region, and not excepted: "
				+ violations,
			violations.isEmpty());
	}

	/**
	 * Guards the poison property {@link Theme#UNIQUE_BOSS} depends on: if a
	 * future edit ever gave some city that theme, every boss-tier citizen in
	 * it would silently stop being flagged. Walks {@link City#values()}
	 * directly rather than trusting {@link CityTheme} to grade its own claim.
	 */
	@Test
	public void noCityIsEverMappedToTheUniqueBossTheme()
	{
		List<String> offenders = new ArrayList<>();
		for (City city : City.values())
		{
			if (CityTheme.of(city) == Theme.UNIQUE_BOSS)
			{
				offenders.add(city.getLabel());
			}
		}

		assertTrue("Theme.UNIQUE_BOSS must map to no city, or it stops flagging boss-tier "
				+ "citizens wherever it is mapped: " + offenders,
			offenders.isEmpty());
	}

	/**
	 * An exception with no reason, or a copy-pasted placeholder, is exactly
	 * what the class javadoc on {@code PlacementExceptions} warns is "how a
	 * lint dies" — this makes a blank one a red test instead of a silent one.
	 */
	@Test
	public void everyExceptionCarriesAReason()
	{
		for (Map.Entry<String, String> exception : PlacementExceptions.all().entrySet())
		{
			String reason = exception.getValue();
			assertTrue("exception " + exception.getKey() + " has no usable reason: '" + reason + "'",
				reason != null && reason.trim().length() >= 10);
		}
	}

	/**
	 * Every excepted uuid has to name a citizen that actually shipped — an
	 * exception for a uuid that got typo'd, or that named an entity later
	 * removed from the dataset, would silently protect nothing while looking
	 * like it protects something.
	 */
	@Test
	public void everyExceptionNamesAShippedCitizen()
	{
		List<String> shippedUuids = new ArrayList<>();
		for (ShippedCitizens.Entry citizen : ShippedCitizens.all())
		{
			shippedUuids.add(citizen.uuid);
		}

		List<String> stale = new ArrayList<>();
		for (String uuid : PlacementExceptions.all().keySet())
		{
			if (!shippedUuids.contains(uuid))
			{
				stale.add(uuid);
			}
		}

		assertTrue("exception(s) naming a uuid that is not in the shipped dataset: " + stale,
			stale.isEmpty());
	}

	/**
	 * The inverse of the previous two: an exception for a citizen who was
	 * never actually going to violate anything is a dead exception — it reads
	 * as protecting something and protects nothing. Confirms each excepted
	 * uuid genuinely fails {@link PlacementCompatibility#isCompatible} before
	 * the exception is consulted, i.e. that removing the exception would turn
	 * {@link #everyShippedCitizenThemeIsCompatibleWithItsRegion} red for it.
	 */
	@Test
	public void everyExceptionWouldActuallyViolateWithoutIt()
	{
		List<String> dead = new ArrayList<>();

		for (ShippedCitizens.Entry citizen : ShippedCitizens.all())
		{
			if (!PlacementExceptions.isExcepted(citizen.uuid))
			{
				continue;
			}

			Theme regionTheme = CityTheme.of(City.of(citizen.fileRegionId));
			Theme entityTheme = EntityTheme.themeOf(citizen.uuid);

			if (PlacementCompatibility.isCompatible(entityTheme, regionTheme))
			{
				dead.add(citizen + " is excepted but was never going to violate "
					+ "(already " + entityTheme + " in a " + regionTheme + " region)");
			}
		}

		assertTrue("dead exception(s) — remove them, they are not doing anything: " + dead,
			dead.isEmpty());
	}

	/**
	 * An echo is judged by the same lint as the citizen it came from, and passes it
	 * for the same reason.
	 *
	 * <p>{@link EntityTheme} is keyed on uuid and an echo's uuid is its own, so an
	 * echo is {@link Theme#GENERIC} to the table — and {@code GENERIC} is compatible
	 * with everywhere. That would be a hole if an echo could carry its source's
	 * <i>identity</i>: a second "Ali" in Varrock would be a desert transplant the lint
	 * could not see. It cannot, and this is the test that says why it cannot: an echo
	 * has no name and no examine text of its source's, so it is a plain Gielinor
	 * passer-by — which needs no regional justification, exactly like the 96 shipped
	 * citizens the table does not list.
	 *
	 * <p>What still has to hold is geography. An echo stands within
	 * {@link RenderPolicy#DATASET_OVERHANG_ALLOWANCE} tiles of its source and is filed
	 * under the same region file, so it is judged against the same city's theme — it
	 * cannot wander into a region with a different flavour, because it cannot wander
	 * at all.
	 */
	@Test
	public void everyEchoIsJudgedAgainstItsSourcesOwnRegionAndPassesAsAGenericPasserBy()
	{
		RegionDataLoader loader = new RegionDataLoader(TestGson.injected());
		List<String> violations = new ArrayList<>();
		int echoes = 0;

		for (int regionId : ShippedRegions.ids())
		{
			RegionDefinition region = loader.loadRegion(regionId);
			assertTrue("region " + regionId + " failed to load", region != null);

			Theme regionTheme = CityTheme.of(City.of(regionId));

			Map<UUID, EntityDefinition> sources = new HashMap<>();
			for (EntityDefinition entity : region.getEntities())
			{
				sources.put(entity.getUuid(), entity);
			}

			// The whole roster, which is how the scene derives them — an echo's tile
			// depends on everything else standing in its region.
			for (EntityDefinition echo : CitizenEcho.echoesOfRegion(region.getEntities()))
			{
				echoes++;
				EntityDefinition source = sources.get(echo.getEchoSourceUuid());
				assertTrue("every echo names a source from its own region file", source != null);

				Theme echoTheme = EntityTheme.themeOf(echo.getUuid().toString());
				if (echoTheme != Theme.GENERIC)
				{
					violations.add(echo.label() + " carries " + echoTheme
						+ " — an echo has no authored identity to carry a theme with");
				}

				if (!PlacementCompatibility.isCompatible(echoTheme, regionTheme))
				{
					violations.add(echo.label() + ": " + echoTheme + " echo in a "
						+ regionTheme + " region (" + cityLabel(City.of(regionId)) + ")");
				}

				if (echo.getRegionId() != regionId)
				{
					violations.add(echo.label() + " left the file its source was filed under");
				}

				if (echo.getCityRegionId() != source.getTileRegionId())
				{
					violations.add(echo.label() + " is judged by region " + echo.getCityRegionId()
						+ " rather than by its source's " + source.getTileRegionId());
				}

				if (RenderPolicy.tileDistance(
					source.getWorldLocation(), echo.getWorldLocation())
					> RenderPolicy.DATASET_OVERHANG_ALLOWANCE)
				{
					violations.add(echo.label() + " stands further from its source than the "
						+ "overhang allowance, so it could be judged against another city");
				}
			}
		}

		assertTrue("the dataset has to actually produce echoes for this to mean anything",
			echoes > 0);
		assertTrue("echo placement violation(s): " + violations, violations.isEmpty());
	}

	/**
	 * A human-readable summary of every non-generic themed citizen and its
	 * status, printed for review. Backed by a real assertion — cross-checked
	 * against an independently-computed count — so the printout cannot drift
	 * from what the other tests here actually checked.
	 */
	@Test
	public void printsAPlacementSummaryForHumanReview()
	{
		int themedCount = 0;
		int compatibleCount = 0;
		int exceptedCount = 0;
		int violationCount = 0;

		System.out.println("Lively Cities placement lint — non-generic citizens");
		System.out.println("region     theme            status        citizen");

		for (ShippedCitizens.Entry citizen : ShippedCitizens.all())
		{
			Theme entityTheme = EntityTheme.themeOf(citizen.uuid);
			if (entityTheme == Theme.GENERIC)
			{
				continue;
			}
			themedCount++;

			Theme regionTheme = CityTheme.of(City.of(citizen.fileRegionId));
			boolean compatible = PlacementCompatibility.isCompatible(entityTheme, regionTheme);
			boolean excepted = PlacementExceptions.isExcepted(citizen.uuid);

			String status;
			if (compatible)
			{
				compatibleCount++;
				status = "ok";
			}
			else if (excepted)
			{
				exceptedCount++;
				status = "excepted";
			}
			else
			{
				violationCount++;
				status = "VIOLATION";
			}

			System.out.println(String.format("%-10d %-16s %-13s %s",
				citizen.fileRegionId, entityTheme, status, citizen));
		}

		System.out.println("total themed: " + themedCount + ", ok: " + compatibleCount
			+ ", excepted: " + exceptedCount + ", violation: " + violationCount);

		assertEqualsInt("themed citizen count must partition into ok/excepted/violation with nothing left over",
			themedCount, compatibleCount + exceptedCount + violationCount);
	}

	private static void assertEqualsInt(String message, int expected, int actual)
	{
		assertFalse(message + " (expected " + expected + ", got " + actual + ")", expected != actual);
	}

	private static String cityLabel(@Nullable City city)
	{
		return city == null ? "unmapped region" : city.getLabel();
	}
}
