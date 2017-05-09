import java.net.Socket;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Random;
public class UdpClient{

	//random source address
	private static int sourceAddress = 1234;
	//random source port
	private static short srcPort = 1234;
	public static void main(String[] args)throws Exception{

		try(Socket socket = new Socket("codebank.xyz", 38005)){
			//Use random object to create random bytes for datas
			Random rand = new Random();
			//Get input stream from server
			InputStream fromServer = socket.getInputStream();
			//Get output stream to server
			OutputStream toServer = socket.getOutputStream();
			//Hard coded data to send to server for handshake process
			byte[] hardCodedData = {(byte)0xDE, (byte)0xAD, (byte)0xBE, (byte)0xEF};
			//Get hand shake IPv4 packet
			byte[] ipv4Packet = getIpv4Packet(hardCodedData, socket.getInetAddress().hashCode());
			//Hold the response from server
			byte[] response = new byte[4];
			//Hold the port number send by server
			byte[] portNumber = new byte[2];
			//Sending the IPv4 packet to server
			toServer.write(ipv4Packet);
			//Reading response from server
			fromServer.read(response);
			System.out.print("Handshake response: ");
			printBytesInHex(response);
			//Reading port number send from server
			fromServer.read(portNumber);
			//Convert the 2 bytes port number to short
			short port = (short)((short)portNumber[0]<<8 | (short)portNumber[1]);
			System.out.println("Prot number recieved: " + Short.toUnsignedInt(port) + "\n");


			//Initial length of data
			int dataLength = 2;
			//loop until data size pass 4096
			while(dataLength<= 4096){

				//initialize data with random bytes
				byte[] data = new byte[dataLength];
				rand.nextBytes(data);
				//Pass in port number, data and destination address into method that returns udp header packet
				byte[] udpHeader = getUdpHeader(portNumber, data, socket.getInetAddress().hashCode());
				//get the whole packet which has the UPD packet inside the data portion of IPv4 packet
				byte[] wholePacket = getIpv4Packet(udpHeader, socket.getInetAddress().hashCode());
				//Send the whole packet to server
				System.out.println("Sending packet with " + dataLength + " bytes of data");
				//Get starting time
				long startTime = System.currentTimeMillis();
				toServer.write(wholePacket);
				//Get the response from server
				response = new byte[4];
				fromServer.read(response);
				//Get RRT. Current time - start time
				long rrt = System.currentTimeMillis() - startTime;
				System.out.print("response: ");
				//print the byte in hex
				printBytesInHex(response);
				//print RRT
				System.out.println("RRT: " + rrt + "ms\n");
				//double data Length
				dataLength *= 2;
			}
		}
	}

	public static byte[] getUdpHeader(byte[] dstPort, byte[] data, int destinationAddress){
		//Length of UPD packet. 8 bytes for header + the bytes from data
		short udpLength = (short)(8 + data.length);
		//Instantiate a byte array to to construct the UDP header
		byte[] udpHeader = new byte[udpLength];
		//Use byte buffer to add datas to udp header byte array
		ByteBuffer udpHeaderBuffer = ByteBuffer.wrap(udpHeader);
		//Put ramdon source port
		udpHeaderBuffer.putShort(srcPort);
		//Put destination port
		udpHeaderBuffer.put(dstPort);
		//Put upd length
		udpHeaderBuffer.putShort(udpLength);
		//Get UPD checksum. This includes the pesudoheader of IPv4, UDP header and data
		byte[] udpCheckSumPacket = new byte[12 + udpLength];
		ByteBuffer udpCheckSumPacketBuffer = ByteBuffer.wrap(udpCheckSumPacket);
		//Put in source address
		udpCheckSumPacketBuffer.putInt(sourceAddress);
		//Put in destination address
		udpCheckSumPacketBuffer.putInt(destinationAddress);
		//Put in 0 byte
		udpCheckSumPacketBuffer.put((byte)0);
		//Put in UDP protocol
		udpCheckSumPacketBuffer.put((byte)17);
		//Put in UDP length
		udpCheckSumPacketBuffer.putShort(udpLength);
		//Put in source port
		udpCheckSumPacketBuffer.putShort(srcPort);
		//Put in destination port
		udpCheckSumPacketBuffer.put(dstPort);
		//Put in UDP length 
		udpCheckSumPacketBuffer.putShort((short)udpLength);
		//Put in 0 as intital checksum
		udpCheckSumPacketBuffer.putShort((short)0);
		//Put in data
		udpCheckSumPacketBuffer.put(data);
		//Pass this checksum packet into checksum funtion which returns its checksum
		short cs = checkSum(udpCheckSumPacketBuffer.array(), udpCheckSumPacket.length);
		//Put in the checksum into UDP header
		udpHeaderBuffer.putShort(cs);
		//Last, append the data to the packet
		udpHeaderBuffer.put(data);
		//return the UDP packet
		return udpHeaderBuffer.array();

	}

