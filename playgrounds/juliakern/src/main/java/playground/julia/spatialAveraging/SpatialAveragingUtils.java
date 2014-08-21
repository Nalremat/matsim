/* *********************************************************************** *
 * project: org.matsim.*
 * SpatialAveragingUtils.java
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
package playground.julia.spatialAveraging;

import java.util.Collection;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.core.utils.geometry.CoordImpl;
import org.matsim.core.utils.gis.ShapeFileReader;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import com.vividsolutions.jts.util.Assert;

/**
 * @author benjamin
 *
 */
public class SpatialAveragingUtils {
	private static final Logger logger = Logger.getLogger(SpatialAveragingUtils.class);
	
	double xMin;
	double xMax;
	double yMin;
	double yMax;
	int noOfXbins;
	int noOfYbins;
	
	double smoothinRadiusSquared_m;
	double area_in_smoothing_circle_sqkm;
	Collection<SimpleFeature> featuresInVisBoundary;
	CoordinateReferenceSystem targetCRS;
	
	public SpatialAveragingUtils(double xMin, double xMax, double yMin,	double yMax, int noOfXbins, int noOfYbins, double smoothingRadius_m, String visBoundaryShapeFile, CoordinateReferenceSystem targetCRS) {
		this.xMin = xMin;
		this.xMax = xMax;
		this.yMin = yMin;
		this.yMax = yMax;
		this.noOfXbins = noOfXbins;
		this.noOfYbins = noOfYbins;
		
		this.smoothinRadiusSquared_m = smoothingRadius_m * smoothingRadius_m;
		this.area_in_smoothing_circle_sqkm = (Math.PI * smoothingRadius_m * smoothingRadius_m) / (1000. * 1000.);
		this.featuresInVisBoundary = ShapeFileReader.getAllFeatures(visBoundaryShapeFile);
		this.targetCRS = targetCRS;
	}
	
	public double calculateWeightOfPointForCell(double x1, double y1, double x2, double y2) {
		double distanceSquared = (x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1);
		return Math.exp((-distanceSquared) / (smoothinRadiusSquared_m));
	}
	
	public double[][] normalizeArray(double[][] array) {
		double [][] normalizedArray = new double[noOfXbins][noOfYbins];
		for(int xIndex = 0; xIndex<noOfXbins; xIndex++){
			for(int yIndex = 0; yIndex<noOfYbins; yIndex++){
				normalizedArray[xIndex][yIndex] = array[xIndex][yIndex] / this.area_in_smoothing_circle_sqkm;
			}
		}
		return normalizedArray;
	}
	
	private double findBinCenterY(int yIndex) {
		double yBinCenter = yMin + ((yIndex + .5) / noOfYbins) * (yMax - yMin);
		Assert.equals(mapYCoordToBin(yBinCenter), yIndex);
		return yBinCenter ;
	}

	private double findBinCenterX(int xIndex) {
		double xBinCenter = xMin + ((xIndex + .5) / noOfXbins) * (xMax - xMin);
		Assert.equals(mapXCoordToBin(xBinCenter), xIndex);
		return xBinCenter ;
	}

	public Coord findCellCentroid(int xIndex, int yIndex) {
		double xCentroid = findBinCenterX(xIndex);
		double yCentroid = findBinCenterY(yIndex);
		Coord cellCentroid = new CoordImpl(xCentroid, yCentroid);
		return cellCentroid;
	}

	private Integer mapYCoordToBin(double yCoord) {
		if (yCoord <= yMin || yCoord >= yMax) return null; // yCoord is not in area of interest
		double relativePositionY = ((yCoord - yMin) / (yMax - yMin) * noOfYbins); // gives the relative position along the y-range
		return (int) relativePositionY; // returns the number of the bin [0..n-1]
	}

	private Integer mapXCoordToBin(double xCoord) {
		if (xCoord <= xMin || xCoord >= xMax) return null; // xCorrd is not in area of interest
		double relativePositionX = ((xCoord - xMin) / (xMax - xMin) * noOfXbins); // gives the relative position along the x-range
		return (int) relativePositionX; // returns the number of the bin [0..n-1]
	}	
	
	public boolean isInResearchArea(Coord coord) {
		Double xCoord = coord.getX();
		Double yCoord = coord.getY();
		
		if(xCoord > xMin && xCoord < xMax){
			if(yCoord > yMin && yCoord < yMax){
				return true;
			}
		}
		return false;
	}

	public int getXbin(double x) {
		return this.mapXCoordToBin(x);
	}
	
	public int getYbin(double y){
		return this.mapYCoordToBin(y);
	}
}
