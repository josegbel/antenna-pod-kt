package de.test.antennapod.ui;

import android.content.Context;
import android.content.Intent;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.espresso.intent.rule.IntentsTestRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.storage.database.FeedDatabaseWriter;
import de.danoeh.antennapod.ui.appstartintent.MainActivityStarter;
import de.test.antennapod.EspressoTestUtils;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.view.View;

import java.util.Collections;
import java.util.Date;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isRoot;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withParent;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static de.test.antennapod.EspressoTestUtils.waitForView;
import static de.test.antennapod.EspressoTestUtils.waitForViewGlobally;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.not;

@RunWith(AndroidJUnit4.class)
public class FeedItemlistFragmentTest {
    @Rule
    public IntentsTestRule<MainActivity> activityRule = new IntentsTestRule<>(MainActivity.class, false, false);

    @Test
    public void testMissingFeedShowsEmptyStateWithoutCrashing() throws InterruptedException {
        EspressoTestUtils.clearPreferences();
        EspressoTestUtils.clearDatabase();
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra(MainActivityStarter.EXTRA_FEED_ID, 42L);
        activityRule.launchActivity(intent);

        onView(isRoot()).perform(waitForView(withId(R.id.recyclerView), 2000));
        Thread.sleep(1000);

        onView(withId(R.id.recyclerView)).check(matches(allOf(isDisplayed(), hasChildCount(0))));
        onView(allOf(isDescendantOfA(withId(R.id.appBar)), withId(R.id.txtvTitle))).check(matches(withText("")));
        onView(allOf(withId(R.id.progressBar), withParent(withId(R.id.coordinatorLayout)))).check(matches(not(isDisplayed())));
    }

    @Test
    public void testExistingFeedLoadsItems() {
        EspressoTestUtils.clearPreferences();
        EspressoTestUtils.clearDatabase();
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        Feed feed = new Feed(0, "last modified", "@@Feed title@@", "link", "description", "payment link",
                "@author@", "language", "type", "feedIdentifier", "http://localhost/cover.png",
                "/sdcard/abc", "http://localhost/feed.xml", 0);
        FeedItem item = new FeedItem(0, "title", "identifier", "link", new Date(), FeedItem.UNPLAYED, feed);
        feed.setItems(Collections.singletonList(item));
        feed = FeedDatabaseWriter.updateFeed(context, feed, false);

        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra(MainActivityStarter.EXTRA_FEED_ID, feed.getId());
        activityRule.launchActivity(intent);

        waitForViewGlobally(allOf(isDescendantOfA(withId(R.id.appBar)), withText(feed.getTitle()), isDisplayed()), 2000);
        onView(withId(R.id.recyclerView)).check(matches(allOf(isDisplayed(), hasChildCountAtLeast(1))));
    }

    private static Matcher<View> hasChildCount(final int expected) {
        return new TypeSafeMatcher<View>() {
            @Override
            public void describeTo(Description description) {
                description.appendText("RecyclerView with child count " + expected);
            }

            @Override
            protected boolean matchesSafely(View view) {
                return view instanceof RecyclerView && ((RecyclerView) view).getChildCount() == expected;
            }
        };
    }

    private static Matcher<View> hasChildCountAtLeast(final int expected) {
        return new TypeSafeMatcher<View>() {
            @Override
            public void describeTo(Description description) {
                description.appendText("RecyclerView with child count >= " + expected);
            }

            @Override
            protected boolean matchesSafely(View view) {
                return view instanceof RecyclerView && ((RecyclerView) view).getChildCount() >= expected;
            }
        };
    }
}
