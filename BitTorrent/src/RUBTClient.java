import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.io.*;

/**
 * The main RUBTClient interface.
 * <br>It spins a Controller thread and keeps looking for user input.
 * <br>It also keeps a track of the pieces downloaded and saves the current 
 * state of the file when it closes. On boot up it restores the file state.
 * <br>The client also assigns the peerControllers which piece to download next.
 * @author Harshil Shah
 * @author Tedd Noh
 */
public class RUBTClient {

	/**
	 * Block length set to 2^16
	 */
	public final static int 	block_length = 16384;
	/**
	 * No of active (unchoked) connections maintained
	 */
	public final static int		UPLOAD_CAP   = 10;
	/**
	 * The input torrent file
	 */
	public  TorrentInfo		 	torrent_file;
	
	private Tracker 			tracker;
	
	private Controller 			controller;
	
	/**
	 * The Log
	 */
	public  Logger 				logger;

	private BitSet 				global_bit_set;
	private BitSet 				working_bit_set;
	private BitSet 				completed_bit_set;
	private int[]				piece_counter;

	private String 				output_file_name;
	/**
	 * Input torrent file name
	 */
	public  String 				input_torrent_name;
	private String 				peer_id;
	
	private FileChannel 		output_file_channel;
	private FileOutputStream	fos;	
	/**
	 * The output file
	 */
	public  File				f;
	private int 				bytes_up;
	private int 				bytes_down;
	private int 				bytes_left;
	
	/**
	 * The Graphical User Interface if set to null (command line only)
	 */
	public GUI					gui;
	
	RUBTClient(String arg0, String arg1)
	{
		try{
			logger = new Logger(this,new PrintWriter("log.txt"),Logger.LVL_DEBUG);
			
			input_torrent_name = arg0;
			output_file_name = arg1;
			
			bytes_up = 0;
			bytes_down = 0;
			
			//initial setup and retrieves the Torrent Info object
			torrent_file = new TorrentInfo(getFileBytes(new File(input_torrent_name)));
		
			bytes_left = torrent_file.file_length;
			
			global_bit_set = new BitSet(getNoPieces());
			working_bit_set = new BitSet(getNoPieces());
			completed_bit_set = new BitSet(getNoPieces());
			piece_counter  = new int[getNoPieces()];
			for(int i=0;i<piece_counter.length;i++)
				piece_counter[i] = 0;
			
			peer_id = generatePeerId();
			
			//create file channel for random access
			f = new File(output_file_name);
			fos = new FileOutputStream(f,true);
			output_file_channel = fos.getChannel();
			
			
		}
		catch(Exception e)
		{
			logger.error("Could not create Client");
			System.exit(1);
		}
	}
	
	
	public static void main(String[] args)
	{
		if(args.length!=2)
		{
			System.out.println("Usage java RUBTClient <input-torrent-file> <output-file>");
			System.exit(1);
		}
		
		RUBTClient rubt = new RUBTClient(args[0],args[1]);
		
		try{
			rubt.restore();
			
			if (rubt.torrent_file != null)
			{
				//build the tracker
				rubt.tracker = new Tracker(rubt, rubt.torrent_file.announce_url);
			}
			else
			{
				rubt.logger.error("Torrent File is invalid");
				System.exit(1);
			}
			//starts up the main Thread Controller
			rubt.controller = new Controller(rubt,rubt.tracker);
			rubt.controller.start();
			
			/*
			 * If you would like to run the program without a gui commentout the line below
			 */
			//rubt.gui = new GUI(rubt,rubt.controller);
			
		}
		catch(Exception e)
		{
			rubt.logger.error("Could not parse torrent File. Exiting...");
			e.printStackTrace();
			System.exit(1);
		}
		
		//waits for user input
		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
		while(true)
		{
			try {
				System.out.print("Enter 'q' or 'quit' to close program: ");
				String in = br.readLine();
				if(in.equals("q") || in.equals("quit"))
				{
					rubt.cleanUp();
					System.exit(1);
				}
			} catch (IOException e) {}
		}
	}
	
	private String sessionFile()
	{
		String s=input_torrent_name+"-"+output_file_name;
	    MessageDigest m;
		try {
			m = MessageDigest.getInstance("MD5");
			m.update(s.getBytes(),0,s.length());
		    return new BigInteger(1,m.digest()).toString(16) + ".dat";
		} catch (NoSuchAlgorithmException e) {
			logger.error("error while naming session file");
			return null;
		}
	}
	
