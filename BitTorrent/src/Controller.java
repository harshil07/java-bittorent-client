import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Calendar;
import java.util.HashMap;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Vector;

/**
 * The main class that Controls all the Threads. <br>
 * 
 * @author Harshil Shah
 * @author Tedd Noh
 */
public class Controller extends Thread{

	private Tracker 					tracker;
	private Logger 						logger;
	private TorrentInfo 				torrent_file;
	private ArrayList<Peer> 			peers;
	private ArrayList<PeerController> 	peer_controllers;
	private PriorityQueue<Peer> 		waiting_list;
	private IncomingController 			in_controller;
	private TrackerController 			tracker_controller;
	private RUBTClient 					rubt;
	private boolean 					am_alive;
	/**
	 * The rate at which the client is uploading
	 */
	public double						up_rate;
	/**
	 * The rate at which the client is downloading
	 */
	public double						down_rate;
	/**
	 * The average rate at which the client is uploading
	 */
	public double						avg_up_rate;
	/**
	 * The average rate at which the client is downloading
	 */
	public double						avg_down_rate;
	private Calendar					cal;
	private Object						waiting_list_lock;
	public Controller(RUBTClient r,Tracker t)
	{
		rubt = r;
		logger = r.logger;
		torrent_file = r.torrent_file;
		tracker = t;
		peers = new ArrayList<Peer>();
		tracker_controller = new TrackerController(r,t,this);
		peer_controllers = new ArrayList<PeerController>();
		in_controller  = new IncomingController(rubt, this);
		waiting_list = new PriorityQueue<Peer>();
		am_alive = true;
		up_rate = 0;
		down_rate = 0;
		avg_up_rate = 0;
		avg_down_rate = 0;
		cal = Calendar.getInstance();
		waiting_list_lock = new Object();
	}
	
	ArrayList<Peer> getPeerList()
	{
		return peers;
	}
	
	/**
	 * Performs the following functions.
	 * <ul>
	 * <li>Initializes
	 * <li>Starts up the tracker controller.
	 * <li>Starts up the "server" IncomingController thread.
	 * <li>Assigns peers their peer controllers.
	 * </ul>
	 */
	public void run()
	{
		logger.info("Controller has been started");
		
		try {
			
			_init_();
			
			tracker_controller.start();
			
			in_controller.start();

			long check_point = Calendar.getInstance().getTimeInMillis() + 30000;
			while(am_alive)
			{
				//Thread.yield();
				//assigns peer controllers to the peers waiting
				synchronized(waiting_list_lock){
					while(!waiting_list.isEmpty() /*&& getUnchokeCount() <= 10*/)
					{
						Peer p = waiting_list.peek();
						peers.add(p);
						
						if(rubt.gui!=null)
							rubt.gui.update(new ActionEvent(this,GUI.NEW_PEER,""));
							
						assignController(p);
						//System.out.println(waiting_list.size());
						waiting_list.remove();
					}
				}
				if (Calendar.getInstance().getTimeInMillis() >= check_point)
				{
					System.out.println("CHECKPOINT REACHED");
					
					chokeWorstPeer();
					unchokeRandomPeer();
					
					check_point += 30000;
				}
			}
			
		} catch (Exception e) {
			e.printStackTrace();
			logger.debug(e.getMessage());
			logger.debug("Exception in Controller. Closing...");
			this.close();
		}
		
	}
	
	private boolean chokeWorstPeer()
	{
		if (peer_controllers.size() == 0 )
			return false;
		
		if (!peer_controllers.get(0).isAlive())
			return false;
		
		boolean is_seeding = rubt.noPiecesCompleted() == rubt.getNoPieces();
		Peer worst = peer_controllers.get(0).peer;
		
		for (int i=1; i<peer_controllers.size(); i++)
		{
			
			if (peer_controllers.get(i).isAlive())
			{
				if (is_seeding)
				{
					if (peer_controllers.get(i).peer.avg_down_rate < worst.avg_down_rate)
						worst = peer_controllers.get(i).peer;
				} else
				{
					if (peer_controllers.get(i).peer.avg_up_rate < worst.avg_up_rate)
						worst = peer_controllers.get(i).peer;
				}
			}
		}
		
		//Chokes worst peer
		worst.choke();System.out.println(worst.getPeerID() + " has been choked.");System.out.println("up: " + worst.avg_up_rate + " down: " + worst.avg_down_rate);
		return true;
	}
	
	private boolean unchokeRandomPeer()
	{
		ArrayList<Peer> choked = getChokeArray();
		int index = (int) (Math.random() * choked.size());
		
		if (choked == null || choked.size() == 0)
			return false;
		
		choked.get(index).unchoke();System.out.println(choked.get(index).getPeerID() + " has been unchoked.");
		
		return true;
	}
	
	/**
	 * Returns the number of unchoked peer connections
	 * @return
	 */
	public int getUnchokeCount()
	{
		int count = 0;
		for (int i=0; i<peer_controllers.size(); i++)
		{
			if (peer_controllers.get(i).isAlive() && !peer_controllers.get(i).peer.isClientChoking())
				count++;
		}
		
		return count;
	}
	
	/**
	 * Returns an ArrayList of the currently choked peers
	 * @return
	 */
	public ArrayList<Peer> getChokeArray()
	{
		ArrayList<Peer> unchoked = new ArrayList<Peer>();
		
		for (int i=0; i<peer_controllers.size(); i++)
		{
			if (peer_controllers.get(i).isAlive() && peer_controllers.get(i).peer.isClientChoking())
				unchoked.add(peer_controllers.get(i).peer);
		}
		
		return unchoked;
	}
	
