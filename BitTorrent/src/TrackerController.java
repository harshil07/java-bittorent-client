import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Vector;

/**
 * Tracker Controller contacts the tracker
 * at regular intervals and updates the peer list.
 * 
 * @author Harshil Shah
 * @author Tedd Noh
 */
public class TrackerController extends Thread{

	private Tracker 	tracker;
	private Controller 	controller;
	private Logger 		logger;
	private RUBTClient 	rubt;
	
	private boolean 	am_alive;
	
	TrackerController(RUBTClient r, Tracker t,Controller c)
	{
		rubt = r;
		tracker = t;
		controller = c;
		logger = rubt.logger;
		am_alive = true;
	}
	
	public void run()
	{
		logger.debug("TrackerController has started..");
		while(am_alive)
		{
			try {
				//sleeps for the time of the interval and then contacts the tracker
				Thread.sleep(tracker.getInterval()*1000);
				if(am_alive)
				contactTracker();
			} catch (InterruptedException e) {logger.error("TrackerController Interrupted!!");}
		}
		logger.debug("TrackerController is shutting down..");
	}
	
	private void contactTracker()
	{
		HashMap tracker_response = tracker.connect(rubt.getUploadedBytes(), rubt.getDownloadedBytes(),rubt.getBytesLeft(),null);
		if(tracker_response==null){
			logger.error("Bailing out...");
			return;
		}
		//retrives the peer list from the tracker
		ArrayList<Peer> peers_list = tracker.getPeerList();
		controller.updatePeerList(peers_list);
	}
	
	/**
	 * Sends the completed message to the tracker
	 */
	public void sendCompleted()
	{
		tracker.connect(rubt.getUploadedBytes(), rubt.getDownloadedBytes(),rubt.getBytesLeft(), "completed");
	}
	
	/**
	 * Safely shutsdown the tracker controller thread
	 */
	public void suicide()
	{
		tracker.connect(rubt.getUploadedBytes(), rubt.getDownloadedBytes(),rubt.getBytesLeft(), "stopped");
		am_alive = false;
	}
}
