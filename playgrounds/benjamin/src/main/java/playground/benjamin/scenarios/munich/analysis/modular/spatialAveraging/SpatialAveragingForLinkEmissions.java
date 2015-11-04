/* *********************************************************************** *
 * project: org.matsim.*
 * SpatialAveragingForLinkEmissions.java
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
package playground.benjamin.scenarios.munich.analysis.modular.spatialAveraging;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.SortedSet;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.MatsimConfigReader;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordImpl;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.gis.PointFeatureFactory;
import org.matsim.core.utils.gis.ShapeFileReader;
import org.matsim.core.utils.misc.Time;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import playground.benjamin.scenarios.munich.analysis.nectar.EmissionsPerLinkColdEventHandler;
import playground.benjamin.scenarios.munich.analysis.nectar.EmissionsPerLinkWarmEventHandler;
import playground.vsp.emissions.events.EmissionEventsReader;
import playground.vsp.emissions.types.ColdPollutant;
import playground.vsp.emissions.types.WarmPollutant;
import playground.vsp.emissions.utils.EmissionUtils;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.util.Assert;
//import playground.julia.interpolation.EmissionUtils;

/**
 * @author benjamin
 *
 */
public class SpatialAveragingForLinkEmissions {
	
	//TODO die r-ausgabe wieder herstellen
	//TODO aufraeumen
	
	private static final Logger logger = Logger.getLogger(SpatialAveragingForLinkEmissions.class);

	private final static String runNumber1 = "981";
	private final static String runNumber2 = "983";
	private final static String runDirectory1 = "../../runs-svn/run" + runNumber1 + "/";
	private final static String runDirectory2 = "../../runs-svn/run" + runNumber2 + "/";
	private final String netFile1 = runDirectory1 + runNumber1 + ".output_network.xml.gz";
	private final String munichShapeFile = "../../detailedEval/Net/shapeFromVISUM/urbanSuburban/cityArea.shp";
	
	private static String configFile1 = runDirectory1 + runNumber1 + ".output_config.xml.gz";
	private final static Integer lastIteration1 = getLastIteration(configFile1);
	private static String configFile2 = runDirectory1 + runNumber1 + ".output_config.xml.gz";
	private final static Integer lastIteration2 = getLastIteration(configFile2);
	private final String emissionFile1 = runDirectory1 + "ITERS/it." + lastIteration1 + "/" + runNumber1 + "." + lastIteration1 + ".emission.events.xml.gz";
	private final String emissionFile2 = runDirectory2 + "ITERS/it." + lastIteration2 + "/" + runNumber2 + "." + lastIteration2 + ".emission.events.xml.gz";

	Network network;
	Collection<SimpleFeature> featuresInMunich;
	EmissionUtils emissionUtils = new EmissionUtils();
	EmissionsPerLinkWarmEventHandler warmHandler;
	EmissionsPerLinkColdEventHandler coldHandler;
	SortedSet<String> listOfPollutants;
	double simulationEndTime;
	String outPathStub;

	Map<Double, Map<Id, Double>> time2CountsPerLink1;
	Map<Double, Map<Id, Double>> time2CountsPerLink2;

	//coordinates
	final CoordinateReferenceSystem targetCRS = MGC.getCRS("EPSG:20004");
	static double xMin = 4452550.25;
	static double xMax = 4479483.33;
	static double yMin = 5324955.00;
	static double yMax = 5345696.81;

	// define all relevant parameters
	final int noOfTimeBins = 6; //was 60 corresponds to 30 min
	final int noOfXbins = 160; //was 160
	final int noOfYbins = 120; //was 120
	final int minimumNoOfLinksInCell = 0;
	final double smoothingRadius_m = 500.; 
	final int radius = 5; //use >=maximum of noOfXbins, noOfYbins if emissions should affect all areas, 0 for only the xy-bin of the actual link  
	//TODO radius in abhaengigkeit vom smoothingRadius waehlen
	final double binsize = 5.0; //groesse des gebiets, in dem Emissionen voll gewertet werden. kann wegfallen falls auf normalverteilung umgestellt wird
	final String pollutant2analyze = WarmPollutant.NO2.toString();
	final boolean baseCaseOnly = true;
	final boolean calculateRelativeChange = false;
	final double sigma = 3.0; //besser: 2sigma = radius
	
