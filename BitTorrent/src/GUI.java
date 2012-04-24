import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Timer;
import java.util.TimerTask;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;

public class GUI extends JFrame implements WindowListener{

	private RUBTClient rubt;
	private Controller c;
	private final static int WIDTH = 700;
	private final static int HEIGHT =500;
	private final static int P_WIDTH = 400;
	private final static int P_X 	 = 125;
	private final static int P_HEIGHT = 20;
	private final static int P_Y     = 60;
	private TorrentPanel torrent_panel;
	private JProgressBar jbar;
	private JTabbedPane pane;
	private InfoPanel torrent_info_panel;
	/**
	 * The Log panel
	 */
	public  LogPanel log_panel;
	private PeersPanel peers_panel;
	private double downloaded_percent;
	
	/**
	 * Key for piece downloaded event
	 */
	public final static int PIECE_DOWNLOADED = 1;
	/**
	 * Key for bytes uploaded event
	 */
	public final static int BYTES_UPLOADED	 = 2;
	/**
	 * Key for bytes downloaded event
	 */
	public final static int BYTES_DOWNLOADED = 3;
	/**
	 * Key for new peer connection established event
	 */
	public final static int NEW_PEER         = 4;
	
	GUI(RUBTClient rubt, Controller c)
	{
		this.rubt = rubt;
		this.c = c;
		jbar = new JProgressBar(0,100);
		downloaded_percent = Math.min(100,((double)rubt.noPiecesCompleted()/(double)rubt.getNoPieces())*100);
		torrent_panel = new TorrentPanel();
		pane = new JTabbedPane();
		torrent_info_panel = new InfoPanel();
		log_panel = new LogPanel();
		peers_panel = new PeersPanel();
		init();
	}
	
	private void init()
	{
		this.setTitle("RUBTClient");
		this.setIconImage((new ImageIcon("logo.gif")).getImage());
		this.setSize(new Dimension(GUI.WIDTH,GUI.HEIGHT));
		this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		this.addWindowListener(this);
		this.setLocationRelativeTo(null);
		
		this.add(torrent_panel,BorderLayout.NORTH);
		
		displayTabs();
		
		this.setVisible(true);
	}
	
	private void displayTabs()
	{
		this.add(pane);
				
		pane.addTab("General",new ImageIcon("info.gif"),torrent_info_panel);
		pane.addTab("Peers",new ImageIcon("peers.gif"),peers_panel);
		pane.addTab("Logger",new ImageIcon("log.gif"), log_panel);
	}
	
	public class InfoPanel extends JPanel
	{
		JFormattedTextField pieces, down_speed,avg_down_speed,up_speed,avg_up_speed,num_active_peers;
		
		protected final byte[] Hexhars = {

			'0', '1', '2', '3', '4', '5',
			'6', '7', '8', '9', 'a', 'b',
			'c', 'd', 'e', 'f'
			};
		InfoPanel()
		{
			super();
			init();
		}
		
		private void init()
		{
			this.setLayout(new GridLayout(10,2));
			JFormattedTextField info_hash= new JFormattedTextField("");
			byte[] array = rubt.torrent_file.info_hash.array();
			StringBuilder s = new StringBuilder();
			for(int i=0;i<array.length;i++)
			{
				if(i%4==0)s.append(" ");
				//s.append(Integer.toHexString(array[i]));
				int v = array[i] & 0xff;

				s.append((char)Hexhars[v >> 4]);
				s.append((char)Hexhars[v & 0xf]);
			}
			info_hash = new JFormattedTextField("Hash: "+s.toString().toUpperCase());
			pieces = new JFormattedTextField(new DecimalFormat("Pieces: " +rubt.torrent_file.piece_hashes.length + " x " + rubt.torrent_file.piece_length/(1000)+" KB (Have: #)"));
			JFormattedTextField path = new JFormattedTextField("Save As: "+rubt.f.getAbsolutePath());
			down_speed = new JFormattedTextField(new DecimalFormat("Download Speed: #.## kB/s "));
			avg_down_speed = new JFormattedTextField(new DecimalFormat("Average Download Speed: #.## kB/s"));
			up_speed = new JFormattedTextField(new DecimalFormat("Upload Speed: #.## kB/s"));
			avg_up_speed = new JFormattedTextField(new DecimalFormat("Average Upload Speed: #.## kB/s"));
			num_active_peers = new JFormattedTextField(new DecimalFormat("Active (Unchoked) Peers: #"));
			path.setEditable(false);
			down_speed.setEditable(false);
			up_speed.setEditable(false);
			info_hash.setEditable(false);
			avg_up_speed.setEditable(false);
			avg_down_speed.setEditable(false);
			pieces.setEditable(false);
			num_active_peers.setEditable(false);
			Font f = new Font("sansserif",Font.BOLD,12);
			up_speed.setFont(f);
			down_speed.setFont(f);
			path.setFont(f);
			pieces.setFont(f);
			info_hash.setFont(f);
			avg_up_speed.setFont(f);
			avg_down_speed.setFont(f);
			num_active_peers.setFont(f);
			
			//info_hash.setBackground(pane.getBackground());
			//pieces.setBackground(pane.getBackground());
			
			this.add(info_hash);
			this.add(pieces);
			this.add(path);
			this.add(down_speed);
			this.add(avg_down_speed);
			this.add(up_speed);
			this.add(avg_up_speed);
			this.add(num_active_peers);
			
		}
		public void paintComponent(Graphics g)
		{
			update(g);
		}
		public void update(Graphics g)
		{
			pieces.setValue(new Double(rubt.noPiecesCompleted()));
			down_speed.setValue(new Double(c.down_rate));
			up_speed.setValue(new Double(c.up_rate));
			avg_down_speed.setValue(new Double(c.avg_down_rate));
			avg_up_speed.setValue(new Double(c.avg_up_rate));
			num_active_peers.setValue(new Double(c.getUnchokeCount()));
		}
		
	}
	
