import java.net.*;
import java.nio.ByteBuffer;
import java.io.*;

/**
 * Acts as a server. Listens for incoming
 * peer connections.
 * @author Harshil Shah
 * @author Tedd Noh
 */
public class IncomingController extends Thread{

	private RUBTClient rubt;
	
	private Controller controller;
	
	private byte[] info_hash;
	
	private boolean am_alive;
	
	private Logger logger;
	
	IncomingController(RUBTClient rubt, Controller c)
	{
		this.rubt = rubt;
		controller = c;
		am_alive = true;
		info_hash = rubt.torrent_file.info_hash.array();
		logger = rubt.logger;
	}
	
	/**
	 * Performs the following functions
	 * <ul>
	 * <li>Waits for an incoming connection
	 * <li>On new connection request first checks if peer is valid
	 * <li>If valid it checks if the hash is good
	 * and adds the peer to the controllers waiting list.
	 * </ul>
	 */
	public void run()
	{
		try
		{
			//create new server socket
			ServerSocket server = new ServerSocket(6881);
			
			while (am_alive)
			{
				Thread.yield();
				//waits for incoming request
				Socket sock = server.accept();
				InetAddress ip = sock.getInetAddress();
				//checks if peer is valid
				
				if(!isValid(ip.getHostAddress()))
				{
					logger.error("Peer from invalid IP address "+ip.getHostAddress()+"trying to connect");
				}
				else{
					DataInputStream fis = new DataInputStream(sock.getInputStream());
					
					//Reads in Handshake
					byte[] response = new byte[68];
					fis.read(response);
					
					//Checks Handshake
					//Checks if info_hashs match
					boolean match = true;
					for (int y=1; y<=20; y++)
					{
						if (response[response.length - 20 - y] != info_hash[info_hash.length - y])
						{
							logger.debug("MISMATCHING INFO_HASH! SOCKET CONNECTION HAS BEEN CLOSED FOR INCOMING CONNECTION");
							sock.close();
							match = false;
							break;
						}
					}
					
					if (match)
					{
						String response_string = new String(response);
						String peer_id = response_string.substring(48);	
						
						Peer p = new Peer(logger, rubt.getNoPieces(), rubt.getClientId(), peer_id, ip.getHostAddress(), sock.getPort(), rubt.torrent_file.info_hash.array());
						p.setPeerSocket(sock);
						
						p.handshake();
						
						controller.addPeer(p);
					}
				}
			}
			
		} catch (IOException ioe)
		{
			logger.error("Error occurred during an incoming handshake!");
			run();	//Restarts IncomingController
		}
		
	}

	private boolean isValid(String ip)
	{
		return ip.equals("172.16.28.27") || ip.equals("128.6.157.250");	
	}
	
	/**
	 * Safely closes this thread.
	 */
	public void suicide()
	{
		am_alive = false;
	}
}
