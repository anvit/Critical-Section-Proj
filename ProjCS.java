import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import com.sun.nio.sctp.*;
import java.nio.*;

public class ProjCS extends Thread
{
	public static final int MESSAGE_SIZE = 100;
	static int nodes = 0;
	static int self_label = 0;
	static ProjCS obj = null;
	static ArrayList<String> hosts = new ArrayList<String>();
	static ArrayList<String> ports = new ArrayList<String>();
	static ArrayList<String> paths = new ArrayList<String>();
	
	//               ,,    ,,                           
	//   .g8"""bgd `7MM    db                     mm    
	// .dP'     `M   MM                           MM    
	// dM'       `   MM  `7MM  .gP"Ya `7MMpMMMb.mmMMmm  
	// MM            MM    MM ,M'   Yb  MM    MM  MM    
	// MM.           MM    MM 8M""""""  MM    MM  MM    
	// `Mb.     ,'   MM    MM YM.    ,  MM    MM  MM    
	//   `"bmmmd'  .JMML..JMML.`Mbmmd'.JMML  JMML.`Mbmo

	public void client(int port,String host,String message)
	{
		ByteBuffer byteBuffer = ByteBuffer.allocate(MESSAGE_SIZE);
		boolean scanning=true;
		SocketAddress socketAddress = null;
		SctpChannel sctpChannel = null;
		Random randomGenerator = new Random();
		while(scanning)
		{
			try
			{
				socketAddress = new InetSocketAddress(host,port);
				sctpChannel = SctpChannel.open();
				sctpChannel.bind(new InetSocketAddress(randomGenerator.nextInt(10000)));
				sctpChannel.connect(socketAddress);
				scanning = false;
			}
			catch(ConnectException e)	//Possible existing connection
			{
				try
				{
					Thread.sleep(2000);//2 seconds
				}
				catch(InterruptedException ie){
					ie.printStackTrace();
				}
			}
			catch(SocketException e)	//For checking if port is being used by existing services
			{
				try
				{
					System.out.println("Port in use, retrying...");
					Thread.sleep(2000);//2 seconds
				}
				catch(InterruptedException ie){
					ie.printStackTrace();
				}
			}
			catch(UnknownHostException ex)	//Server isn't up yet
			{
				try
				{
					Thread.sleep(2000);//2 seconds
				}
				catch(InterruptedException ie){
					ie.printStackTrace();
				}
			}
			catch(IOException ex)
			{
				ex.printStackTrace();
			}
		}

		try
		{			
			MessageInfo messageInfo = MessageInfo.createOutgoing(null,0);
			byteBuffer.put(message.getBytes());
			byteBuffer.flip();
			sctpChannel.send(byteBuffer,messageInfo);
		}
		catch(IOException ex)
		{
			ex.printStackTrace();
		}
	}


	//  .M"""bgd                                             
	// ,MI    "Y                                             
	// `MMb.      .gP"Ya `7Mb,od8 `7M'   `MF'.gP"Ya `7Mb,od8 
	//   `YMMNq. ,M'   Yb  MM' "'   VA   ,V ,M'   Yb  MM' "' 
	// .     `MM 8M""""""  MM        VA ,V  8M""""""  MM     
	// Mb     dM YM.    ,  MM         VVV   YM.    ,  MM     
	// P"Ybmmd"   `Mbmmd'.JMML.        W     `Mbmmd'.JMML.   
                                                      
	public void srvr(int port)
	{
		try
		{
			SctpServerChannel sctpServerChannel = SctpServerChannel.open();
			InetSocketAddress serverAddr = new InetSocketAddress(port);
			sctpServerChannel.bind(serverAddr);
			System.out.println("Server started at - " + port);
			while(true)
			{
				ByteBuffer byteBuffer = ByteBuffer.allocate(MESSAGE_SIZE);
				SctpChannel sctpChannel = sctpServerChannel.accept();
				MessageInfo messageInfo = sctpChannel.receive(byteBuffer,null,null);
				String message = byteToString(byteBuffer);
				System.out.println("Label value received:" + readlabel(message));
				int addlabel = readlabel(message) + self_label;
				final String new_mesg = strip_path(message) + "-" + addlabel ;
				if(!newpath(message).split(" ")[0].equals("*"))		//Checks if path destination is '*'. '*' signifies that all nodes have been visited and we're back in the originating process
				{
					int send_proc_no = Integer.parseInt(newpath(message).split(" ")[0]) - 1 ; 
					final String send_port = ports.get(send_proc_no) ;
					final String send_host = hosts.get(send_proc_no) ;

					System.out.println("Sending - "+send_host+":"+send_port + "> "+ new_mesg);

					Thread thread1 = new Thread()
					{
						public void run()
						{
							obj.client(Integer.parseInt(send_port),send_host,new_mesg);
						}
					};
					thread1.start();
				}
				else
				{
					System.out.println(".-----------------------.");
					System.out.println("|Final Value : " + addlabel + "	|");
					System.out.println("'-----------------------'");
				}
			}
		}
		catch(IOException ex)
		{
			ex.printStackTrace();
		}
	}

