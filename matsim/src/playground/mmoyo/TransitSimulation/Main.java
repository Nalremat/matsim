package playground.mmoyo.TransitSimulation;

import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;

import org.matsim.api.basic.v01.Coord;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.network.LinkImpl;
import org.matsim.core.network.MatsimNetworkReader;
import org.matsim.core.network.NetworkFactory;
import org.matsim.core.network.NetworkLayer;
import org.matsim.core.network.NodeImpl;
import org.matsim.core.router.util.LeastCostPathCalculator.Path;
import org.matsim.core.utils.geometry.CoordImpl;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.transitSchedule.TransitScheduleBuilderImpl;
import org.matsim.transitSchedule.TransitScheduleReaderV1;
import org.matsim.transitSchedule.api.TransitSchedule;
import org.matsim.transitSchedule.api.TransitScheduleBuilder;
import org.xml.sax.SAXException;

import playground.mmoyo.PTRouter.PTActWriter;
import playground.mmoyo.PTRouter.PTRouter;

/**
 * This class contains the options to route with a TransitSchedule object 
 */
public class Main {
	private static final String PATH = "../shared-svn/studies/schweiz-ivtch/pt-experimental/";
	//private static final String PATH = "../shared-svn/studies/schweiz-ivtch/pt-experimental/5x5/";
	private static final String CONFIG =  PATH  + "config.xml";
	private static final String PLANFILE = PATH +  "_input_file.xml"; // "plans.xml";
	private static final String OUTPUTPLANS = PATH + "output_plans.xml";
	private static final String NETWORK = PATH + "network.xml";
	private static final String PLAINNETWORK = PATH + "plainNetwork.xml";
	private static final String LOGICNETWORK = PATH + "logicNetwork.xml";
	private static final String LOGICTRANSITSCHEDULE = PATH + "logicTransitSchedule.xml";
	private static final String TRANSITSCHEDULEFILE = PATH + "transitSchedule.xml";
	
	public static void main(String[] args) {
		NetworkLayer network= new NetworkLayer(new NetworkFactory());
		TransitScheduleBuilder builder = new TransitScheduleBuilderImpl();
		TransitSchedule transitSchedule = builder.createTransitSchedule();
		PTActWriter ptActWriter;
		NetworkLayer plainNetwork;
		
		/***************reads the transitSchedule file**********/
		new MatsimNetworkReader(network).readFile(NETWORK);
		try {
			new TransitScheduleReaderV1(transitSchedule, network).readFile(TRANSITSCHEDULEFILE);
		} catch (SAXException e){
			e.printStackTrace();
		} catch (ParserConfigurationException e) {
			e.printStackTrace();
		} catch (IOException e){
			e.printStackTrace();
		}
		/*******************************************************/
	
		LogicFactory logicFactory = new LogicFactory(transitSchedule); // Creates logic elements: logicNetwork, logicTransitSchedule, logicToPlanConverter
		
		int option =3;
		switch (option){
			case 1:    //writes logicElement files
				logicFactory.writeLogicElements(PLAINNETWORK, LOGICTRANSITSCHEDULE, LOGICNETWORK);
				break;
			
			case 2:  //searches and shows a PT path between two coordinates or nodes */  
				plainNetwork=logicFactory.getPlainNet();
				PTRouter ptRouter = logicFactory.getPTRouter();
				
				Coord coord1 = new CoordImpl(686897, 250590);
				Coord coord2 = new CoordImpl(684854, 254079);
				NodeImpl nodeA = plainNetwork.getNode("299598");
				NodeImpl nodeB = plainNetwork.getNode("8503309");
				Path path = ptRouter.findPTPath (coord1, coord2, 37075, 400);
				System.out.println(path.links.size());
				for (Link l : path.links){
					System.out.println(l.getId()+ ": " + l.getFromNode().getId() + " " + ((LinkImpl)l).getType() + " " + l.getToNode().getId() );
				}
				System.out.println(path.travelTime);
				break;
				
			case 3: //Routes a population/
				ptActWriter = new PTActWriter(transitSchedule, CONFIG, PLANFILE, OUTPUTPLANS);
				ptActWriter.findRouteForActivities();
				break;

			case 4:  //tests the TransitRouteFinder class with the population of PTActWriter class
				ptActWriter = new PTActWriter(transitSchedule, CONFIG, PLANFILE, OUTPUTPLANS);
				ptActWriter.printPTLegs(transitSchedule);
				break;

			case 5:
				plainNetwork=logicFactory.getPlainNet();
				NodeImpl node1 = plainNetwork.getNode("299598");
				NodeImpl node2 = plainNetwork.getNode("8503006");
				double distance = CoordUtils.calcDistance(node1.getCoord(), node2.getCoord());
				System.out.println(distance);
				//-->check if their nodes are joined by detTransfer links 
				break;
				
			case 6:  //simplifies a plan
				String planToSimplify = "output_plan.xml";
				String simplifiedPlan = "simplfied_plan.xml";
				ptActWriter = new PTActWriter(transitSchedule, CONFIG, PATH + planToSimplify , PATH + simplifiedPlan);
				ptActWriter.SimplifyPtLegs();
				break;
					
		}
	}
}
