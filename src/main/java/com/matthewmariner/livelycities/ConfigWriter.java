package com.matthewmariner.livelycities;

import javax.annotation.Nullable;

/**
 * The one way this plugin writes its own settings.
 *
 * <p><b>Why an interface and not {@code ConfigManager} itself.</b>
 * {@code ConfigManager}'s only constructor is <i>private</i> — verified with
 * {@code javap -p} against the 1.12.36 client jar, where it takes eight
 * collaborators including a {@code ScheduledExecutorService}, an
 * {@code EventBus} and a {@code SessionManager}. It cannot be subclassed and
 * there is no mocking framework on this classpath, so anything that took one
 * directly would be a class no test could ever construct. One method behind an
 * interface makes the whole write path — hide a citizen, mute a citizen, clear
 * either list — testable against a map, and leaves exactly one line of
 * untestable glue (the {@code @Provides} method in
 * {@link LivelyCitiesPlugin}).
 *
 * <p><b>The group is not a parameter.</b> Every key this plugin writes lives in
 * {@link LivelyCitiesConfig#GROUP}; making the group an argument would only
 * create the possibility of writing into somebody else's.
 */
@FunctionalInterface
interface ConfigWriter
{
	/**
	 * @param key   a {@code keyName} from {@link LivelyCitiesConfig}
	 * @param value the new value, or {@code null} to remove the key entirely so
	 *              the {@code @ConfigItem} default applies again. Removing is
	 *              not the same as writing the default: a key left in the
	 *              profile shows up as a user override forever, and for the
	 *              two "clear this list" buttons the honest end state is "the
	 *              user has no setting here" rather than "the user has
	 *              explicitly chosen the empty list".
	 */
	void write(String key, @Nullable String value);
}