	public class TorrentPanel extends JPanel
	{
		JFormattedTextField torrent_name;
		JFormattedTextField torrent_size;
		JFormattedTextField down_speed;
		JFormattedTextField up_speed;
		JFormattedTextField upd;
		JFormattedTextField r;	
		
		TorrentPanel (){
			this.setSize(new Dimension(GUI.WIDTH,100));
			GridLayout gl = new GridLayout(0,7);
			this.setLayout(gl);
			
			JLabel name = new JLabel(" Name ");
			JLabel size = new JLabel(" Size ");
			JLabel done = new JLabel(" Done ");
			JLabel down = new JLabel(" Down Speed ");
			JLabel up =   new JLabel(" Up Speed ");
			JLabel uploaded = new JLabel(" Uploaded ");
			JLabel ratio = new JLabel(" Ratio ");
			JLabel down_label = new JLabel("Downloaded: ");
			
			this.add(name);
			this.add(size);
			this.add(done);
			this.add(down);
			this.add(up);
			this.add(uploaded);
			this.add(ratio);
			
			Font f = new Font("SansSerif",Font.PLAIN,12);
			torrent_name = new JFormattedTextField(rubt.input_torrent_name);
			torrent_name.setFont(f);
			torrent_name.setEditable(false);
			torrent_size = new JFormattedTextField(new DecimalFormat("#.# MB"));
			torrent_size.setValue(new Double((double)rubt.torrent_file.file_length)/(1000*1000));
			torrent_size.setFont(f);
			torrent_size.setEditable(false);
			
			downloaded_percent = Math.min(100,((double)rubt.noPiecesCompleted()/(double)rubt.getNoPieces())*100);
			jbar.setValue((int)downloaded_percent);
			jbar.setStringPainted(true);
			
			down_speed = new JFormattedTextField(new DecimalFormat("#.## kB/s"));
			down_speed.setEditable(false);
			up_speed   = new JFormattedTextField(new DecimalFormat("#.## kB/s"));
			up_speed.setEditable(false);
			upd		   = new JFormattedTextField(new DecimalFormat("#.## KB"));
			upd.setEditable(false);
			r		   = new JFormattedTextField(new DecimalFormat("#.###"));
			r.setEditable(false);
			down_speed.setFont(f);
			up_speed.setFont(f);
			upd.setFont(f);
			r.setFont(f);
			
			
			this.add(torrent_name);
			this.add(torrent_size);
			this.add(jbar);
			this.add(down_speed);
			this.add(up_speed);
			this.add(upd);
			this.add(r);
			
			update();
			
			for(int i=0;i<7;i++)
				this.add(new JLabel(""));
			
			this.add(down_label);
			
			for(int i=0;i<7;i++)
				this.add(new JLabel(""));
				
		}
		