	/**
	 * Returns the number of peer_controller threads that are alive.
	 */
	public int getActiveCount()
	{
		int count=0;
		for(int i=0;i<peer_controllers.size();i++)
		{
			if(peer_controllers.get(i).isAlive())
				count++;
		}
		return count;
	}
	
	private void _init_() throws Exception
	{
		//connects to the tracker
		HashMap tracker_response = tracker.connect(0,0,rubt.getBytesLeft(),"started");
		if(tracker_response==null){
			logger.error("Bailing out...");
			throw new Exception("Could not initialize tracker");
		}
		//retrives the peer list from the tracker
		updatePeerList(tracker.getPeerList());
		/*
		peers = tracker.getPeerList();
		if(rubt.gui!=null)
		rubt.gui.update(new ActionEvent(this,GUI.NEW_PEER,""));
		//assigns the peers their controller
		for (int i=0; i<peers.size(); i++)
		{
			Peer p = peers.get(i);
			if(isPeerValid(p))
			{
				assignController(p);
			}
		}*/
	}
	
	private void assignController(Peer p)
	{
		//if handshake performed and confirmed
		if (p.handshake() && p.confirmHandshake())
		{
			//send bitfield message
			BitSet tmp = rubt.getCompletedBitSet();
			boolean exists = false;
			for(int i=0;i<tmp.size();i++)
				if(tmp.get(i)){
					exists = true;
					break;
				}
			if(exists)
			p.bitfield(rubt.getCompletedBitSet());
			
			//creates the peer controller and assign it the peer
			PeerController d = new PeerController(rubt,this,peer_controllers.size()+"",p,tracker, torrent_file);
			peer_controllers.add(d);
			logger.debug("PeerController "+d.getPCId()+" assigned peer "+p.getPeerID());
			d.start();
		}
	}
	
	/**
	 * Adds peer to the waiting list.
	 * @param p
	 */
	public void addPeer(Peer p)
	{
		//if peer not already assigned
		if (!doesPeerExist(p))
		{
			synchronized(waiting_list_lock){
				//adds the peer to the waiting list
				//System.out.println("Adding peer "+p.getPeerID()+" to waiting list");
				waiting_list.add(p);
			}
		}
	}
	
	private boolean isPeerValid(Peer p)
	{
		return p.getPeerIP().equals("172.16.28.27") || p.getPeerIP().equals("128.6.157.250");
	}
	
	private boolean doesPeerExist(Peer p)
	{
		//checks to see if the peer is already assigned a peer controller
		//System.out.println("Checking peer "+p.getPeerID());
		for(int i=0;i<peers.size();i++)
			if(peers.get(i).getPeerID().equals(p.getPeerID()) && peers.get(i).getPeerIP().equals(p.getPeerIP()))
			{
				//System.out.println("peer exists "+p.getPeerID());
				return true;
			}
		
		return false;
	}
	
	/**
	 * Called by the tracker controller. Updates the peer list with the new
	 * set of peers
	 * @param list
	 */
	public void updatePeerList(ArrayList<Peer> list)
	{
		for(int i=0;i<list.size();i++)
			if(isPeerValid(list.get(i)))
				addPeer(list.get(i));
	}
	
	/**
	 * Sends a have message to all the peers
	 * @param piece
	 * @param p
	 */
	public void sendHave(int piece, PeerController p)
	{
		//check to see if the download is complete.
		//if so send tracker the completed message
		if(rubt.isCompleted())
		{
			tracker_controller.sendCompleted();
		}
		
		//sends a have message to all the peers
		for(int i=0;i<peer_controllers.size();i++)
		{
			if(p.getPCId()!=peer_controllers.get(i).getPCId())
			peer_controllers.get(i).addToQueue(piece);
				//peer_controllers.get(i).peer.have(piece);
		}
	}
	
	public void onUpload()
	{
		double sum1=0;
		double sum2=0;
		int count =0;
		for(int i=0;i<peers.size();i++)
		{
			if(peers.get(i).getPeerSocket()!=null && !peers.get(i).getPeerSocket().isClosed())
			{
				count++;
				sum1+=peers.get(i).down_rate;
				sum2+=peers.get(i).avg_down_rate;				
			}
		}
		up_rate = sum1/count;
		avg_up_rate = sum2/count;
		//avg_up_rate = rubt.getDownloadedBytes()/(1000*(Calendar.getInstance().getTimeInMillis()-cal.getTimeInMillis()));
	}
	
	public void onDownload()
	{
		double sum1=0;
		double sum2=0;
		int count =0;
		for(int i=0;i<peers.size();i++)
		{
			if(!peers.get(i).getPeerSocket().isClosed())
			{
				count++;
				sum1+=peers.get(i).up_rate;
				sum2+=peers.get(i).avg_up_rate;
			}
		}
		down_rate = sum1/count;
		avg_down_rate = sum2/count;
		//avg_up_rate = rubt.getUploadedBytes()/(1000*(Calendar.getInstance().getTimeInMillis()-cal.getTimeInMillis()));
	}
	
	/**
	 * Safely closes this Controller
	 */
	public void close()
	{
		//closes the peer controllers
		for(int i=0;i<peer_controllers.size();i++)
			if(peer_controllers.get(i).isAlive())
				peer_controllers.get(i).suicide();
		
		//closes the tracker controller
		tracker_controller.suicide();
		
		//closes the server
		in_controller.suicide();
		
		//closes self
		am_alive = false;
	}

}