	int counter1, counter2, counter3, counter4, counter5, counter6 =0;


	private void run() throws IOException{
		this.simulationEndTime = getEndTime(configFile1);
		this.listOfPollutants = emissionUtils.getListOfPollutants();
		Scenario scenario = loadScenario(netFile1);
		this.network = scenario.getNetwork();
		
		PointFeatureFactory factory = new PointFeatureFactory.Builder()
			.setName("EmissionPoint")
			.setCrs(this.targetCRS)
			.addAttribute("Time", String.class)
			.addAttribute("Emissions", Double.class)
			.create();
		
		this.featuresInMunich = readShape(munichShapeFile);
		Map<Double, Map<Id, Map<String, Double>>> time2EmissionMapToAnalyze;

		processEmissions(emissionFile1);
		Map<Double, Map<Id, Map<WarmPollutant, Double>>> time2warmEmissionsTotal1 = this.warmHandler.getWarmEmissionsPerLinkAndTimeInterval();
		Map<Double, Map<Id, Map<ColdPollutant, Double>>> time2coldEmissionsTotal1 = this.coldHandler.getColdEmissionsPerLinkAndTimeInterval();
		time2CountsPerLink1 = this.warmHandler.getTime2linkIdLeaveCount();

		Map<Double, Map<Id, SortedMap<String, Double>>> time2EmissionsTotal1 = sumUpEmissionsPerTimeInterval(time2warmEmissionsTotal1, time2coldEmissionsTotal1);
		Map<Double, Map<Id, SortedMap<String, Double>>> time2EmissionsTotalFilled1 = setNonCalculatedEmissions(time2EmissionsTotal1);
		Map<Double, Map<Id, Map<String, Double>>> time2EmissionsTotalFilledAndFiltered1 = filterLinks(time2EmissionsTotalFilled1);
		time2CountsPerLink1 = setNonCalculatedCountsAndFilter(time2CountsPerLink1);

		this.warmHandler.reset(0);
		this.coldHandler.reset(0);

		if(baseCaseOnly){
			time2EmissionMapToAnalyze = time2EmissionsTotalFilledAndFiltered1;
			outPathStub = runDirectory1 + runNumber1 + "." + lastIteration1;
		} else {
			processEmissions(emissionFile2);
			Map<Double, Map<Id, Map<WarmPollutant, Double>>> time2warmEmissionsTotal2 = this.warmHandler.getWarmEmissionsPerLinkAndTimeInterval();
			Map<Double, Map<Id, Map<ColdPollutant, Double>>> time2coldEmissionsTotal2 = this.coldHandler.getColdEmissionsPerLinkAndTimeInterval();
			time2CountsPerLink2 = this.warmHandler.getTime2linkIdLeaveCount();
			
			Map<Double, Map<Id, SortedMap<String, Double>>> time2EmissionsTotal2 = sumUpEmissionsPerTimeInterval(time2warmEmissionsTotal2, time2coldEmissionsTotal2);
			Map<Double, Map<Id, SortedMap<String, Double>>> time2EmissionsTotalFilled2 = setNonCalculatedEmissions(time2EmissionsTotal1);
			Map<Double, Map<Id, Map<String, Double>>> time2EmissionsTotalFilledAndFiltered2 = filterLinks(time2EmissionsTotalFilled2);
			time2CountsPerLink2 = setNonCalculatedCountsAndFilter(time2CountsPerLink2);

			if(calculateRelativeChange){
				time2EmissionMapToAnalyze = calcualateRelativeEmissionDifferences(time2EmissionsTotalFilledAndFiltered1, time2EmissionsTotalFilledAndFiltered2);
				outPathStub = runDirectory1 + runNumber2 + "." + lastIteration2 + "-" + runNumber1 + "." + lastIteration1 + ".relativeDelta";
			} else {
				time2EmissionMapToAnalyze = calcualateAbsoluteEmissionDifferences(time2EmissionsTotalFilledAndFiltered1, time2EmissionsTotalFilledAndFiltered2);;
				outPathStub = runDirectory1 + runNumber2 + "." + lastIteration2 + "-" + runNumber1 + "." + lastIteration1 + ".absoluteDelta";
			}
		}

// 		EmissionWriter eWriter = new EmissionWriter();
//		BufferedWriter writer = IOUtils.getBufferedWriter(outPathStub + "." + pollutant + ".smoothed.txt");
//		writer.append("xCentroid\tyCentroid\t" + pollutant + "\tTIME\n");

		Collection<SimpleFeature> features = new ArrayList<SimpleFeature>(); //TODO featerzeug geloescht, neu schreiebn

		double[][] sumOfweightedValuesForCell = new double[noOfXbins][noOfYbins];
		
		logger.info("Start mapping emissions");
		
		for(double endOfTimeInterval : time2EmissionMapToAnalyze.keySet()){
			Map<Id, Map<String, Double>> emissionMapToAnalyze = time2EmissionMapToAnalyze.get(endOfTimeInterval);
			// String outFile = outPathStub + (int) endOfTimeInterval + ".txt";
			// eWriter.writeLinkLocation2Emissions(listOfPollutants, deltaEmissionsTotal, network, outFile);

			int[][] noOfLinksInCell = new int[noOfXbins][noOfYbins];
			sumOfweightedValuesForCell = new double[noOfXbins][noOfYbins];
			double[][] sumOfOriginateValuesForCell = new double [noOfXbins][noOfYbins];

			//1. in den jeweiligen Kaestchen entstehende Emissionen berechnen
			//TODO grosse Links dabei aufteilen
			//2. Emissionen auf die Nachbarn (und sich selbst) verteilen - abhaengig von Entfernung und gewaehlter Varianz
			//- dabei eventuell skalieren
			
			//1.
			for(Link link: network.getLinks().values()){
				Id linkId = link.getId();
				Coord linkCoord = link.getCoord();
				double xLink = linkCoord.getX();
				double yLink = linkCoord.getY();
				
				Integer xbin = mapXCoordToBin(xLink);
				Integer ybin = mapYCoordToBin(yLink);
				if ( xbin != null && ybin != null ){

					noOfLinksInCell[xbin][ybin] ++;					
					//den Wert auslesen
					double value = emissionMapToAnalyze.get(linkId).get(pollutant2analyze);
					//den wert in das richtige kaestchen schreiben
					sumOfOriginateValuesForCell[xbin][ybin]=sumOfOriginateValuesForCell[xbin][ybin]+value;
					counter4++;
					//counter5+=
				}
			
			}	
			double sumOverAll=0.0;
			for(int i=0; i<sumOfOriginateValuesForCell.length;i++){
				for(int j=0; j<sumOfOriginateValuesForCell[i].length;j++)
					sumOverAll=sumOverAll+ sumOfOriginateValuesForCell[i][j];
			}
		
			
			logger.info("2. for");
				//2. 
			//jedes Kaestchen der sumOfOrigi durchgehen und aufteilen, in sumOfWeightedValues speichern
			
			for (int i= 0; i< sumOfOriginateValuesForCell.length; i++){
				for (int j=0; j< sumOfOriginateValuesForCell[i].length; j++){
					
					//alle nachbarn des aktuellen kaestchens i,j
					int xlimitBottom= Math.max(i-radius, 0);
					int xlimitTop= Math.min(i+radius, noOfXbins-1); //TODO -1 wegen Arraygroesse noetig??
					int ylimitBottom= Math.max(i-radius, 0);
					int ylimitTop= Math.min(i+radius, noOfYbins-1); //TODO -1 wegen Arraygroesse noetig?? 
					for(int neighborX = xlimitBottom; neighborX < xlimitTop; neighborX++){
						for (int neighborY = ylimitBottom; neighborY < ylimitTop; neighborY++){
							//den effektiven belastungswert berechnen 
							//der factor entspricht einer diskretisierten normalverteilung
							//System.out.println(factor(neighborX, neighborY, radius)+"factor"+sumOfOriginateValuesForCell[i][j]);
							//sumOfweightedValuesForCell[neighborX][neighborY]+=10000*factor(neighborX, neighborY, radius)*sumOfOriginateValuesForCell[i][j];
							sumOfweightedValuesForCell[neighborX][neighborY]+=factor(neighborX, neighborY, radius)*sumOfOriginateValuesForCell[i][j];
						}
					}
				}
			} //Ende 2.
			String outputPathForR = new String(outPathStub + ".Routput"+pollutant2analyze.toString()+"."+endOfTimeInterval+".txt");
			
			//TODO rauskriegen, was in der sumOfWeighted Valued drin steht
			for(double[] eintragarray : sumOfOriginateValuesForCell){
				for(double eintrag : eintragarray){
					if (eintrag < 1.0) counter2++;
					else{
						if(eintrag > 10000)counter3++;
					}
				}
			}
			
			writeRoutput(sumOfweightedValuesForCell, outputPathForR);
			
			logger.info("done with time bin "+endOfTimeInterval+" "+sumOfOriginateValuesForCell[60][70]);
			logger.info(sumOverAll);
			
		}//Ende der Schleife Zeitintervall

		//TODO momentan fuer jedes Zeitintervall, passende Ifabfrage o ae
//		String outputPathForR = new String(outPathStub + ".Routput.txt");
//		writeRoutput(sumOfweightedValuesForCell, outputPathForR);
//		writer.close();
//		logger.info("Finished writing output to " + outPathStub + "." + pollutant2analyze + ".smoothed.txt");

		//TODO beide folgezeilen wieder ausschalten
//		ShapeFileWriter.writeGeometries(features, outPathStub +  "." + pollutant2analyze + "perKmSquare.movie.emissionsPerLinkSmoothed.shp");
//		logger.info("Finished writing output to " + outPathStub +  "." + pollutant2analyze + ".perKmSquare.movie.emissionsPerLinkSmoothed.shp");
//		ShapeFileWriter.writeGeometries(features, outPathStub +  "." + pollutant2analyze + ".movie.emissionsPerLinkSmoothed.shp");
//		logger.info("Finished writing output to " + outPathStub +  "." + pollutant2analyze + ".movie.emissionsPerLinkSmoothed.shp");
		logger.info("Conterstaende 1,2,3,4,5,6: "+counter1+","+counter2+","+counter3+","+counter4+","+counter5+","+counter6);

		
	}