	private void restore()
	{
		try {
		//reads in the completed bit set
			File state = new File(sessionFile());
			FileInputStream f = new FileInputStream(state);
			for(int i=0;i<getNoPieces();i++)
			{
				completed_bit_set.set(i,f.read()==1 ? true : false);
			}
			
			//reads in the bytes uploaded
			byte[] up_ba = new byte[4];
			f.read(up_ba);
			bytes_up = ByteBuffer.wrap(up_ba).getInt();
			
			//reads in the bytes downloaded
			byte[] down_ba = new byte[4];
			f.read(down_ba);
			bytes_down = ByteBuffer.wrap(down_ba).getInt();
			
			//reads in the bytes left to download
			byte[] left_ba = new byte[4];
			f.read(left_ba);
			bytes_left = ByteBuffer.wrap(left_ba).getInt();
			
			f.close();
		} catch (Exception e) {
			logger.error(e.getMessage());
		}
	}
	
	/**
	 * Returns the global bit set
	 * @return
	 */
	public BitSet getGlobalBitSet()
	{
		return global_bit_set;
	}
	
	/**
	 * Sets the index bit of global bit set to bool
	 * @param index
	 * @param bool
	 */
	public void setGlobalBit(int index, boolean bool)
	{
		global_bit_set.set(index,bool);
	}
	
	protected void cleanUp()
	{
		controller.close();
		
		try {
			//saves the state of the completed bit set
			File state = new File(sessionFile());
			FileOutputStream f = new FileOutputStream(state);
			for(int i=0;i<getNoPieces();i++)
			{
				f.write(completed_bit_set.get(i) ? 1 : 0);
			}
			
			//saves the no of bytes uploaded
			ByteBuffer up_bb = ByteBuffer.allocate(4);
			up_bb.putInt(bytes_up);
			f.write(up_bb.array());
			
			//saves the no of bytes downloaded
			ByteBuffer down_bb = ByteBuffer.allocate(4);
			down_bb.putInt(bytes_down);
			f.write(down_bb.array());
			
			//saves the no of bytes left to download
			bytes_left = Math.max(0, bytes_left);
			ByteBuffer left_bb = ByteBuffer.allocate(4);
			left_bb.putInt(bytes_left);
			f.write(left_bb.array());
			
			f.close();
			output_file_channel.close();
			fos.close();
		} catch (Exception e) {
			logger.error(e.getMessage());
		}
		
		System.out.println("Bytes uploaded = " + bytes_up+" (indicates the total bytes sent by the client to other peers through piece messages)");
		System.out.println("Bytes downloaded = " + bytes_down+" (indicates the bytes downloaded even the ones that might have been discarded if a piece hash wasn't verified)");
		System.out.println("Bytes left = " + bytes_left+" from initial "+torrent_file.file_length);
		double downloaded_percent = Math.min(100,100-((double)bytes_left/(double)torrent_file.file_length)*100);
		System.out.println("Progress="+downloaded_percent+"%");
		System.out.println("Ratio="+((double)bytes_up/(double)bytes_down));
	}
	
	/**
	 * Returns the no of pieces in the torrent file
	 * @return int
	 */
	public int getNoPieces()
	{
		return torrent_file.piece_hashes.length;
	}
	
	private byte[] getFileBytes(File file) throws IOException {
        FileInputStream is = new FileInputStream(file);
        byte[] buf = new byte[(int)file.length()];
   
        is.read(buf);
        return buf;
    }
	
	/**
	 * Returns the clients peer id
	 * @return
	 */
	public String getClientId()
	{
		return peer_id;
	}
	
	private String generatePeerId()
	{
		char[] characters = {'0','1','2','3','4','5','6','7','8','9',
	'a','b','c','d','e','f','g','h','i','j','k','l','m','n','o','p','q','r','s','t','u','v','w','x','y','z',
	'A','B','C','D','E','F','G','H','I','J','K','L','M','N','O','P','Q','R','S','T','U','V','W','X','Y','Z'};
		
		String peer_id = "";
		Random r = new Random();
		
		for(int i = 0; i < 20; i++)
		{
			peer_id += characters[r.nextInt(61)];
		}
		logger.debug("Generated Peer Id: "+peer_id);
		return peer_id;
		
	}
		
	/**
	 * Writes the bytes downloaded to the output file
	 * 
	 * @param buf
	 * @param piece_no
	 * @return
	 */
	public synchronized boolean writeBytes(ByteBuffer buf,int piece_no)
	{
		logger.debug("writing bytes for piece "+piece_no+" | "+buf.array()+" | ");
		try {
			//System.out.println(piece_no);
			buf.rewind();	//This is necessary because the position marker in the ByteBuffer may not initially be at zero
			output_file_channel.position(piece_no*torrent_file.piece_length);
			output_file_channel.write(buf);
			return true;
		} catch (IOException e) {
			logger.error("Error while writing bytes to file");
			return false;
		}
	}
	
	/**
	 * Updates the global bitset
	 * With data from the input bitset
	 * Called by the Peer
	 * 
	 * @param bit set
	 */
	public synchronized void uploadBitSet(BitSet bs)
	{
		for(int i=0;i<global_bit_set.size();i++)
		{
			if(bs.get(i))
			{
				global_bit_set.set(i,true);
				piece_counter[i]= piece_counter[i]+1;
			}
		}
	}
	
