/* *********************************************************************** *
 * project: org.matsim.*
 * TravelTimeCalculator.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2009 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */
package org.matsim.core.trafficmonitoring;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.api.experimental.events.AgentArrivalEvent;
import org.matsim.core.api.experimental.events.AgentDepartureEvent;
import org.matsim.core.api.experimental.events.AgentStuckEvent;
import org.matsim.core.api.experimental.events.LinkEnterEvent;
import org.matsim.core.api.experimental.events.LinkLeaveEvent;
import org.matsim.core.api.experimental.events.handler.AgentArrivalEventHandler;
import org.matsim.core.api.experimental.events.handler.AgentDepartureEventHandler;
import org.matsim.core.api.experimental.events.handler.AgentStuckEventHandler;
import org.matsim.core.api.experimental.events.handler.LinkEnterEventHandler;
import org.matsim.core.api.experimental.events.handler.LinkLeaveEventHandler;
import org.matsim.core.events.TransitDriverStartsEvent;
import org.matsim.core.events.VehicleArrivesAtFacilityEvent;
import org.matsim.core.events.handler.TransitDriverStartsEventHandler;
import org.matsim.core.events.handler.VehicleArrivesAtFacilityEventHandler;
import org.matsim.core.router.util.LinkToLinkTravelTime;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.utils.collections.CollectionUtils;
import org.matsim.core.utils.collections.Tuple;

/**
 * Calculates actual travel times on link from events and optionally also the link-to-link 
 * travel times, e.g. if signaled nodes are used and thus turns in different directions
 * at a node may take a different amount of time.
 * <br>
 * Travel times on links are collected and averaged in bins/slots with a specified size
 * (<code>binSize</code>, in seconds, default 900 seconds = 15 minutes). The data for the travel times per link
 * is stored in {@link TravelTimeData}-objects. If a short binSize is used, it is useful to
 * use {@link TravelTimeDataHashMap} (see {@link #setTravelTimeDataFactory(TravelTimeDataFactory)}
 * as that one does not use any memory to time bins where no traffic occurred. By default,
 * {@link TravelTimeDataArray} is used.
 * 
 * 
 * @author dgrether
 * @author mrieser
 */
