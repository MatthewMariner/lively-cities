package com.matthewmariner.livelycities;

import java.util.Set;
import java.util.UUID;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * The two per-citizen opt-outs: <b>hide this one</b> and <b>shut this one up</b>.
 *
 * <p>Both are the same mechanism — a set of uuids in a {@code hidden = true}
 * config string, written by a RUNELITE menu entry, cleared by a visible
 * checkbox — so they are one class with two {@link UuidSetting}s rather than two
 * classes that would drift apart. Upstream issue #40 asked for the first; the
 * 144-upvote "please add an option to shut them up" thread is the second, at the
 * granularity people actually complained at: it was never "no citizen anywhere
 * should ever speak", it was "this one, outside the bank, every six seconds".
 *
 * <p><b>Why per-citizen and not just a global mute.</b> A global mute already
 * exists one notch up — {@link LivelyCitiesConfig#overheadText()} is the hard off
 * switch that removes overhead text entirely (issue #35). A second global switch
 * spelled "mute" would be the same setting twice, and a test could not tell them
 * apart, which is exactly the "fixture too uniform to distinguish outcomes" trap.
 * Two genuinely different granularities: one citizen, or the whole feature.
 *
 * <p><b>The uuid is the dataset's, and it is stable.</b> All 151 shipped records
 * carry one. {@link EntityDefinition} generates a random uuid for a record that
 * does not, and an override on such an entity therefore lasts only as long as the
 * session — the honest failure, and the reason the generator logs a warning.
 *
 * <p><b>Nothing here despawns or silences anything itself.</b> Writing the
 * setting posts a {@code ConfigChanged}, the plugin answers that by re-running the
 * visibility pass, and the pass has one rule — what is not wanted is despawned.
 * That is the same path the per-city checkboxes take, and reusing it is why
 * "Hide" needs no code of its own on the render side.
 */
@Slf4j
@Singleton
class CitizenOverrides
{
	/** {@code keyName} of the hidden string holding the hidden-citizen uuids. */
	static final String HIDDEN_KEY = "hiddenCitizens";

	/** {@code keyName} of the hidden string holding the muted-citizen uuids. */
	static final String MUTED_KEY = "mutedCitizens";

	/** {@code keyName} of the visible "Unhide all" checkbox. */
	static final String UNHIDE_ALL_KEY = "unhideAll";

	/** {@code keyName} of the visible "Unmute all" checkbox. */
	static final String UNMUTE_ALL_KEY = "unmuteAll";

	private final UuidSetting hidden;
	private final UuidSetting muted;

	@Inject
	CitizenOverrides(LivelyCitiesConfig config, ConfigWriter writer)
	{
		this.hidden = new UuidSetting(HIDDEN_KEY, config::hiddenCitizens, writer);
		this.muted = new UuidSetting(MUTED_KEY, config::mutedCitizens, writer);
	}

	/**
	 * @return the hidden uuids. Hoist this out of a per-entity loop — see
	 * {@link UuidSetting}.
	 */
	Set<UUID> hiddenUuids()
	{
		return hidden.current();
	}

	/**
	 * @return the muted uuids. Same advice as {@link #hiddenUuids()}.
	 */
	Set<UUID> mutedUuids()
	{
		return muted.current();
	}

	/**
	 * @return true if this call actually hid something new
	 */
	boolean hide(EntityDefinition definition)
	{
		boolean added = hidden.add(definition.getUuid());
		if (added)
		{
			log.debug("hiding {} ({}), {} citizen(s) now hidden",
				definition.label(), definition.getUuid(), hidden.size());
		}
		return added;
	}

	/**
	 * @return true if this call actually muted something new
	 */
	boolean mute(EntityDefinition definition)
	{
		boolean added = muted.add(definition.getUuid());
		if (added)
		{
			log.debug("muting {} ({}), {} citizen(s) now muted",
				definition.label(), definition.getUuid(), muted.size());
		}
		return added;
	}

	/**
	 * @return how many citizens were unhidden
	 */
	int unhideAll()
	{
		int count = hidden.size();
		if (hidden.clear())
		{
			log.info("Lively Cities: unhid {} citizen(s)", count);
			return count;
		}
		return 0;
	}

	/**
	 * @return how many citizens were unmuted
	 */
	int unmuteAll()
	{
		int count = muted.size();
		if (muted.clear())
		{
			log.info("Lively Cities: unmuted {} citizen(s)", count);
			return count;
		}
		return 0;
	}
}
