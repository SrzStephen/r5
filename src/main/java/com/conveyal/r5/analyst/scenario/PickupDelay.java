package com.conveyal.r5.analyst.scenario;

import com.conveyal.r5.profile.StreetMode;
import com.conveyal.r5.transit.TransportNetwork;
import com.conveyal.r5.util.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.conveyal.r5.profile.StreetMode.CAR;

/**
 * This Modification type configures the amount of time a passenger must wait to be picked up by a ride-hailing service.
 * This waiting time may vary spatially, and is specified with a set of polygons like the RoadCongestion modification.
 * See the documentation on that class for discussions on polygon priority. Eventually all the polygon priority and
 * indexing should be moved to a reusable class.
 *
 * Created by abyrd on 2019-03-28
 */
public class PickupDelay extends Modification {

    private static final Logger LOG = LoggerFactory.getLogger(PickupDelay.class);

    // Public Parameters deserialized from JSON

    /** The identifier of the polygon layer containing the speed data. */
    public String polygonLayer;

    /**
     * The name of the attribute (floating-point) within the polygon layer that contains the pick-up wait time
     * (in minutes). Negative waiting times mean the area is not served at all.
     */
    public String waitTimeAttribute = "wait";

    /** The name of the attribute (numeric) within the polygon layer that contains the polygon priority. */
    public String priorityAttribute = "priority";

    /**
     * The name of the attribute (text) within the polygon layer that contains the polygon names.
     * This is only used for logging.
     */
    public String nameAttribute = "name";

    /**
     * Name of the attribute (array) containing GTFS stop IDs. If any stop_id is specified for a polygon, service is
     * only allowed between the polygon and the stops (i.e. no direct trips). If no stop_ids are specified,
     * passengers boarding an on-demand service in a pick-up zone should be able to alight anywhere.
     *
     * TODO not yet implemented
     */
    public String linkedStopsAttribute = "linkedStops";

    /**
     * LegMode for which this set of pickup delays applies
     *
     * TODO not yet implemented
     */
    public StreetMode streetMode = CAR;

    /**
     * The default waiting time (floating point, in minutes) when no polygon is found. Negative numbers mean the area
     * is not served at all.
     */
    public double defaultWait = 0;

    // Internal (private) fields

    private IndexedPolygonCollection polygons;

    // Implementations of methods for the Modification interface

    @Override
    public boolean resolve (TransportNetwork network) {
        // Polygon will only be fetched from S3 once when the scenario is resolved, then after application the
        // resulting network is cached. Subsequent uses of this same modification should not re-trigger S3 fetches.
        try {
            polygons = new IndexedPolygonCollection(
                    polygonLayer,
                    waitTimeAttribute,
                    nameAttribute,
                    priorityAttribute,
                    defaultWait
            );
            polygons.loadFromS3GeoJson();
            // Collect any errors from the IndexedPolygonCollection construction, so they can be seen in the UI.
            errors.addAll(polygons.getErrors());
        } catch (Exception e) {
            // Record any unexpected errors to bubble up to the UI.
            errors.add(ExceptionUtils.asString(e));
        }
        return errors.size() > 0;
    }

    @Override
    public boolean apply (TransportNetwork network) {
        // network.streetLayer is already a protective copy made by method Scenario.applyToTransportNetwork.
        // The polygons have already been validated in the resolve method, we just need to record them in the network.
        if (network.streetLayer.waitTimePolygons != null) {
            errors.add("Multiple pickup delay modifications cannot be applied to a single network.");
        } else {
            network.streetLayer.waitTimePolygons = polygons;
        }
        return errors.size() > 0;
    }

    @Override
    public int getSortOrder () {
        // TODO Determine where this modification type should appear in the ordering
        return 97;
    }

    @Override
    public boolean affectsStreetLayer () {
        // This modification only affects the waiting time to use on-street transport, but changes nothing at all about
        // scheduled public transit.
        return true;
    }

    @Override
    public boolean affectsTransitLayer () {
        // This modification only affects the waiting time to use on-street transport, but changes nothing at all about
        // scheduled public transit.
        return false;
    }
}