		public void paintComponent(Graphics g)
		{
			update(g);
		}
		public void update(Graphics g)
		{
			Graphics2D g2 = (Graphics2D) g;
			
			//this.setBackground(Color.WHITE);
			g2.setColor(Color.BLACK);
			g2.drawRect(P_X,P_Y,P_WIDTH,P_HEIGHT);
			double piece_width = (double)P_WIDTH/rubt.torrent_file.piece_hashes.length;
			g2.setColor(Color.BLUE);
			
			
			BitSet bs = rubt.getCompletedBitSet();
			for(int i=0;i<bs.size();i++)
				if(bs.get(i))
					g2.fill(new Rectangle2D.Double(P_X + piece_width*i, P_Y,piece_width, P_HEIGHT));
		}
		
		public void drawPiece(Graphics g,int piece)
		{
			Graphics2D g2 = (Graphics2D) g;
			double piece_width = (double)P_WIDTH/rubt.torrent_file.piece_hashes.length;
			g2.setColor(Color.BLUE);
			g2.fill(new Rectangle2D.Double(P_X + piece_width*piece, P_Y,piece_width, P_HEIGHT));
		}
		
		public void update()
		{
			down_speed.setValue(new Double(c.down_rate));
			up_speed.setValue(new Double(c.up_rate));
			upd.setValue(new Double(rubt.getUploadedBytes()/1000));
			r.setValue(new Double(((double)rubt.getUploadedBytes())/Math.max(1,rubt.getDownloadedBytes())));
		}
	}

	public class LogPanel extends JPanel
	{
		JTable table;
		private int count=0;
		LogPanel()
		{
			int width = 150;
		    
			this.setLayout(new BorderLayout());	
			
		    table = new JTable(new MyTableModel(new String[]{"Time","Log"}));
		    table.getColumnModel().getColumn(0).setResizable(false);
		    table.getColumnModel().getColumn(0).setPreferredWidth(width);
	        table.getColumnModel().getColumn(1).setPreferredWidth(GUI.WIDTH - width-5);
		    
	        this.add(table.getTableHeader(),BorderLayout.PAGE_START);
			this.add(table,BorderLayout.CENTER);
			this.add(new JScrollPane(table));
			

			//addLogEntry("hello","world");
		}
		
		void addLogEntry(String timestamp, String log)
		{
			((MyTableModel)table.getModel()).data.add(new String[]{timestamp,log});
			((MyTableModel)table.getModel()).fireTableRowsInserted(count, count);
			count++;
		}
	}
	
	public class PeersPanel extends JPanel
	{
		JTable table;
		
		PeersPanel()
		{
			String[] colNames = new String[]{"ID","IP","%","Status","Down Speed","Up Speed","Downloaded","Uploaded"};
			this.setLayout(new BorderLayout());	
			table = new JTable(new MyTableModel(colNames));
	        table.setPreferredScrollableViewportSize(new Dimension(GUI.WIDTH, 50));
	        this.add(table.getTableHeader(),BorderLayout.PAGE_START);
			this.add(table,BorderLayout.CENTER);
			this.add(new JScrollPane(table));
			//for(int i=0;i<colNames.length;i++){
			//TableColumn tm = table.getColumnModel().getColumn(i);
		    //  tm.setCellRenderer(new ColorColumnRenderer(this.getBackground(), Color.blue));
			//}
		    for(int i=0;i<c.getPeerList().size();i++)
			addPeer(c.getPeerList().get(i));			
		}
		
		void addPeer(Peer p)
		{
		    double percent = calPercent(p.bit_set);
			addPeer(p.getPeerID(),p.getPeerIP(),percent,getPeerStatus(p),p.up_rate,p.down_rate,p.getUploadedBytes(),p.getDownloadedBytes());
		}
		
		private String getPeerStatus(Peer p)
		{
			String s="";
			if(p.isClientChoking())
			{
				s += "C & ";
			}
			else
				s += "UC & ";
			
			if(p.isPeerInterested())
			{
				s += "I";
			}
			else{
				s +="UI";
			}
			
			return s;
		}
		
		private double calPercent(BitSet bs)
		{
			double percent = 0;
			double count = 0;
			for(int i=0;i<rubt.getNoPieces();i++)
				if(bs.get(i))
					count++;
			
			percent = (count/rubt.getNoPieces())*100;
			BigDecimal bd = new BigDecimal(Double.toString(percent));
		    bd = bd.setScale(2,BigDecimal.ROUND_HALF_UP);
		    return bd.doubleValue();
		}
		
		void update()
		{
			for(int i=0;i<c.getPeerList().size();i++)
				updatePeer(c.getPeerList().get(i));
		}
		
