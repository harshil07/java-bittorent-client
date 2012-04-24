import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.*;
/**
 * Tracker class is a used
 * to model the tracker. It has only one main function which is connect
 * the client can connect to the tracker and send a message to it.
 * Get the trackers response and do what it needs to do with it.
 * 
 * @author Harshil Shah
 * @author Tedd Noh
 */

public class Tracker {
	
	/* Trackers base url */
	private URL tracker_url;
	
	private static String tracker_host;
	private static int tracker_port;
	
	private static int tracker_interval;
	private static int tracker_incomplete;
	private static int tracker_complete;
	private static int tracker_downloaded;
	private static int tracker_min_interval;
	
	private Logger logger;
	private RUBTClient rubt;
	
	private static ArrayList<Peer> peer_list;
	
	/**
     * Key used to retrieve the tracker interval from the tracker response.
     */
	public final static ByteBuffer KEY_INTERVAL = ByteBuffer.wrap(new byte[]{ 'i', 'n', 't', 'e','r','v','a','l' });
	/**
     * Key used to retrieve the incomplete value from the tracker response.
     */
	public final static ByteBuffer KEY_INCOMPLETE = ByteBuffer.wrap(new byte[]{ 'i', 'n', 'c', 'o','m','p','l','e','t','e' });
	/**
     * Key used to retrieve the complete value from the tracker response.
     */
	public final static ByteBuffer KEY_COMPLETE = ByteBuffer.wrap(new byte[]{ 'c', 'o', 'm', 'p','l','e','t','e' });
	/**
     * Key used to retrieve the peer list from the tracker response.
     */
	public final static ByteBuffer KEY_PEERS = ByteBuffer.wrap(new byte[]{ 'p', 'e', 'e', 'r','s'});
	/**
     * Key used to retrieve the peer list from the tracker response.
     */
	public final static ByteBuffer KEY_DOWNLOADED = ByteBuffer.wrap(new byte[]{ 'd', 'o', 'w', 'n','l','o','a','d','e','d'});
	/**
     * Key used to retrieve the peer list from the tracker response.
     */
	public final static ByteBuffer KEY_MIN_INTERVAL = ByteBuffer.wrap(new byte[]{ 'm', 'i', 'n', ' ','i','n','t','e','r','v','a','l'});
	/**
     * Key used to retrieve the peer id from the tracker response.
     */
	public final static ByteBuffer KEY_PEER_ID = ByteBuffer.wrap(new byte[]{ 'p', 'e', 'e', 'r',' ','i','d'});
	/**
     * Key used to retrieve the peer port from the tracker response.
     */
	public final static ByteBuffer KEY_PEER_PORT = ByteBuffer.wrap(new byte[]{ 'p', 'o', 'r', 't'});
	/**
     * Key used to retrieve the peer ip from the tracker response.
     */
	public final static ByteBuffer KEY_PEER_IP = ByteBuffer.wrap(new byte[]{ 'i', 'p'});
	/**
     * Key used to retrieve the peer ip from the tracker response.
     */
	public final static ByteBuffer KEY_FAILURE = ByteBuffer.wrap(new byte[]{ 'f', 'a','i','l','u','r','e',' ','r','e','a','s','o','n'});
	
	
	
	Tracker(RUBTClient r, URL u)
	{
		tracker_url = u;
		logger = r.logger;
		rubt = r;
		String url = tracker_url.toString().substring(tracker_url.toString().indexOf("//")+2);
		
		String[] tokens = url.split(":");
		tracker_host = tokens[0];
		
		String socket_port = tokens[1].substring(0,tokens[1].indexOf("/"));
		tracker_port = Integer.parseInt(socket_port);
		
		peer_list = new ArrayList<Peer>();
		
	}
	