	//Converts byte buffer to string
	public String byteToString(ByteBuffer byteBuffer)
	{
		byteBuffer.position(0);
		byteBuffer.limit(MESSAGE_SIZE);
		byte[] bufArr = new byte[byteBuffer.remaining()];
		byteBuffer.get(bufArr);
		return new String(bufArr);
	}

	//Seperates the path from the message received on the server
	public String newpath(String mesg)
	{
		String old_path = mesg.split("-")[0];
		String[] parts = old_path.split(" ");
		String path =  "" ;
		for (int i=0; i<parts.length ; ++i )
		{
			path = path + parts[i] + " ";
		}
		if (path != null && path.length() > 1) {
			return path.substring(0, path.length() - 1);
		}
		else
		{
			return "*";
		}
	}

	//Strips the first node from the path in message received on the server
	public static String strip_path(String mesg)
	{
		String old_path = mesg.split("-")[0];
		String[] parts = old_path.split(" ");
		String path =  "" ;
		if(!old_path.contains(" "))
		{
			return "*";
		}
		else
		{
			for (int i=1; i<parts.length ; ++i )
			{
				path = path + parts[i] + " ";
			}
			if (path != null && path.length() > 1) {
				return path.substring(0, path.length() - 1);
			}
			else
			{
				return "*";
			}
		}
	}

	//Returns labels from messages
	public int readlabel(String mesg)
	{
		return Integer.parseInt(mesg.split("-")[1].trim());
	}

	//Reads the config file and saves the hosts ports and paths in global array lists
	public void readconfig(String filename)
	{
		try
		{
			FileReader fileReader = new FileReader(filename);
			BufferedReader bufferedReader = new BufferedReader(fileReader);
			List<String> lines = new ArrayList<String>();
			String line = null;
			while ((line = bufferedReader.readLine()) != null)
			{
				lines.add(line);
			}
			bufferedReader.close();
			String[] fline = lines.toArray(new String[lines.size()]);
			nodes = Integer.parseInt(fline[0]);
			for (int i = 1; i < nodes+1; ++i)
			{
				String[] temp = fline[i].split("\t");
				hosts.add(temp[0]);
				ports.add(temp[1]);
				paths.add(temp[2]);
			}
		}
		catch(FileNotFoundException ex)
		{
			ex.printStackTrace();
		}
		catch(IOException ex)
		{
			ex.printStackTrace();
		}
		
	}

	public static void main(String args[])
	{
		Random randomGenerator = new Random();

		//Label for this process
		self_label = randomGenerator.nextInt(100);
		obj = new ProjCS();
		obj.readconfig(args[0]);
		final int proc_no = Integer.parseInt(args[1]) - 1;
		final String first_send_port = ports.get(Integer.parseInt(paths.get(proc_no).split(" ")[0]) - 1) ;
		final String first_send_host = hosts.get(Integer.parseInt(paths.get(proc_no).split(" ")[0]) - 1) ;
		System.out.println("Self label:" + self_label);
		final String mesg = strip_path(paths.get(proc_no)) + "-0";

		//Starting the main thread for this process
		Thread thread = new Thread()
		{
			public void run()
			{
				obj.srvr(Integer.parseInt(ports.get(proc_no)));
			}
		};
		thread.start();

		//Starting a client thread and sending the first message listed in path
		Thread thread1 = new Thread()
		{
			public void run()
			{
				obj.client(Integer.parseInt(first_send_port),first_send_host,mesg);
			}
		};
		thread1.start();

	}
}