package io.github.hidroh.materialistic;

import android.graphics.Rect;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.RecyclerView;
import android.widget.TextView;

import org.assertj.android.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.MockitoAnnotations;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.fakes.RoboMenuItem;
import org.robolectric.internal.ShadowExtractor;
import org.robolectric.shadows.ShadowToast;
import org.robolectric.util.ActivityController;

import javax.inject.Inject;
import javax.inject.Named;

import io.github.hidroh.materialistic.data.HackerNewsClient;
import io.github.hidroh.materialistic.data.Item;
import io.github.hidroh.materialistic.data.ItemManager;
import io.github.hidroh.materialistic.data.ResponseListener;
import io.github.hidroh.materialistic.data.TestHnItem;
import io.github.hidroh.materialistic.test.ListActivity;
import io.github.hidroh.materialistic.test.ShadowRecyclerView;
import io.github.hidroh.materialistic.test.ShadowRecyclerViewAdapter;
import io.github.hidroh.materialistic.test.ShadowSupportPreferenceManager;
import io.github.hidroh.materialistic.test.ShadowSwipeRefreshLayout;
import io.github.hidroh.materialistic.test.TestItem;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static org.assertj.android.api.Assertions.assertThat;
import static org.assertj.android.support.v4.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.robolectric.Shadows.shadowOf;

