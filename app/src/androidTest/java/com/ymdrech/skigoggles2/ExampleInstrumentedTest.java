package com.ymdrech.skigoggles2;

import android.content.Context;
import android.content.Intent;
import android.support.test.InstrumentationRegistry;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;

import com.google.android.gms.maps.model.LatLng;
import com.ymdrech.skigoggles2.location.dijkstra.Algorithm;
import com.ymdrech.skigoggles2.location.dijkstra.KmlLayerAlgorithm;
import com.ymdrech.skigoggles2.location.dijkstra.Vertex;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Instrumented test, which will execute on an Android device.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class ExampleInstrumentedTest {

    @Rule
    public ActivityTestRule<MapsActivity> activity = new ActivityTestRule<MapsActivity>(MapsActivity.class);

    @Test
    public void useAppContext() throws Exception {
        // Context of the app under test.
        Context appContext = InstrumentationRegistry.getTargetContext();

        Vertex sourceVertex = new Vertex(
                new LatLng(6.65590875258859, 45.3991888415065), null);
        Vertex targetVertex = new Vertex(
                new LatLng(6.66064966674864, 45.4071753215602), null);

        Algorithm algorithm = activity.getActivity().getKmlLayerAlgorithm();
        algorithm.execute(sourceVertex);
        List<Vertex> route = algorithm.getPath(targetVertex);

        assertEquals("com.ymdrech.skigoggles2", appContext.getPackageName());
    }
}