	//int getInterval(){return tracker_interval;}
	//int getDownloaded(){return tracker_downloaded;}
	//int getComplete(){return tracker_complete;}
	//int getInComplete(){return tracker_incomplete;}
	/**
	 * Retrives the peer list from the tracker
	 * @return Vector<Peer>
	 */
	public ArrayList<Peer> getPeerList(){return peer_list;}
	
	
	/**
	 * Called to send tracker a message
	 * and get a reply from the tracker as a HashMap
	 * 
	 * @param client_id clients "peer" id
	 * @param info_hash
	 * @param bytes_up Uploaded Bytes
	 * @param bytes_down Downloaded Bytes
	 * @param file_length Remaining Bytes
	 * @param event Event eg. "started"
	 * 
	 * @return HashMap
	 */
	public synchronized HashMap connect(int bytes_up, int bytes_down, int bytes_left, String event)
	{
		Socket sock = null;
		URL tracker_connect_url = null;
		HttpURLConnection tracker_connection = null;
		HashMap tracker_decoded_response = null;
		
		if(tracker_url==null)
		{
			logger.error("Tracker not initialized.");
			return null;
		}
		
		/* Creating a new socket for communication with tracker */
		try{
			sock = new Socket(tracker_host,tracker_port);
		}catch(Exception e){
			logger.error("Could not make new socket @ "+tracker_host+":"+tracker_port);
			return null;
		}
		
		/* Building the tracker connection url */
		try {
			String u = tracker_url + "?info_hash="+escape(new String(rubt.torrent_file.info_hash.array(),"ISO-8859-1"))+"&peer_id="+escape(rubt.getClientId())+"&port=6881"+"&uploaded="+bytes_up
			+"&downloaded="+bytes_down+"&left="+bytes_left;
			if(event!=null)
				u = u+"&event="+event;
			tracker_connect_url = new URL(u);
			logger.debug("Tracker Request Url = "+tracker_connect_url.toString());
		} catch (Exception e ) {
			logger.error("Could not contact Tracker!! AHHHH");
			return null;
		}
		
		
		/* Making the connection with the tracker */
		try {
			tracker_connection = (HttpURLConnection)tracker_connect_url.openConnection();
		}catch (Exception e) 
		{
			logger.error("Tracker connection Failed!! Common Man. Chop Chop Chop!!");
		}
		
		/* Getting the tracker response*/
		try {
			BufferedInputStream response_reader = new BufferedInputStream(tracker_connection.getInputStream());
			ByteArrayOutputStream temp_output = new ByteArrayOutputStream();
			byte[] buf = new byte[1];
			
			while(response_reader.read(buf) != -1)
				temp_output.write(buf);
		
			byte[] tracker_response = temp_output.toByteArray();
			logger.debug("Tracker Response: "+new String(tracker_response));
			
			/* Decoding tracker response... */
			tracker_decoded_response =  (HashMap)Bencoder2.decode(tracker_response);
			if(tracker_decoded_response.get(KEY_FAILURE)!=null)
			{
				logger.error("Tracker Connection failed. Reason: "+new String(((ByteBuffer)tracker_decoded_response.get(KEY_FAILURE)).array()));
				return null;
			}
			logger.debug("Tracker Response Decoded. YIPEEE!");
			
			/* Get data from the tracker response's HashMap */
			tracker_interval = ((Integer)tracker_decoded_response.get(KEY_INTERVAL)).intValue();
			
			/* Build peer list */
			ArrayList l = (ArrayList)tracker_decoded_response.get(KEY_PEERS);
			for(int i=0;i<l.size();i++)
			{
				HashMap peer_map = (HashMap)l.get(i);
				Peer p = new Peer(logger, rubt.getNoPieces(), rubt.getClientId(), new String(((ByteBuffer)peer_map.get(KEY_PEER_ID)).array()),new String(((ByteBuffer)peer_map.get(KEY_PEER_IP)).array()),((Integer)peer_map.get(KEY_PEER_PORT)).intValue(), rubt.torrent_file.info_hash.array());
				peer_list.add(p);
			}
			logger.debug("Peer List built.");
			
		} catch (Exception e){
			//e.printStackTrace();
			logger.error("Something happened while getting tracker response!! WHY WHY WHY??");
			return null;
		}

		
		/* Closing socket */
		if (sock != null)
		{
			try
			{
				sock.close();
			}
			catch (Exception e ) {logger.error("Could not close socket cleanly. ");}
		}
		return tracker_decoded_response;
	}
	
	/**
	 * Returns the interval the client must wait before contacting
	 * the tracker again.
	 * @return
	 */
	public int getInterval()
	{
		return tracker_interval;
	}
	
	private String escape(String s)
	{
		String result=null;
		try{
			result = URLEncoder.encode(s,"ISO-8859-1");
			logger.debug(result);
			
		}
		catch(Exception e)
		{
			logger.error("Could not escape URL");
			
		}
		return result;
	}
	
	
}
