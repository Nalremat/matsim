package playground.mmoyo.PTRouter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.matsim.api.basic.v01.Coord;
import org.matsim.api.basic.v01.Id;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.basic.v01.IdImpl;
import org.matsim.core.network.LinkImpl;
import org.matsim.core.network.NetworkLayer;
import org.matsim.core.network.NodeImpl;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.TravelCost;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.router.util.LeastCostPathCalculator.Path;
import org.matsim.core.utils.geometry.CoordUtils;

import playground.mmoyo.TransitSimulation.LogicIntoPlainTranslator;
/** 
 * Second version of Router using Matsims Class Dijkstra  
 * We avoid the relationship with the city network and use coordinate search instead
 *  *
 * @param nodeList PTNodes in stored a a Node-List
 * @param linkList Collection of org.matsim.network.Link
 * @param OriginNode Node where the trip begins
 * @param DestinationNode Node where the trip must finish
 * @param ptLinkCostCalculator Class that contains the weight information of links
 * @param time Milliseconds after the midnight in which the trip must begin
 */
public class PTRouter{
	private NetworkLayer logicNet;
	private LeastCostPathCalculator myDijkstra;
	private TravelCost ptTravelCost;
	public TravelTime ptTravelTime;   //> make private 
	
	public PTRouter(NetworkLayer logicNet, PTTimeTable ptTimetable) {
		this.logicNet = logicNet;
		this.ptTravelCost = new PTTravelCost(ptTimetable);
		this.ptTravelTime =new PTTravelTime(ptTimetable);
		this.myDijkstra = new MyDijkstra(logicNet, ptTravelCost, ptTravelTime);	
	}
	
	public PTRouter(NetworkLayer logicNet, PTTimeTable ptTimetable, LogicIntoPlainTranslator logicToPlainConverter) {
		this.logicNet = logicNet;
		this.ptTravelCost = new PTTravelCost(ptTimetable);
		this.ptTravelTime =new PTTravelTime(ptTimetable);
		this.myDijkstra = new MyDijkstra(logicNet, ptTravelCost, ptTravelTime);		
	}
	
	public Path findPTPath(Coord coord1, Coord coord2, double time, final double distToWalk){
		double walkRange= distToWalk; 
		NodeImpl origin= createWalkingNode(new IdImpl("W1"), coord1);   //this is faster than network.createNode but uses PTNode
		NodeImpl destination= createWalkingNode(new IdImpl("W2"), coord2);
		
		Collection <NodeImpl> nearOriginStops = findnStations (coord1, walkRange);
		Collection <NodeImpl> nearDestinationStops = findnStations (coord2, walkRange);
		
		List <LinkImpl> walkLinksFromOrigin = createWalkingLinks(origin, nearOriginStops, true);
		List <LinkImpl> walkLinksToDestination = createWalkingLinks(destination, nearDestinationStops, false);
			
		Path path = myDijkstra.calcLeastCostPath(origin, destination, time); 
			
		removeWalkLinks(walkLinksFromOrigin);
		removeWalkLinks(walkLinksToDestination);
		if (path!=null){
			path.nodes.remove(origin);
			path.nodes.remove(destination);
		}
		logicNet.removeNode(origin);
		logicNet.removeNode(destination);
		
		return path;
	}

	private Collection <NodeImpl> findnStations(Coord coord, double walkRange){
		Collection <NodeImpl> stations;
		do{
			stations = logicNet.getNearestNodes(coord, walkRange);
			walkRange= walkRange + 300;
		} while (stations.size()<2);
		return stations;
	}

	/**
	 * Creates a temporary origin or destination node
	 * avoids the method net.createNode because it is not necessary to rebuild the quadtree
	 */
	public NodeImpl createWalkingNode(Id id, Coord coord) {
		NodeImpl node = new PTNode(id, coord, "Walking");
		logicNet.getNodes().put(id, node);
		return node;
	}
	
	public List <LinkImpl> createWalkingLinks(NodeImpl walkNode, Collection <NodeImpl> nearNodes, boolean to){
		//->move to link factory
		List<LinkImpl> newWalkLinks = new ArrayList<LinkImpl>();
		Id idLink;
		NodeImpl fromNode;
		NodeImpl toNode;
		int x=0;
		String type;
		for (NodeImpl node : nearNodes){
			if (to){
				fromNode= walkNode;
				toNode= node;
				idLink = new IdImpl("WLO" + x++);
				type = "Access";
			}else{
				fromNode= node;
				toNode=  walkNode;
				idLink = new IdImpl("WLD" + x++);
				type = "Walking";
			}
			
			LinkImpl link= logicNet.createLink(idLink, fromNode, toNode, CoordUtils.calcDistance(fromNode.getCoord(), toNode.getCoord()) , 1, 1, 1, "0", type);
			//-->check if this improves the performance
			//link.setFreespeed(link.getLength()* WALKING_SPEED);
			newWalkLinks.add(link);
		}
		return newWalkLinks;
	}

	public void removeWalkLinks(Collection<LinkImpl> WalkingLinkList){
		//->use link factory
		for (LinkImpl link : WalkingLinkList){
			logicNet.removeLink(link);
		}
	}
	
	public Path findRoute(Coord coord1, Coord coord2, double time){
		NodeImpl node1= logicNet.getNearestNode(coord1);
		NodeImpl node2= logicNet.getNearestNode(coord2);
		return findRoute(node1, node2,time);
	}
	
	public Path findRoute(NodeImpl ptNode1, NodeImpl ptNode2, double time){
		return myDijkstra.calcLeastCostPath(ptNode1, ptNode2, time);
	}
	
	public void printRoute(Path path){
		if (path!=null){
			System.out.print("\nLinks: ");
			//for (Link l L route.getLinks()) {
				//System.out.println("link: "l.getId() + " cost: " + link.);
			//}
		
			Id idPTLine = new IdImpl("");
			for (Node node : path.nodes){
				PTNode ptNode= (PTNode)node;
				if(ptNode.getIdPTLine()==idPTLine){
					System.out.print(ptNode.getId().toString() + " ");
				}else{
					System.out.println("\n" + ptNode.getIdPTLine().toString());
					System.out.print(ptNode.getId().toString() + " ");
				}
				idPTLine= ptNode.getIdPTLine();	
			}
			System.out.println("\nTravel cost of route=" + path.travelCost + "  time of route:" + path.travelTime);
		}else{
			System.out.println("The route is null");
		}
	}	
}



/*
 * searches nodes in walk range or else the nearest one
*/
/*
private Collection <Node> findNearStops (final Coord coord, final double walkDistance){
	Collection <Node> NearStops = net.getNearestNodes(coord, walkDistance);
	if (NearStops.size()==0){
		Node nearNode = net.getNearestNode(coord);
		if (CoordUtils.calcDistance(coord, nearNode.getCoord())< walkDistance){
			NearStops.add(nearNode);
		}
	}
	return NearStops;
}
*/


