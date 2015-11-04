/* *********************************************************************** *
 * project: org.matsim.*
 * BikeTravelTimeFactory.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2011 by the members listed in the COPYING,        *
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

package org.matsim.core.mobsim.qsim.multimodalsimengine.router.util;

import org.matsim.core.config.groups.PlansCalcRouteConfigGroup;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.router.util.TravelTimeFactory;

public class BikeTravelTimeFactory implements TravelTimeFactory {

	private final PlansCalcRouteConfigGroup plansCalcRouteConfigGroup;
	private final TravelTime walkTravelTimeFactory;
	
	public BikeTravelTimeFactory(PlansCalcRouteConfigGroup plansCalcRouteConfigGroup, TravelTime travelTime) {
		this.plansCalcRouteConfigGroup = plansCalcRouteConfigGroup;
		this.walkTravelTimeFactory = travelTime;
	}
	
	@Override
	public TravelTime createTravelTime() {
		return new BikeTravelTime(plansCalcRouteConfigGroup, walkTravelTimeFactory);
	}
	
}