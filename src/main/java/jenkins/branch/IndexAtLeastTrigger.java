/*
 * The MIT License
 *
 * Copyright (c) 2013, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package jenkins.branch;

import antlr.ANTLRException;
import hudson.Extension;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TopLevelItem;
import hudson.triggers.TimerTrigger;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import hudson.util.ListBoxModel;
import hudson.util.TimeUnit2;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * A {@link Trigger} for {@link MultiBranchProject}s that will only trigger an indexing if an indexing has not been
 * triggered within a specified timeframe.
 *
 * @author Stephen Connolly
 */
public class IndexAtLeastTrigger<P extends Job<P, R> & TopLevelItem,
        R extends Run<P, R>> extends Trigger<MultiBranchProject<P, R>> {

    /**
     * The interval between successive indexings.
     */
    private final long interval;

    /**
     * Constructor.
     *
     * @param interval the interval.
     * @throws ANTLRException if the crontab cannot be parsed.
     */
    @DataBoundConstructor
    public IndexAtLeastTrigger(String interval) throws ANTLRException {
        super(toCrontab(interval));
        long intervalMillis = toIntervalMillis(interval);
        this.interval = intervalMillis;
    }

    /**
     * Turns an interval into a suitable crontab.
     *
     * @param interval the interval.
     * @return the crontab.
     */
    private static String toCrontab(String interval) {
        long millis = toIntervalMillis(interval);
        if (millis < TimeUnit2.MINUTES.toMillis(5)) {
            return "* * * * *";
        }
        if (millis < TimeUnit2.MINUTES.toMillis(10)) {
            return "*/12 * * * *";
        }
        if (millis < TimeUnit2.MINUTES.toMillis(30)) {
            return "*/6 * * * *";
        }
        if (millis < TimeUnit2.HOURS.toMillis(1)) {
            return "*/2 * * * *";
        }
        if (millis < TimeUnit2.HOURS.toMillis(8)) {
            return "H * * * *";
        }
        return "H H * * *";
    }

    /**
     * Turns an interval into milliseconds./
     *
     * @param interval the interval.
     * @return the milliseconds.
     */
    private static long toIntervalMillis(String interval) {
        TimeUnit2 units = TimeUnit2.MINUTES;
        interval = interval.toLowerCase();
        if (interval.endsWith("h")) {
            units = TimeUnit2.HOURS;
            interval = StringUtils.removeEnd(interval, "h");
        }
        if (interval.endsWith("m")) {
            interval = StringUtils.removeEnd(interval, "m");
        } else if (interval.endsWith("d")) {
            units = TimeUnit2.DAYS;
            interval = StringUtils.removeEnd(interval, "d");
        } else if (interval.endsWith("ms")) {
            units = TimeUnit2.SECONDS;
            interval = StringUtils.removeEnd(interval, "ms");
        } else if (interval.endsWith("s")) {
            units = TimeUnit2.SECONDS;
            interval = StringUtils.removeEnd(interval, "s");
        }
        long value = 0;
        try {
            value = Long.parseLong(interval);
        } catch (NumberFormatException e) {
            value = 1;
        }
        return Math.min(TimeUnit2.DAYS.toMillis(30),
                Math.max(TimeUnit2.MINUTES.toMillis(1), units.toMillis(value)));
    }

    /**
     * Returns the number of milliseconds between indexing.
     *
     * @return the number of milliseconds between indexing.
     */
    @SuppressWarnings("unused") // used by Jelly EL
    public long getIntervalMillis() {
        return interval;
    }

    /**
     * Returns the interval between indexing.
     *
     * @return the interval between indexing.
     */
    @SuppressWarnings("unused") // used by Jelly EL
    public String getInterval() {
        if (interval < TimeUnit2.SECONDS.toMillis(1)) {
            return Long.toString(interval) + "ms";
        }
        if (interval < TimeUnit2.MINUTES.toMillis(1)) {
            return Long.toString(TimeUnit2.MILLISECONDS.toSeconds(interval)) + "s";
        }
        if (interval < TimeUnit2.HOURS.toMillis(1)) {
            return Long.toString(TimeUnit2.MILLISECONDS.toMinutes(interval)) + "m";
        }
        if (interval < TimeUnit2.DAYS.toMillis(1)) {
            return Long.toString(TimeUnit2.MILLISECONDS.toHours(interval)) + "m";
        }
        return Long.toString(TimeUnit2.MILLISECONDS.toDays(interval)) + "d";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run() {
        MultiBranchProject.BranchIndexing<P, R> indexing = job.getIndexing();
        if (indexing != null) {
            long delay = System.currentTimeMillis() - indexing.getTimeInMillis();
            if (delay < interval) {
                return;
            }
        }
        job.scheduleBuild(0, new TimerTrigger.TimerTriggerCause());
    }

    /**
     * Our {@link hudson.model.Descriptor}
     */
    @Extension
    @SuppressWarnings("unused") // instantiated by Jenkins
    public static class DescriptorImpl extends TriggerDescriptor {
        /**
         * {@inheritDoc}
         */
        public boolean isApplicable(Item item) {
            return item instanceof MultiBranchProject;
        }

        /**
         * {@inheritDoc}
         */
        public String getDisplayName() {
            return "On push notification with periodic fallback";
        }

        /**
         * Fills the interval list box.
         *
         * @return the interval list box.
         */
        @SuppressWarnings("unused") // used by Jelly
        public ListBoxModel doFillIntervalItems() {
            ListBoxModel model = new ListBoxModel();
            model.add("1 minute", "1m");
            model.add("2 minutes", "2m");
            model.add("5 minutes", "5m");
            model.add("10 minutes", "10m");
            model.add("15 minutes", "15m");
            model.add("20 minutes", "20m");
            model.add("25 minutes", "25m");
            model.add("30 minutes", "30m");
            model.add("1 hour", "1h");
            model.add("2 hours", "2h");
            model.add("4 hours", "4h");
            model.add("8 hours", "8h");
            model.add("12 hours", "12h");
            model.add("1 day", "1d");
            model.add("2 days", "2d");
            model.add("1 week", "7d");
            model.add("2 weeks", "14d");
            model.add("4 weeks", "28d");
            return model;
        }

    }


}