	private double factor(int neighborX, int neighborY, int radius2) {
		// TODO ueberpruefen
		//TODO und es muss nicht jedes mal berechnet werden
		//1/ sigma * sqrt(2 pi)
		//return 0.4; //TODO rausnehmen, funktioniert nur fuer radius 5, wenn ueberhaupt
		double prefactor = (1/sigma)/(Math.sqrt(2*Math.PI));
		//exponentialterm e^(-1/2*(Abstand/sigma)^2)
		double abstand = Math.sqrt(neighborX*neighborX+neighborY*neighborY);
		double expo = Math.pow(Math.E, Math.pow(-0.5*(abstand/sigma),2));
		double fac = prefactor*expo;
		//System.out.println("fac"+fac+"expo"+expo+"abstand"+abstand+"pre"+prefactor);
		return fac;
	}

	private void writeRoutput(double[][] sumOfweightedValuesForCell,
			String outputPathForR) {
		
		try {

			
			BufferedWriter buffW = new BufferedWriter(new FileWriter(outputPathForR));
			String valueString = new String();
			valueString="\t";
			
			//step size between coordinates
			double xDist=(xMax-xMin)/noOfXbins;
			double yDist=(yMax-yMin)/noOfYbins;
			
			//first line containing coordinates
			for(int i=0; i<sumOfweightedValuesForCell.length;i++){
				valueString= valueString+Double.toString(yMin+i*yDist)+"\t";
			}
			buffW.write(valueString);
			buffW.newLine();
			valueString="";
			
			//array[160][120]
			//outputdatei mit 160 zeilen
			for(int i = 0; i< sumOfweightedValuesForCell[0].length; i++){
				//coordinates as header
				valueString=valueString+Double.toString(xMin+i*xDist)+"\t";
				
				//table contents
				for(int j=0; j<sumOfweightedValuesForCell.length; j++){ 
					try {
						if (sumOfweightedValuesForCell[i][j]<1.0)counter1++;
						String actString=Double.toString(sumOfweightedValuesForCell[i][j]);
						if (actString.contains("E"))actString="1.9"; //TODO catch this exception
						if (actString.contains("N"))actString="0.0";
						valueString= valueString+actString+"\t"; 
					} catch (Exception e) {
						//if the array was not initialized at [i][j] use 0.0
						valueString=valueString+"0.0"+"\t";
						//alternative, TODO check if R handles this correctly
						//valueString+="NA"+"\t";
					}
				}
				//write line + line break
				buffW.write(valueString);
				buffW.newLine();
				valueString="";
			}
		buffW.close();	
		} catch (IOException e) {
			logger.warn("Failed to write output file for R.");
		}	
		logger.info("Finished writing output for R to " + outputPathForR);
	}