		void updatePeer(Peer p)
		{
			boolean exists = false;
			ArrayList<Object[]> list = ((MyTableModel)table.getModel()).data;
			for(int i=0;i<list.size();i++)
			{
				if(((String)list.get(i)[0]).equals(p.getPeerID()))
				{
					exists = true;
					String[] peer = (String[])list.get(i);
					peer[0] = p.getPeerID();
					peer[1] = p.getPeerIP();
					peer[2] = calPercent(p.bit_set)+"%";
					peer[3] = getPeerStatus(p);			
					peer[4] = p.up_rate+" kB/s";
					peer[5] = p.down_rate+" kB/s";
					peer[6] = p.getUploadedBytes()/1000+" KB";
					peer[7] = p.getDownloadedBytes()/1000+" KB";
					((MyTableModel)table.getModel()).fireTableRowsUpdated(i, i);
					break;
				}
			}
			if(!exists)
			addPeer(p);
			
		}
		
		void addPeer(String id,String ip,double percent,String status,double down_speed,double up_speed,double downloaded,double uploaded){
			((MyTableModel)table.getModel()).data.add(new String[]{id,ip,percent+"%",status,down_speed+" kB/s",up_speed+" kB/s",downloaded/1000+" KB",uploaded/1000+" KB"});
			((MyTableModel)table.getModel()).fireTableDataChanged();
		}
	
	}
	
	class ColorColumnRenderer extends DefaultTableCellRenderer 

	{
	   Color bkgndColor, fgndColor;
	 	
	   public ColorColumnRenderer(Color bkgnd, Color foregnd) {
	      super(); 
	      bkgndColor = bkgnd;
	      fgndColor = foregnd;
	   }
	  	
	   public Component getTableCellRendererComponent
		    (JTable table, Object value, boolean isSelected,
		     boolean hasFocus, int row, int column) 
	   {
	      Component cell = super.getTableCellRendererComponent
	         (table, value, isSelected, hasFocus, row, column);
	 
	      cell.setBackground( bkgndColor );
	      cell.setForeground( fgndColor );
	     
	      return cell;
	   }
	}
	class MyTableModel extends AbstractTableModel {
	    private String[] columnNames;
	    private ArrayList<Object[]> data = new ArrayList<Object[]>();
	
	    public MyTableModel(String[] cols)
	    {
	    	this.columnNames = cols;
	    }
	    
	    public int getColumnCount() {
	        return columnNames.length;
	    }

	    public int getRowCount() {
	        return data.size();
	    }

	    public String getColumnName(int col) {
	        return columnNames[col];
	    }

	    public Object getValueAt(int row, int col) {
	        return data.get(row)[col];
	    }

	    public Class getColumnClass(int c) {
	        return getValueAt(0, c).getClass();
	    }
	    
	    public boolean isCellEditable(int row, int col) {
	      return false;
	    }

	    public void setValueAt(Object value, int row, int col) {
	        data.get(row)[col] = value;
	        fireTableCellUpdated(row, col);
	    }
	}

	
	public static void main(String[] args)
	{
		GUI gui = new GUI(null,null);
		
		
	}

	public void update(ActionEvent e) {
		switch(e.getID())
		{
			case PIECE_DOWNLOADED:
				downloaded_percent = Math.min(100,((double)rubt.noPiecesCompleted()/(double)rubt.getNoPieces())*100);
				jbar.setValue((int)downloaded_percent);
				if(e.getActionCommand()!=null)
				{
					int piece = Integer.parseInt(e.getActionCommand());
					torrent_panel.drawPiece(torrent_panel.getGraphics(),piece);
					torrent_info_panel.update(torrent_info_panel.getGraphics());
				}
				break;
			case NEW_PEER:
				peers_panel.update();
				break;
			case BYTES_UPLOADED:
			
			case BYTES_DOWNLOADED:
				//torrent_panel.repaint();
				torrent_info_panel.update(torrent_info_panel.getGraphics());
				peers_panel.update();
				torrent_panel.update();
				break;
			default:
				break;
		}
		
	}
	
	@Override
	public void windowActivated(WindowEvent arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void windowClosed(WindowEvent arg0) {
		// TODO Auto-generated method stub
	}

	@Override
	public void windowClosing(WindowEvent arg0) {
		rubt.cleanUp();
	}

	@Override
	public void windowDeactivated(WindowEvent arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void windowDeiconified(WindowEvent arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void windowIconified(WindowEvent arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void windowOpened(WindowEvent arg0) {
		// TODO Auto-generated method stub
		
	}
}
