/* *********************************************************************** *
 * project: org.matsim.*
 * AgentBeliefsImpl.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2007 by the members listed in the COPYING,        *
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

package org.matsim.withinday.beliefs;

import java.util.LinkedList;
import java.util.List;

import org.matsim.api.core.v01.network.Link;
import org.matsim.core.router.costcalculators.FreespeedTravelTimeCost;
import org.matsim.core.router.util.TravelCost;
import org.matsim.core.router.util.TravelTime;


/**
 * @author dgrether
 *
 */
public class AgentBeliefsImpl implements AgentBeliefs {

	private List<TravelTime> travelTimePerceptions;

	private List<TravelCost> travelCostPerceptions;

	private FreespeedTravelTimeCost freespeedTimeCost;


	public AgentBeliefsImpl() {
		this.travelTimePerceptions = new LinkedList<TravelTime>();
		this.freespeedTimeCost = new FreespeedTravelTimeCost();
	  this.travelCostPerceptions = new LinkedList<TravelCost>();
	}

	public double getLinkTravelTime(final Link link, final double time) {
		double ttime = 0.0;
		for (TravelTime tt : this.travelTimePerceptions) {
			ttime = tt.getLinkTravelTime(link, time);
			if (ttime > 0) {
				return ttime;
			}
		}
		return this.freespeedTimeCost.getLinkTravelTime(link, time);
	}


	public double getLinkTravelCost(final Link link, final double time) {
		double tcost = 0.0;
		for (TravelCost tc : this.travelCostPerceptions) {
			tcost = tc.getLinkTravelCost(link, time);
			if (tcost > 0) {
				return tcost;
			}
		}
		return this.freespeedTimeCost.getLinkTravelCost(link, time);
	}

	public void addTravelTimePerception(final TravelTime travelTimePerception) {
		this.travelTimePerceptions.add(travelTimePerception);
	}


}