	public synchronized void updateBit(int piece_index)
	{
		global_bit_set.set(piece_index,true);
		piece_counter[piece_index] = piece_counter[piece_index]+ 1;
	}
	 
	/**
	 * Called by the downloader when a certain piece has been 
	 * completely downloaded and verified
	 * 
	 * @param piece
	 */
	public synchronized void pieceDownloaded(int piece)
	{
		if(piece>=0){
			completed_bit_set.set(piece,true);
			if(gui!=null)
			gui.update(new ActionEvent(this,GUI.PIECE_DOWNLOADED,""+piece));
			piece_counter[piece] = -1; //signifying piece completed
		}
	}
	
	/**
	 * Called by the peerController which is about to die
	 * for some reason. The method toggles the working bit for the piece
	 * it was downloading
	 * @param d
	 * @param piece_no
	 */
	public synchronized void iAmDying(PeerController d, int piece_no)
	{
		if(piece_no>=0)
		working_bit_set.set(piece_no,false);
	}
	
	/**
	 * Assigns the next piece to download using the rarest available piece
	 * 
	 * @param downloader
	 * @return int
	 */
	public synchronized int getNextPieceToDownload(PeerController d)
	{
		int piece_no= -1;
		
		if(isCompleted())
			return piece_no;
		
		if(d.peer.isPeerChoking())
			return piece_no;
		
		ArrayList<Integer> available = new ArrayList<Integer>();
		
		int min = 0;
		for(int i=0;i<piece_counter.length;i++)
		{
			if(global_bit_set.get(i) && !working_bit_set.get(i) && !completed_bit_set.get(i) && d.pieceExists(i))
				if(piece_counter[i]>0 && (piece_counter[i]<min || (piece_counter[i]>min && min==0)) )
				{
					min = piece_counter[i];
					//piece_no = i;
				}
		}
		if(min > 0){
			for(int i=0;i<piece_counter.length;i++)
			{
				if(global_bit_set.get(i) && !working_bit_set.get(i) && !completed_bit_set.get(i) && d.pieceExists(i))
					if(piece_counter[i]==min)
					{
						available.add(new Integer(i));
					}
			}
			
			Random r = new Random();
			int i = r.nextInt(available.size());
			piece_no = available.get(i).intValue();
		}
		
		if(piece_no >= 0)
		{
			working_bit_set.set(piece_no,true);
		}
		
		
		//loop all bitsets
		/*for(int i=0;i<global_bit_set.length();i++)
		{
			//if currently not being downloaded i.e not in working_bit_set && not completed
			if(global_bit_set.get(i) && !working_bit_set.get(i) && !completed_bit_set.get(i))
			{
				//and current piece in downloaders->peer->bit_set 
				if(d.pieceExists(i))
				{
					//add piece to working bit set
					working_bit_set.set(i, true);
					piece_no = i;
					break;
				}	
			}
		}*/
		logger.debug("PeerController "+d.getPCId()+" assigned piece "+piece_no);
		return piece_no;
	}
	
	/**
	 * Checks if the file has been completely downloaded
	 * @return
	 */
	public synchronized boolean isCompleted()
	{
		for(int i=0;i<getNoPieces();i++)
		{
			if(!completed_bit_set.get(i))
				return false;
		}
		return true;
	}
	
	/**
	 * Returns no of bytes uploaded so far
	 * @return int
	 */
	public int getUploadedBytes()
	{
		return bytes_up;
	}
	
	/**
	 * Returns no of bytes downloaded so far
	 * @return int
	 */
	public int getDownloadedBytes()
	{
		return bytes_down;
	}
	
	/**
	 * returns the completed bit set
	 * @return
	 */
	public BitSet getCompletedBitSet()
	{
		return completed_bit_set;
	}
	
	/**
	 * returns the file we are downloading i.e writing bytes to
	 * @return
	 */
	public String getOutputFileName()
	{
		return output_file_name;
	}
	
	/**
	 * Updates the no of bytes uploaded so far
	 * @param no_bytes
	 */
	public void uploadBytes(int no_bytes)
	{
		bytes_up+= no_bytes;
	}
	
	/**
	 * Updates the no of bytes downloaded so far
	 * @param no_bytes
	 */
	public void downloadBytes(int no_bytes)
	{
		bytes_down+= no_bytes;
	}
	
	/**
	 * Updates the no of bytes still left to download
	 * @param no_bytes
	 */
	public void leftBytes(int no_bytes)
	{
		bytes_left-=no_bytes;
	}
	
	/**
	 * Returns the no of bytes left the client has still left to download
	 * @return
	 */
	public int getBytesLeft()
	{
		return bytes_left;
	}
	
	/**
	 * Returns the no of pieces completed
	 * @return
	 */
	public int noPiecesCompleted()
	{
		int count = 0;
		for(int i=0;i<completed_bit_set.size();i++)
			if(completed_bit_set.get(i))
				count++;
		return count;
	}
}