@Config(shadows = {ShadowSwipeRefreshLayout.class, ShadowRecyclerView.class, ShadowRecyclerViewAdapter.class, ShadowRecyclerViewAdapter.ShadowViewHolder.class, ShadowSupportPreferenceManager.class})
@RunWith(RobolectricGradleTestRunner.class)
public class ListFragmentTest {
    private ActivityController<ListActivity> controller;
    private ListActivity activity;
    @Inject @Named(ActivityModule.HN) ItemManager itemManager;
    @Captor ArgumentCaptor<ResponseListener<Item[]>> listener;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        TestApplication.applicationGraph.inject(this);
        reset(itemManager);
        controller = Robolectric.buildActivity(ListActivity.class)
                        .create().postCreate(null).start().resume().visible();
        activity = controller.get();
    }

    @Test
    public void testOnCreate() {
        Bundle args = new Bundle();
        args.putString(ListFragment.EXTRA_ITEM_MANAGER, HackerNewsClient.class.getName());
        args.putString(ListFragment.EXTRA_FILTER, ItemManager.TOP_FETCH_MODE);
        activity.getSupportFragmentManager()
                .beginTransaction()
                .add(android.R.id.list,
                        Fragment.instantiate(activity, ListFragment.class.getName(), args))
                .commit();
        assertThat((SwipeRefreshLayout) activity.findViewById(R.id.swipe_layout)).isRefreshing();
        controller.pause().stop().destroy();
    }

    @Test
    public void testRefresh() {
        Bundle args = new Bundle();
        args.putString(ListFragment.EXTRA_ITEM_MANAGER, HackerNewsClient.class.getName());
        args.putString(ListFragment.EXTRA_FILTER, ItemManager.TOP_FETCH_MODE);
        activity.getSupportFragmentManager()
                .beginTransaction()
                .add(android.R.id.list,
                        Fragment.instantiate(activity, ListFragment.class.getName(), args))
                .commit();
        ShadowSwipeRefreshLayout shadowSwipeRefreshLayout = (ShadowSwipeRefreshLayout)
                ShadowExtractor.extract(activity.findViewById(R.id.swipe_layout));
        shadowSwipeRefreshLayout.getOnRefreshListener().onRefresh();
        // should trigger another data request
        verify(itemManager).getStories(any(String.class),
                eq(ItemManager.MODE_DEFAULT),
                any(ResponseListener.class));
        verify(itemManager).getStories(any(String.class),
                eq(ItemManager.MODE_NETWORK),
                any(ResponseListener.class));
        controller.pause().stop().destroy();
    }

    @Test
    public void testHighlightNewItems() {
        Bundle args = new Bundle();
        args.putString(ListFragment.EXTRA_ITEM_MANAGER, HackerNewsClient.class.getName());
        args.putString(ListFragment.EXTRA_FILTER, ItemManager.TOP_FETCH_MODE);
        activity.getSupportFragmentManager()
                .beginTransaction()
                .add(android.R.id.list,
                        Fragment.instantiate(activity, ListFragment.class.getName(), args))
                .commit();
        verify(itemManager).getStories(anyString(),
                eq(ItemManager.MODE_DEFAULT),
                listener.capture());
        listener.getValue().onResponse(new Item[]{new TestItem() {
            @Override
            public String getId() {
                return "1";
            }
        }});
        reset(itemManager);
        ShadowSwipeRefreshLayout shadowSwipeRefreshLayout = (ShadowSwipeRefreshLayout)
                ShadowExtractor.extract(activity.findViewById(R.id.swipe_layout));
        shadowSwipeRefreshLayout.getOnRefreshListener().onRefresh();
        // should trigger another data request
        verify(itemManager).getStories(any(String.class),
                eq(ItemManager.MODE_NETWORK),
                listener.capture());
        listener.getValue().onResponse(new Item[]{
                new TestHnItem(1L),
                new TestHnItem(2L)
        });
        assertEquals(2, ((RecyclerView) activity.findViewById(R.id.recycler_view)).getAdapter().getItemCount());
        Assertions.assertThat((TextView) activity.findViewById(R.id.snackbar_text))
                .isNotNull()
                .containsText(activity.getResources().getQuantityString(R.plurals.new_stories_count, 1, 1));
        activity.findViewById(R.id.snackbar_action).performClick();
        assertEquals(1, ((RecyclerView) activity.findViewById(R.id.recycler_view)).getAdapter().getItemCount());
        Assertions.assertThat((TextView) activity.findViewById(R.id.snackbar_text))
                .isNotNull()
                .containsText(activity.getResources().getQuantityString(R.plurals.showing_new_stories, 1, 1));
        activity.findViewById(R.id.snackbar_action).performClick();
        assertEquals(2, ((RecyclerView) activity.findViewById(R.id.recycler_view)).getAdapter().getItemCount());
        controller.pause().stop().destroy();
    }

    @Test
    public void testConfigurationChanged() {
        Bundle args = new Bundle();
        args.putString(ListFragment.EXTRA_ITEM_MANAGER, HackerNewsClient.class.getName());
        args.putString(ListFragment.EXTRA_FILTER, ItemManager.TOP_FETCH_MODE);
        Fragment fragment = Fragment.instantiate(activity, ListFragment.class.getName(), args);
        activity.getSupportFragmentManager()
                .beginTransaction()
                .add(android.R.id.list, fragment)
                .commit();
        verify(itemManager).getStories(anyString(),
                eq(ItemManager.MODE_DEFAULT),
                listener.capture());
        listener.getValue().onResponse(new Item[]{new TestItem() {
        }});
        reset(itemManager);
        Bundle state = new Bundle();
        fragment.onSaveInstanceState(state);
        fragment.onActivityCreated(state);
        // should not trigger another data request
        verify(itemManager, never()).getStories(any(String.class),
                eq(ItemManager.MODE_DEFAULT),
                any(ResponseListener.class));
        controller.pause().stop().destroy();
    }

    @Test
    public void testEmptyResponse() {
        Bundle args = new Bundle();
        args.putString(ListFragment.EXTRA_ITEM_MANAGER, HackerNewsClient.class.getName());
        args.putString(ListFragment.EXTRA_FILTER, ItemManager.TOP_FETCH_MODE);
        activity.getSupportFragmentManager()
                .beginTransaction()
                .add(android.R.id.list,
                        Fragment.instantiate(activity, ListFragment.class.getName(), args))
                .commit();
        verify(itemManager).getStories(anyString(),
                eq(ItemManager.MODE_DEFAULT),
                listener.capture());
        listener.getValue().onResponse(new Item[0]);
        assertThat((SwipeRefreshLayout) activity.findViewById(R.id.swipe_layout)).isNotRefreshing();
        Assertions.assertThat(activity.findViewById(R.id.empty)).isNotVisible();
        controller.pause().stop().destroy();
    }

    @Test
    public void testErrorResponse() {
        Bundle args = new Bundle();
        args.putString(ListFragment.EXTRA_ITEM_MANAGER, HackerNewsClient.class.getName());
        args.putString(ListFragment.EXTRA_FILTER, ItemManager.TOP_FETCH_MODE);
        activity.getSupportFragmentManager()
                .beginTransaction()
                .add(android.R.id.list,
                        Fragment.instantiate(activity, ListFragment.class.getName(), args))
                .commit();
        verify(itemManager).getStories(anyString(),
                eq(ItemManager.MODE_DEFAULT),
                listener.capture());
        listener.getValue().onError(null);
        assertThat((SwipeRefreshLayout) activity.findViewById(R.id.swipe_layout)).isNotRefreshing();
        Assertions.assertThat(activity.findViewById(R.id.empty)).isVisible();
        controller.pause().stop().destroy();
    }

    @Test
    public void testRefreshError() {
        Bundle args = new Bundle();
        args.putString(ListFragment.EXTRA_ITEM_MANAGER, HackerNewsClient.class.getName());
        args.putString(ListFragment.EXTRA_FILTER, ItemManager.TOP_FETCH_MODE);
        activity.getSupportFragmentManager()
                .beginTransaction()
                .add(android.R.id.list,
                        Fragment.instantiate(activity, ListFragment.class.getName(), args))
                .commit();
        verify(itemManager).getStories(anyString(),
                eq(ItemManager.MODE_DEFAULT),
                listener.capture());
        listener.getValue().onResponse(new Item[]{new TestItem() {}});
        Assertions.assertThat(activity.findViewById(R.id.empty)).isNotVisible();
        reset(itemManager);
        ShadowSwipeRefreshLayout shadowSwipeRefreshLayout = (ShadowSwipeRefreshLayout)
                ShadowExtractor.extract(activity.findViewById(R.id.swipe_layout));
        shadowSwipeRefreshLayout.getOnRefreshListener().onRefresh();
        verify(itemManager).getStories(anyString(),
                eq(ItemManager.MODE_NETWORK),
                listener.capture());
        listener.getValue().onError(null);
        Assertions.assertThat(activity.findViewById(R.id.empty)).isNotVisible();
        assertNotNull(ShadowToast.getLatestToast());
        controller.pause().stop().destroy();
    }

    @Test
    public void testErrorWhenDetached() {
        Bundle args = new Bundle();
        args.putString(ListFragment.EXTRA_ITEM_MANAGER, HackerNewsClient.class.getName());
        args.putString(ListFragment.EXTRA_FILTER, ItemManager.TOP_FETCH_MODE);
        activity.getSupportFragmentManager()
                .beginTransaction()
                .add(android.R.id.list,
                        Fragment.instantiate(activity, ListFragment.class.getName(), args))
                .commit();
        verify(itemManager).getStories(anyString(),
                eq(ItemManager.MODE_DEFAULT),
                listener.capture());
        controller.pause().stop().destroy();
        listener.getValue().onError(null);
        // no exception
    }

    @Test
    public void testResponseWhenDetached() {
        Bundle args = new Bundle();
        args.putString(ListFragment.EXTRA_ITEM_MANAGER, HackerNewsClient.class.getName());
        args.putString(ListFragment.EXTRA_FILTER, ItemManager.TOP_FETCH_MODE);
        activity.getSupportFragmentManager()
                .beginTransaction()
                .add(android.R.id.list,
                        Fragment.instantiate(activity, ListFragment.class.getName(), args))
                .commit();
        verify(itemManager).getStories(anyString(),
                eq(ItemManager.MODE_DEFAULT),
                listener.capture());
        controller.pause().stop().destroy();
        listener.getValue().onResponse(null);
        // no exception
    }

    @Test
    public void testLayoutToggle() {
        Bundle args = new Bundle();
        args.putString(ListFragment.EXTRA_ITEM_MANAGER, HackerNewsClient.class.getName());
        args.putString(ListFragment.EXTRA_FILTER, ItemManager.TOP_FETCH_MODE);
        activity.getSupportFragmentManager()
                .beginTransaction()
                .add(android.R.id.list,
                        Fragment.instantiate(activity, ListFragment.class.getName(), args))
                .commit();
        Rect rect = new Rect();
        assertCompactView(rect);
        ShadowSupportPreferenceManager.getDefaultSharedPreferences(activity)
                .edit()
                .putBoolean(activity.getString(R.string.pref_list_item_view), true)
                .apply();
        assertCardView(rect);
        ShadowSupportPreferenceManager.getDefaultSharedPreferences(activity)
                .edit()
                .putBoolean(activity.getString(R.string.pref_list_item_view), false)
                .apply();
        assertCompactView(rect);
        controller.pause().stop().destroy();
    }

    @Test
    public void testTogglePreferenceChange() {
        Bundle args = new Bundle();
        args.putString(ListFragment.EXTRA_ITEM_MANAGER, HackerNewsClient.class.getName());
        args.putString(ListFragment.EXTRA_FILTER, ItemManager.TOP_FETCH_MODE);
        activity.getSupportFragmentManager()
                .beginTransaction()
                .add(android.R.id.list,
                        Fragment.instantiate(activity, ListFragment.class.getName(), args),
                        ListFragment.class.getName())
                .commit();
        Rect rect = new Rect();
        assertCompactView(rect);
        controller.pause();
        ShadowSupportPreferenceManager.getDefaultSharedPreferences(activity)
                .edit()
                .putBoolean(activity.getString(R.string.pref_list_item_view), true)
                .apply();
        controller.resume().postResume();
        activity.getSupportFragmentManager().findFragmentByTag(ListFragment.class.getName())
                .onPrepareOptionsMenu(shadowOf(activity).getOptionsMenu());
        assertCardView(rect);
        controller.pause().stop().destroy();
    }

    @Test
    public void testListMenu() {
        Bundle args = new Bundle();
        args.putString(ListFragment.EXTRA_ITEM_MANAGER, HackerNewsClient.class.getName());
        args.putString(ListFragment.EXTRA_FILTER, ItemManager.TOP_FETCH_MODE);
        activity.getSupportFragmentManager()
                .beginTransaction()
                .add(android.R.id.list,
                        Fragment.instantiate(activity, ListFragment.class.getName(), args),
                        ListFragment.class.getName())
                .commit();
        activity.getSupportFragmentManager().findFragmentByTag(ListFragment.class.getName())
                .onOptionsItemSelected(new RoboMenuItem(R.id.menu_list));
        assertThat(activity.getSupportFragmentManager())
                .hasFragmentWithTag(PopupSettingsFragment.class.getName());
    }

    private void assertCardView(Rect rect) {
        RecyclerView recyclerView = (RecyclerView) activity.findViewById(R.id.recycler_view);
        ShadowRecyclerView shadowRecyclerView = (ShadowRecyclerView) ShadowExtractor
                .extract(recyclerView);
        shadowRecyclerView.getItemDecorations().get(0)
                .getItemOffsets(rect, null, recyclerView, null);
        int horizontalMargin = activity.getResources()
                .getDimensionPixelSize(R.dimen.cardview_horizontal_margin);
        int verticalMargin = activity.getResources()
                .getDimensionPixelSize(R.dimen.cardview_vertical_margin);
        assertThat(rect).hasLeft(horizontalMargin)
                .hasRight(horizontalMargin)
                .hasTop(verticalMargin);
    }

    private void assertCompactView(Rect rect) {
        RecyclerView recyclerView = (RecyclerView) activity.findViewById(R.id.recycler_view);
        ShadowRecyclerView shadowRecyclerView = (ShadowRecyclerView) ShadowExtractor
                .extract(recyclerView);
        shadowRecyclerView.getItemDecorations().get(0)
                .getItemOffsets(rect, null, recyclerView, null);
        int divider = activity.getResources().getDimensionPixelSize(R.dimen.divider);
        assertThat(rect).hasTop(0)
                .hasLeft(0)
                .hasRight(0)
                .hasBottom(divider);
    }
}