	private boolean isInMunichShape(Coord cellCentroid) {
		boolean isInMunichShape = false;
		GeometryFactory factory = new GeometryFactory();
		Geometry geo = factory.createPoint(new Coordinate(cellCentroid.getX(), cellCentroid.getY()));
		for(SimpleFeature feature : this.featuresInMunich){
			if(((Geometry) feature.getDefaultGeometry()).contains(geo)){
				isInMunichShape = true;
				break;
			}
		}
		return isInMunichShape;
	}

	private String convertSeconds2dateTimeFormat(double endOfTimeInterval) {
		String date = "2012-04-13 ";
		String time = Time.writeTime(endOfTimeInterval, Time.TIMEFORMAT_HHMM);
		String dateTimeString = date + time;
		return dateTimeString;
	}

	private double calculateWeightOfPersonForCell(double x1, double y1, double x2, double y2) {
		double distance = Math.sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1));
		return Math.exp((-distance * distance) / (smoothingRadius_m * smoothingRadius_m));
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

	private Coord findCellCentroid(int xIndex, int yIndex) {
		double xCentroid = findBinCenterX(xIndex);
		double yCentroid = findBinCenterY(yIndex);
		Coord cellCentroid = new CoordImpl(xCentroid, yCentroid);
		return cellCentroid;
	}

	private Integer mapYCoordToBin(double yCoord) {
		if (yCoord <= yMin || yCoord >= yMax) return null; // yHome is not in area of interest
		double relativePositionY = ((yCoord - yMin) / (yMax - yMin) * noOfYbins); // gives the relative position along the y-range
		return (int) relativePositionY; // returns the number of the bin [0..n-1]
	}

	private Integer mapXCoordToBin(double xCoord) {
		if (xCoord <= xMin || xCoord >= xMax) return null; // xHome is not in area of interest
		double relativePositionX = ((xCoord - xMin) / (xMax - xMin) * noOfXbins); // gives the relative position along the x-range
		return (int) relativePositionX; // returns the number of the bin [0..n-1]
	}

	private Map<Double, Map<Id, Map<String, Double>>> calcualateAbsoluteEmissionDifferences(
			Map<Double, Map<Id, Map<String, Double>>> time2EmissionsTotal1,
			Map<Double, Map<Id, Map<String, Double>>> time2EmissionsTotal2) {

		Map<Double, Map<Id, Map<String, Double>>> time2AbsoluteDelta = new HashMap<Double, Map<Id, Map<String, Double>>>();
		for(Entry<Double, Map<Id, Map<String, Double>>> entry0 : time2EmissionsTotal1.entrySet()){
			double endOfTimeInterval = entry0.getKey();
			Map<Id, Map<String, Double>> linkId2Emissions = entry0.getValue();
			Map<Id, Map<String, Double>> absoluteDelta = new HashMap<Id, Map<String, Double>>();

			for(Entry<Id, Map<String, Double>> entry1 : linkId2Emissions.entrySet()){
				Id linkId = entry1.getKey();
				Map<String, Double> emissionDifferenceMap = new HashMap<String, Double>();
				for(String pollutant : entry1.getValue().keySet()){
					Double emissionsBefore = entry1.getValue().get(pollutant);
					Double emissionsAfter = time2EmissionsTotal2.get(endOfTimeInterval).get(linkId).get(pollutant);
					
					
					double linkLength_km = this.network.getLinks().get(linkId).getLength() / 1000.;
					
					double emissionsPerVehicleKmBefore; 
					double countBefore = this.time2CountsPerLink1.get(endOfTimeInterval).get(linkId);
					if(countBefore != 0.0){
						emissionsPerVehicleKmBefore = emissionsBefore / (countBefore * linkLength_km);
					} else {
						emissionsPerVehicleKmBefore = 0.0;
					}
					
					double emissionsPerVehicleKmAfter;
					double countAfter = this.time2CountsPerLink2.get(endOfTimeInterval).get(linkId);
					if(countAfter != 0.0){
						emissionsPerVehicleKmAfter = emissionsAfter / (countAfter * linkLength_km);
					} else {
						emissionsPerVehicleKmAfter = 0.0;
					}

					double emissionsPerVehicleKmDifference = emissionsPerVehicleKmAfter - emissionsPerVehicleKmBefore;
//					double emissionsPerVehicleKmDifference = (emissionsPerVehicleKmAfter - emissionsPerVehicleKmBefore) / emissionsPerVehicleKmBefore;
					emissionDifferenceMap.put(pollutant, emissionsPerVehicleKmDifference);
					
					
//					Double emissionDifference = emissionsAfter - emissionsBefore;
//					emissionDifferenceMap.put(pollutant, emissionDifference);
				}
				absoluteDelta.put(linkId, emissionDifferenceMap);
			}
			time2AbsoluteDelta.put(endOfTimeInterval, absoluteDelta);
		}
		return time2AbsoluteDelta;
	}

	private Map<Double, Map<Id, Map<String, Double>>> calcualateRelativeEmissionDifferences(
			Map<Double, Map<Id, Map<String, Double>>> time2EmissionsTotal1,
			Map<Double, Map<Id, Map<String, Double>>> time2EmissionsTotal2) {

		Map<Double, Map<Id, Map<String, Double>>> time2RelativeDelta = new HashMap<Double, Map<Id, Map<String, Double>>>();
		for(Entry<Double, Map<Id, Map<String, Double>>> entry0 : time2EmissionsTotal1.entrySet()){
			double endOfTimeInterval = entry0.getKey();
			Map<Id, Map<String, Double>> linkId2emissions = entry0.getValue();
			Map<Id, Map<String, Double>> relativeDelta = new HashMap<Id, Map<String, Double>>();

			for(Entry<Id, Map<String, Double>> entry1 : linkId2emissions.entrySet()){
				Id linkId = entry1.getKey();
				Map<String, Double> emissionDifferenceMap = new HashMap<String, Double>();
				for(String pollutant : entry1.getValue().keySet()){
					double emissionsBefore = entry1.getValue().get(pollutant);
					double emissionsAfter = time2EmissionsTotal2.get(endOfTimeInterval).get(linkId).get(pollutant);
					if (emissionsBefore == 0.0){ // cannot calculate relative change if "before" value is 0.0 ...
						emissionsBefore = 1.0;   // ...therefore setting "before" value to a small value.
					} else {
						// do nothing
					}
					double emissionDifferenceRatio = (emissionsAfter - emissionsBefore) / emissionsBefore;
					emissionDifferenceMap.put(pollutant, emissionDifferenceRatio);
				}
				relativeDelta.put(linkId, emissionDifferenceMap);
			}
			time2RelativeDelta.put(endOfTimeInterval, relativeDelta);
		}
		return time2RelativeDelta;
	}

	private Map<Double, Map<Id, SortedMap<String, Double>>> setNonCalculatedEmissions(Map<Double, Map<Id, SortedMap<String, Double>>> time2EmissionsTotal) {
		Map<Double, Map<Id, SortedMap<String, Double>>> time2EmissionsTotalFilled = new HashMap<Double, Map<Id, SortedMap<String, Double>>>();
		
		for(double endOfTimeInterval : time2EmissionsTotal.keySet()){
			Map<Id, SortedMap<String, Double>> emissionsTotalFilled = this.emissionUtils.setNonCalculatedEmissionsForNetwork(this.network, time2EmissionsTotal.get(endOfTimeInterval));
			time2EmissionsTotalFilled.put(endOfTimeInterval, emissionsTotalFilled);
		}
		return time2EmissionsTotalFilled;
	}

	private Map<Double, Map<Id, Map<String, Double>>> filterLinks(Map<Double, Map<Id, SortedMap<String, Double>>> time2EmissionsTotal) {
		Map<Double, Map<Id, Map<String, Double>>> time2EmissionsTotalFiltered = new HashMap<Double, Map<Id, Map<String, Double>>>();

		for(Double endOfTimeInterval : time2EmissionsTotal.keySet()){
			Map<Id, SortedMap<String, Double>> emissionsTotal = time2EmissionsTotal.get(endOfTimeInterval);
			Map<Id, Map<String, Double>> emissionsTotalFiltered = new HashMap<Id, Map<String, Double>>();

			for(Link link : network.getLinks().values()){
				Coord linkCoord = link.getCoord();
				Double xLink = linkCoord.getX();
				Double yLink = linkCoord.getY();

				if(xLink > xMin && xLink < xMax){
					if(yLink > yMin && yLink < yMax){
						emissionsTotalFiltered.put(link.getId(), emissionsTotal.get(link.getId()));
					}
				}					
			}
			time2EmissionsTotalFiltered.put(endOfTimeInterval, emissionsTotalFiltered);
		}
		return time2EmissionsTotalFiltered;
	}

	private Map<Double, Map<Id, Double>> setNonCalculatedCountsAndFilter(Map<Double, Map<Id, Double>> time2CountsPerLink) {
		Map<Double, Map<Id, Double>> time2CountsTotalFiltered = new HashMap<Double, Map<Id,Double>>();

		for(Double endOfTimeInterval : time2CountsPerLink.keySet()){
			Map<Id, Double> linkId2Count = time2CountsPerLink.get(endOfTimeInterval);
			Map<Id, Double> linkId2CountFiltered = new HashMap<Id, Double>();
			for(Link link : network.getLinks().values()){
				Coord linkCoord = link.getCoord();
				Double xLink = linkCoord.getX();
				Double yLink = linkCoord.getY();

				if(xLink > xMin && xLink < xMax){
					if(yLink > yMin && yLink < yMax){
						Id linkId = link.getId();
						if(linkId2Count.get(linkId) == null){
							linkId2CountFiltered.put(linkId, 0.);
						} else {
							linkId2CountFiltered = linkId2Count;
						}
					}
				}
			}
			time2CountsTotalFiltered.put(endOfTimeInterval, linkId2CountFiltered);
		}
		return time2CountsTotalFiltered;
	}

	private Map<Double, Map<Id, SortedMap<String, Double>>> sumUpEmissionsPerTimeInterval(
			Map<Double, Map<Id, Map<WarmPollutant, Double>>> time2warmEmissionsTotal,
			Map<Double, Map<Id, Map<ColdPollutant, Double>>> time2coldEmissionsTotal) {

		Map<Double, Map<Id, SortedMap<String, Double>>> time2totalEmissions = new HashMap<Double, Map<Id, SortedMap<String, Double>>>();

		for(double endOfTimeInterval: time2warmEmissionsTotal.keySet()){
			Map<Id, Map<WarmPollutant, Double>> warmEmissions = time2warmEmissionsTotal.get(endOfTimeInterval);
			
			Map<Id, SortedMap<String, Double>> totalEmissions = new HashMap<Id, SortedMap<String, Double>>();
			if(time2coldEmissionsTotal.get(endOfTimeInterval) == null){
				for(Id id : warmEmissions.keySet()){
					SortedMap<String, Double> warmEmissionsOfLink = emissionUtils.convertWarmPollutantMap2String(warmEmissions.get(id));
					totalEmissions.put(id, warmEmissionsOfLink);
				}
			} else {
				Map<Id, Map<ColdPollutant, Double>> coldEmissions = time2coldEmissionsTotal.get(endOfTimeInterval);
				totalEmissions = emissionUtils.sumUpEmissionsPerId(warmEmissions, coldEmissions);
			}
			time2totalEmissions.put(endOfTimeInterval, totalEmissions);
		}
		return time2totalEmissions;
	}

	private void processEmissions(String emissionFile) {
		EventsManager eventsManager = EventsUtils.createEventsManager();
		EmissionEventsReader emissionReader = new EmissionEventsReader(eventsManager);
		this.warmHandler = new EmissionsPerLinkWarmEventHandler(this.simulationEndTime, noOfTimeBins);
		this.coldHandler = new EmissionsPerLinkColdEventHandler(this.simulationEndTime, noOfTimeBins);
		eventsManager.addHandler(this.warmHandler);
		eventsManager.addHandler(this.coldHandler);
		emissionReader.parse(emissionFile);
	}

	private static Collection<SimpleFeature> readShape(String shapeFile) {
		return ShapeFileReader.getAllFeatures(shapeFile);
	}

	private Scenario loadScenario(String netFile) {
		Config config = ConfigUtils.createConfig();
		config.network().setInputFile(netFile);
		Scenario scenario = ScenarioUtils.loadScenario(config);
		return scenario;
	}

	private Double getEndTime(String configfile) {
		Config config = new Config();
		config.addCoreModules();
		MatsimConfigReader configReader = new MatsimConfigReader(config);
		configReader.readFile(configfile);
		Double endTime = config.getQSimConfigGroup().getEndTime();
		logger.info("Simulation end time is: " + endTime / 3600 + " hours.");
		logger.info("Aggregating emissions for " + (int) (endTime / 3600 / noOfTimeBins) + " hour time bins.");
		return endTime;
	}

	private static Integer getLastIteration(String configFile) {
		Config config = new Config();
		config.addCoreModules();
		MatsimConfigReader configReader = new MatsimConfigReader(config);
		configReader.readFile(configFile);
		Integer lastIteration = config.controler().getLastIteration();
		return lastIteration;
	}

	public static void main(String[] args) throws IOException{
		new SpatialAveragingForLinkEmissions().run();
	}
}