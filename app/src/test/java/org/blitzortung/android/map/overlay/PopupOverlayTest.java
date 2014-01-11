package org.blitzortung.android.map.overlay;

import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.TextView;
import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapView;
import com.google.android.maps.OverlayItem;
import org.blitzortung.android.app.R;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.robolectric.RobolectricTestRunner;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(RobolectricTestRunner.class)
public class PopupOverlayTest {

    class PopupOverlayItemForTest extends OverlayItem
    {
        public PopupOverlayItemForTest(GeoPoint geoPoint, String s, String s1) {
            super(geoPoint, s, s1);
        }
    }

    class PopupOverlayForTest extends PopupOverlay<PopupOverlayItemForTest>
    {
        public PopupOverlayForTest(Drawable defaultMarker)
        {
            super(defaultMarker);
        }

        @Override
        protected PopupOverlayItemForTest createItem(int i) {
            return null;
        }

        @Override
        public int size() {
            return 0;
        }
    }

    private PopupOverlayForTest popupOverlay;

    @Before
    public void setUp()
    {
        popupOverlay = spy(new PopupOverlayForTest(mock(Drawable.class)));
    }

    @Test
    public void testSetGetActivity()
    {
        OwnMapActivity activity = mock(OwnMapActivity.class);
        popupOverlay.setActivity(activity);

        assertThat(popupOverlay.getActivity(), is(activity));
    }

    @Test
    public void testShowPopup()
    {
        OwnMapActivity activity = mock(OwnMapActivity.class);
        popupOverlay.setActivity(activity);

        OwnMapView mapView = mock(OwnMapView.class);
        when(activity.getMapView()).thenReturn(mapView);

        View popUp = mock(View.class);
        when(mapView.getPopup()).thenReturn(popUp);

        TextView statusText = mock(TextView.class);
        when(popUp.findViewById(R.id.popup_text)).thenReturn(statusText);

        GeoPoint location = mock(GeoPoint.class);

        popupOverlay.showPopup(location, "<title>");

        ArgumentCaptor<String> title = ArgumentCaptor.forClass(String.class);
        verify(statusText, times(1)).setText(title.capture());
        assertThat(title.getValue(), is("<title>"));

        verify(mapView, times(1)).addView(eq(popUp), any(MapView.LayoutParams.class));

        assertTrue(popupOverlay.popupShown);
    }

    @Test
    public void testClearPopup()
    {
        OwnMapActivity activity = mock(OwnMapActivity.class);
        popupOverlay.setActivity(activity);

        OwnMapView mapView = mock(OwnMapView.class);
        when(activity.getMapView()).thenReturn(mapView);

        View popUp = mock(View.class);
        when(mapView.getPopup()).thenReturn(popUp);

        assertFalse(popupOverlay.clearPopup());

        verify(mapView, times(1)).removeView(popUp);

        assertFalse(popupOverlay.popupShown);

        popupOverlay.popupShown = true;

        assertTrue(popupOverlay.clearPopup());

        assertFalse(popupOverlay.popupShown);
    }
}