	//return the IPv4 packet header with data
	public static byte[] getIpv4Packet(byte[] data, int destinationAddress){
		byte version = 4;//IP version 4
		byte headerLength = 5; //5 line of 32 bits header
		byte tos = 0; //do not implement
		short length= (short)(headerLength * 4 + data.length); //total length
		short ident = 0; //do not implement
		short flag = 2; //010 for no fragmentation
		short offset = 0; //do not implement
		byte ttl = 50; // assuming every packet has a TTL of 50
		byte protocal = 17; //UDP
		short checksum = 0; //0 initially
		//int sourceAddress= 1234; //random source address
		//packet array is the packet that will send to server. This will have the correct checksum
		byte[] packet = new byte[length]; 
		//The array is only the bits in header, with checksum equals zero. This is use to calculate the correct checksum.
		byte[] header = new byte[headerLength*4];
		//wrap both array to byteBuffer.
		ByteBuffer byteBuffer = ByteBuffer.wrap(packet);
		ByteBuffer forCheckSum = ByteBuffer.wrap(header);
		//shift version 4 bit left, or it with headerlength to form  the first eight bits and store in packet
		byteBuffer.put((byte)((byte)(version & 0xf) << 4 | (byte)headerLength & 0xf));
		forCheckSum.put((byte)((byte)(version & 0xf) << 4 | (byte)headerLength & 0xf));
		//put TOS to packet
		byteBuffer.put(tos);
		forCheckSum.put(tos);
		//put Total Length to packet
		byteBuffer.putShort(length);
		forCheckSum.putShort(length);
		//put Ident to packet
		byteBuffer.putShort(ident);
		forCheckSum.putShort(ident);
		// concatenate flag and offset to packet
		byteBuffer.putShort((short)((flag & 0x7) << 13 | offset & 0x1fff));
		forCheckSum.putShort((short)((flag & 0x7) << 13 | offset & 0x1fff));
		//put TTL to packet
		byteBuffer.put(ttl);
		forCheckSum.put(ttl);
		//put protocal to packet
		byteBuffer.put(protocal);
		forCheckSum.put(protocal);
		//put check sum(0) only to heaader.
		forCheckSum.putShort(checksum);
		//put source address to header.
		forCheckSum.putInt(sourceAddress);
		//put destination address to header.
		forCheckSum.putInt(destinationAddress);
		//get checksum of the header.
		checksum = checkSum(forCheckSum.array(), forCheckSum.array().length);
		//put this checksum to packet.
		byteBuffer.putShort(checksum);
		//put source address to packet
		byteBuffer.putInt(sourceAddress);
		//put destination address to
		byteBuffer.putInt(destinationAddress);
		//put data to packet
		byteBuffer.put(data);
		return byteBuffer.array();
	}
	//Return checksum in short
	public static short checkSum(byte[] message, int length) {
	    int i = 0;
	    long sum = 0;
	    while (length > 0) {
	        sum += (message[i]&0xff) << 8;
	        i++;
	        length--;
	        if ((length)==0) break;
	        sum += (message[i++]&0xff);
	        length--;
	    }
	    sum = (~((sum & 0xFFFF)+(sum >> 16)))&0xFFFF;
	    short cs = (short)(sum & 0xffff);
		return cs;
	}

	//funtion that prints the byte array into hex format string. 
	public static void printBytesInHex(byte[] bytes){
		for(int i = 0; i<bytes.length; i++){
			System.out.print(String.format("%02X", bytes[i]));
		}
		System.out.println();
	}
}