public class TravelTimeCalculator
		implements TravelTime, LinkToLinkTravelTime, LinkEnterEventHandler, LinkLeaveEventHandler, 
		AgentDepartureEventHandler, AgentArrivalEventHandler, VehicleArrivesAtFacilityEventHandler, TransitDriverStartsEventHandler, 
		AgentStuckEventHandler {
	
	private static final String ERROR_STUCK_AND_LINKTOLINK = "Using the stuck feature with turning move travel times is not available. As the next link of a stucked" +
	"agent is not known the turning move travel time cannot be calculated!";
	
	/*package*/ final int timeSlice;
	/*package*/ final int numSlots;
	private AbstractTravelTimeAggregator aggregator;
	
	private static final Logger log = Logger.getLogger(TravelTimeCalculator.class);
	
	private Map<Id, DataContainer> linkData;
	
	private Map<Tuple<Id, Id>, DataContainer> linkToLinkData;
	
	private final Map<Id, LinkEnterEvent> linkEnterEvents;

	private final Map<Id, Id> transitVehicleDriverMapping;
	
	private final Set<Id> agentsToFilter;
	private final Set<String> analyzedModes;
	
	private final boolean filterAnalyzedModes;
	
	private final boolean calculateLinkTravelTimes;

	private final boolean calculateLinkToLinkTravelTimes;
	
	private TravelTimeDataFactory ttDataFactory = null; 
	
	public TravelTimeCalculator(final Network network, TravelTimeCalculatorConfigGroup ttconfigGroup) {
		this(network, ttconfigGroup.getTraveltimeBinSize(), 30*3600, ttconfigGroup); // default: 30 hours at most
	}

	public TravelTimeCalculator(final Network network, final int timeslice, final int maxTime,
			TravelTimeCalculatorConfigGroup ttconfigGroup) {
		this.timeSlice = timeslice;
		this.numSlots = (maxTime / this.timeSlice) + 1;
		this.aggregator = new OptimisticTravelTimeAggregator(this.numSlots, this.timeSlice);
		this.ttDataFactory = new TravelTimeDataArrayFactory(network, this.numSlots);
		this.calculateLinkTravelTimes = ttconfigGroup.isCalculateLinkTravelTimes();
		this.calculateLinkToLinkTravelTimes = ttconfigGroup.isCalculateLinkToLinkTravelTimes();
		this.filterAnalyzedModes = ttconfigGroup.isFilterModes();
		if (this.calculateLinkTravelTimes){
			this.linkData = new ConcurrentHashMap<Id, DataContainer>((int) (network.getLinks().size() * 1.4));
		}
		if (this.calculateLinkToLinkTravelTimes){
			// assume that every link has 2 outgoing links as default
			this.linkToLinkData = new ConcurrentHashMap<Tuple<Id, Id>, DataContainer>((int) (network.getLinks().size() * 1.4 * 2));
		}
		this.linkEnterEvents = new ConcurrentHashMap<Id, LinkEnterEvent>();
		this.transitVehicleDriverMapping = new ConcurrentHashMap<Id, Id>();
		this.agentsToFilter = new HashSet<Id>();
		this.analyzedModes = CollectionUtils.stringToSet(ttconfigGroup.getAnalyzedModes());
		
		this.reset(0);
	}

	@Override
	public void handleEvent(final LinkEnterEvent e) {
		/* if only some modes are analyzed, we check whether the agent
		 * performs a trip with one of those modes. if not, we skip the event. */
		if (filterAnalyzedModes && agentsToFilter.contains(e.getPersonId())) return;
		
		LinkEnterEvent oldEvent = this.linkEnterEvents.remove(e.getPersonId());
		if ((oldEvent != null) && this.calculateLinkToLinkTravelTimes) {
			Tuple<Id, Id> fromToLink = new Tuple<Id, Id>(oldEvent.getLinkId(), e.getLinkId());
			DataContainer data = getLinkToLinkTravelTimeData(fromToLink, true);
			this.aggregator.addTravelTime(data.ttData, oldEvent.getTime(), e.getTime());
			data.needsConsolidation = true;
		}
		this.linkEnterEvents.put(e.getPersonId(), e);
	}

	@Override
	public void handleEvent(final LinkLeaveEvent e) {
		if (this.calculateLinkTravelTimes) {
			LinkEnterEvent oldEvent = this.linkEnterEvents.get(e.getPersonId());
	  		if (oldEvent != null) {
	  			DataContainer data = getTravelTimeData(e.getLinkId(), true);
	  			this.aggregator.addTravelTime(data.ttData, oldEvent.getTime(), e.getTime());
	  			data.needsConsolidation = true;
	  		}
		}
	}

	@Override
	public void handleEvent(AgentDepartureEvent event) {
		/* if filtering transport modes is enabled and the agents
		 * starts a leg on a non analyzed transport mode, add the agent
		 * to the filtered agents set. */
		if (filterAnalyzedModes && !analyzedModes.contains(event.getLegMode())) this.agentsToFilter.add(event.getPersonId());
	}
	
	@Override
	public void handleEvent(final AgentArrivalEvent event) {
		/* remove EnterEvents from list when an agent arrives.
		 * otherwise, the activity duration would counted as travel time, when the
		 * agent departs again and leaves the link! */
		this.linkEnterEvents.remove(event.getPersonId());
		
		// try to remove agent from set with filtered agents
		if (filterAnalyzedModes) this.agentsToFilter.remove(event.getPersonId());
	}

	@Override
	public void handleEvent(VehicleArrivesAtFacilityEvent event) {
		/* remove EnterEvents from list when a bus stops on a link.
		 * otherwise, the stop time would counted as travel time, when the
		 * bus departs again and leaves the link! */
		Id personId = transitVehicleDriverMapping.get(event.getVehicleId());
		if (personId != null) this.linkEnterEvents.remove(personId);
	}
	
	@Override
	public void handleEvent(TransitDriverStartsEvent event) {
		/* we create a mapping between transit vehicles and their drivers. this
		 * is needed to remove transit vehicles from the linkEnterEvents map if
		 * they stop on a link (similar to agents who perform an activity at a link. */
		transitVehicleDriverMapping.put(event.getVehicleId(), event.getDriverId());
	}
	
	@Override
	public void handleEvent(AgentStuckEvent event) {
		LinkEnterEvent e = this.linkEnterEvents.remove(event.getPersonId());
		if (e != null) {
			DataContainer data = getTravelTimeData(e.getLinkId(), true);
			data.needsConsolidation = true;
			this.aggregator.addStuckEventTravelTime(data.ttData, e.getTime(), event.getTime());
			if (this.calculateLinkToLinkTravelTimes){
				log.error(ERROR_STUCK_AND_LINKTOLINK);
				throw new IllegalStateException(ERROR_STUCK_AND_LINKTOLINK);
			}
		}
		
		// try to remove agent from set with filtered agents
		if (filterAnalyzedModes) this.agentsToFilter.remove(event.getPersonId());
	}
	
	private DataContainer getLinkToLinkTravelTimeData(Tuple<Id, Id> fromLinkToLink, final boolean createIfMissing) {
		DataContainer data = this.linkToLinkData.get(fromLinkToLink);
		if ((null == data) && createIfMissing) {
			data = new DataContainer(this.ttDataFactory.createTravelTimeData(fromLinkToLink.getFirst()));
			this.linkToLinkData.put(fromLinkToLink, data);
		}
		return data;
	}

	private DataContainer getTravelTimeData(final Id linkId, final boolean createIfMissing) {
		DataContainer data = this.linkData.get(linkId);
		if ((null == data) && createIfMissing) {
			data = new DataContainer(this.ttDataFactory.createTravelTimeData(linkId));
			this.linkData.put(linkId, data);
		}
		return data;
	}

	public double getLinkTravelTime(final Id linkId, final double time) {
    if (this.calculateLinkTravelTimes) {
      DataContainer data = getTravelTimeData(linkId, true);
      if (data.needsConsolidation) {
        consolidateData(data);
      }
      return this.aggregator.getTravelTime(data.ttData, time); 
    }
    throw new IllegalStateException("No link travel time is available " +
        "if calculation is switched off by config option!");
	}
	
	@Override
	public double getLinkTravelTime(final Link link, final double time) {
	  return this.getLinkTravelTime(link.getId(), time);
	}
	
	public double getLinkToLinkTravelTime(final Id fromLinkId, final Id toLinkId, double time){
    if (!this.calculateLinkToLinkTravelTimes) {
      throw new IllegalStateException("No link to link travel time is available " +
      "if calculation is switched off by config option!");      
    }
    DataContainer data = this.getLinkToLinkTravelTimeData(new Tuple<Id, Id>(fromLinkId, toLinkId), true);
    if (data.needsConsolidation) {
      consolidateData(data);
    }
    return this.aggregator.getTravelTime(data.ttData, time);
	}
	
	@Override
	public double getLinkToLinkTravelTime(Link fromLink, Link toLink, double time) {
	  return this.getLinkToLinkTravelTime(fromLink.getId(), toLink.getId(), time);
	}

	@Override
	public void reset(int iteration) {
		if (this.calculateLinkTravelTimes) {
			for (DataContainer data : this.linkData.values()){
				data.ttData.resetTravelTimes();
				data.needsConsolidation = false;
			}
		}
		if (this.calculateLinkToLinkTravelTimes){
			for (DataContainer data : this.linkToLinkData.values()){
				data.ttData.resetTravelTimes();
				data.needsConsolidation = false;
			}
		}
		this.linkEnterEvents.clear();
		this.transitVehicleDriverMapping.clear();
		this.agentsToFilter.clear();
	}
	
	public void setTravelTimeDataFactory(final TravelTimeDataFactory factory) {
		this.ttDataFactory = factory;
	}
	
	public void setTravelTimeAggregator(final AbstractTravelTimeAggregator aggregator) {
		this.aggregator = aggregator;
	}
	
	/**
	 * Makes sure that the travel times "make sense".
	 * <p/>
	 * Imagine short bin sizes (e.g. 5min), small links (e.g. 300 veh/hour)
	 * and small sample sizes (e.g. 2%). This would mean that effectively
	 * in the simulation only 6 vehicles can pass the link in one hour,
	 * one every 10min. So, the travel time in one time slot could be 
	 * >= 10min if two cars enter the link at the same time. If no car
	 * enters in the next time bin, the travel time in that time bin should
	 * still be >=5 minutes (10min - binSize), and not freespeedTraveltime,
	 * because actually every car entering the link in this bin will be behind
	 * the car entered before, which still needs >=5min until it can leave.
	 * <p/>
	 * This method ensures that the travel time in a time bin
	 * cannot be smaller than the travel time in the bin before minus the
	 * bin size.
	 * 
	 * @param data
	 */
	private void consolidateData(final DataContainer data) {
		synchronized(data) {
			if (data.needsConsolidation) {
				TravelTimeData r = data.ttData;

				// initialize prevTravelTime with ttime from time bin 0 and time 0.  (The interface comment already states that
				// having both as argument does not make sense.)
				double prevTravelTime = r.getTravelTime(0, 0.0);
				// changed (1, 0.0) to (0, 0.0) since Michal has convinced me (by a test) that using "1" is wrong
				// because you get the wrong result for time slot number 1.  This change does not affect the existing
				// unit tests.  kai, oct'11
				
				// go from time slot 1 forward in time:
				for (int i = 1; i < this.numSlots; i++) {
					
					// once more the getter is weird since it needs both the time slot and the time:
					double travelTime = r.getTravelTime(i, i * this.timeSlice);
					
					// if the travel time in the previous time slice was X, then now it is X-S, where S is the time slice:
					double minTravelTime = prevTravelTime - this.timeSlice;

					// if the travel time that has been measured so far is less than that minimum travel time, then do something:
					if (travelTime < minTravelTime) {

						// these TWO statements effectively set the travel time to the minTravelTime:
//						r.resetTravelTime(i) ; // removing that line again since it makes planomat scores _worse_.  Does
						// not make sense to me ...  Kai, oct'11
						r.addTravelTime(i, minTravelTime);

						prevTravelTime = minTravelTime;
						// (it seems a bit odd that this remembers "minTravelTime" and not getTravelTime(.,.), since they do
						// not need to be the same.  kai, oct'11)
					} else {
						prevTravelTime = travelTime;
					}
				}
				data.needsConsolidation = false;
			}
		}
	}

	public int getNumSlots() {
		return this.numSlots;
	}

	public int getTimeSlice(){
		return this.timeSlice;
	}
	
	private static class DataContainer {
		/*package*/ final TravelTimeData ttData;
		/*package*/ volatile boolean needsConsolidation = false;
		
		/*package*/ DataContainer(final TravelTimeData data) {
			this.ttData = data;
		}
	}

}
