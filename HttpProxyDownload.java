/* BeginGroupMembers */
/* f20170165@hyderabad.bits-pilani.ac.in GUMMA VARUN */
/* f20170011@hyderabad.bits-pilani.ac.in PAVAN SRIHARI DARBHA */
/* f20170248@hyderabad.bits-pilani.ac.in MUZAFFAR AHMED */
/* f20170238@hyderabad.bits-pilani.ac.in SNS MANEESH SARMA */
/* EndGroupMembers */

/* This program works similar to a web-scraper. It opens socket on a given proxy server then the opened socket is 
 * wrapped in an SSL layer. A request is made through the socket to the proxy to CONNECT to google. Once the connection
 * is established, the webpage HTML is retrieved through the socket and is stored in .html file. To retrieve the image from google
 * (or basically an webpage which has a PNG image embedded in it), the retrieved HTML file is parsed and the URL in the IMG SRC tag
 * extracted and the PNG image is read in bytes (from the starting byte containing PNG to the last byte containing IEND). Once The 
 * received bytes are stored in a .png file, the program closes all open streams and sockets and terminates normally.
 */

import java.io.*;
import java.net.*;
import java.util.*;
import javax.net.ssl.*;


class HttpProxyDownload
{
    private static void exitWithMessage(String msg)
    {
        System.out.println(msg);
        System.exit(0);
    }

    
    private static String htmlParser(String fname) throws IOException
    {
    	int val = 0;
    	String res = null, tmp = null;  	
    	BufferedReader br = new BufferedReader(new FileReader(fname));
        StringBuilder sbr = new StringBuilder("");
        StringBuilder url = new StringBuilder("");
    	while((tmp = br.readLine()) != null)
    		sbr.append(tmp);
        String corpus = sbr.toString();
        int pos = corpus.indexOf("<img");
        if(pos != -1)
        {
            int i = corpus.indexOf("src", pos);
            while(i < corpus.length() && val == 0)
            {
                if(corpus.charAt(i) == '"')
                    val = 1;
                if(corpus.charAt(i) == '\'')
                    val = 2;
                i++;
            }
            while(((val == 1 && corpus.charAt(i) != '"') || (val == 2 && corpus.charAt(i) != '\'')) && i < corpus.length())
            {
                url.append(corpus.charAt(i));
                i++;
            }

            res = url.toString();
        }
        
        br.close();
        return res;
    }


    public static String base64Encoder(String s)
    {
        StringBuilder res = new StringBuilder("");
        StringBuilder sbr = new StringBuilder("");
        String lookUpTable = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"; 
        
        for(int i = 0; i < s.length(); i++)
        {
            String tmp = Integer.toBinaryString(s.charAt(i));
            while(tmp.length() != 8)
                tmp = '0' + tmp;
            sbr.append(tmp);
        }
        
        int padding = (3 - (s.length() % 3));
        while((sbr.length() % 6) != 0)
            sbr.append('0');

        int idx = 0, splitSize = 6;
        String binRep = sbr.toString();
        while(idx < binRep.length())
        {
            String piece = binRep.substring(idx, idx+splitSize);
            res.append(lookUpTable.charAt(Integer.parseInt(piece, 2)));
            idx += splitSize;
        }
        
        for(int i = 0; i < padding; i++)
            res.append('=');
        
        return res.toString(); 
    }


    public static void main(String args[]) throws Exception
    {
        if(args.length < 7)
            HttpProxyDownload.exitWithMessage("Insufficient Arguments. Expected URL, proxy IP, proxy port, login, password, filename to save html, filename to save logo.");
        if(args.length > 7)
            HttpProxyDownload.exitWithMessage("Too many Arguments. Expected URL, proxy IP, proxy port, login, password, filename to save html, filename to save logo.");

        String url = args[0];
        String ip = args[1];
        String uname = args[3];
        String pass = args[4];
        String htmlFile = args[5];
        String logoFile = args[6];
        int port = Integer.parseInt(args[2]);

        Socket sckt = new Socket(ip, port);
        PrintWriter htmlOutputWriter = new PrintWriter(new FileWriter(htmlFile), true);
        PrintWriter pw = new PrintWriter(sckt.getOutputStream(), true);
        BufferedReader br = new BufferedReader(new InputStreamReader(sckt.getInputStream()));

        StringBuilder conn = new StringBuilder("");
        String base64EncodedCred = HttpProxyDownload.base64Encoder((uname + ":" + pass));
        
        conn.append("CONNECT " + url + ":443 HTTP/1.1\r\n");
        conn.append("Host: " + url + ":443\r\n");
        conn.append("Proxy-Connection: keep-alive\r\n");
        conn.append("Proxy-Authorization: Basic ");
        conn.append(base64EncodedCred + "\r\n\r\n");
        pw.print(conn.toString());
        pw.flush();

        System.out.println(br.readLine());
        SSLSocketFactory sfac = (SSLSocketFactory)SSLSocketFactory.getDefault();
        SSLSocket sslsckt = (SSLSocket)sfac.createSocket(sckt, null, sckt.getPort(), false);
        BufferedReader brSSL = new BufferedReader(new InputStreamReader(sslsckt.getInputStream()));
        PrintWriter pwSSL = new PrintWriter(sslsckt.getOutputStream());
        sslsckt.startHandshake();

        StringBuilder tlsHandshake = new StringBuilder("");
        StringBuilder imgRequest = new StringBuilder("");
        tlsHandshake.append("GET / HTTP/1.1\r\n");
        tlsHandshake.append("Host: " + url + ":443\r\n");
        tlsHandshake.append("Accept: text/html\r\n\r\n");
        pwSSL.print(tlsHandshake.toString());
        pwSSL.flush();

        boolean write = false;
        while(true)
        {	
        	String line = brSSL.readLine().trim();
            String line_lowercase = line.toLowerCase();
            if(!write && line_lowercase.startsWith("<!doctype html"))
                write = true;
            if(write)
                htmlOutputWriter.println(line);
            if(line.endsWith("</html>"))
                break;
        }

        InputStream inpStream = sslsckt.getInputStream();
        String imageUrl = HttpProxyDownload.htmlParser(htmlFile);
        if(imageUrl == null)
            HttpProxyDownload.exitWithMessage("No image url found! Application terminating...");
        
        FileOutputStream picWriter = new FileOutputStream(logoFile);
        imgRequest.append("GET " + imageUrl + " HTTP/1.1\r\n");
        imgRequest.append("Host: " + url + ":443\r\n");
        imgRequest.append("Content-type: image/png\r\n\r\n");
		pwSSL.print(imgRequest.toString());
		pwSSL.flush();
       
        int length = 0;
        byte[] bytes = new byte[1024];
        boolean startSeq = false, endSeq = false;
        while (!endSeq && ((length = inpStream.read(bytes)) != -1))
        {
        	if(startSeq)
        		picWriter.write(bytes, 0, length);
        	for(int i = 0; i <= length-4 && !startSeq; i++)
        		if(bytes[i+1] == 'P' && bytes[i+2] == 'N' && bytes[i+3] == 'G')
        		{
        			startSeq = true;
        			picWriter.write(bytes, i, length-i);
        		}
        	for(int i = 0; i <= length-4 && !endSeq; i++)
        		if(bytes[i] == 'I' && bytes[i+1] == 'E' && bytes[i+2] == 'N' && bytes[i+3] == 'D')
  					endSeq = true;
        }
        picWriter.flush();

        pw.close();
        br.close();
        sckt.close();      
        pwSSL.close();
        brSSL.close();
        sslsckt.close();
        picWriter.close();
        inpStream.close();
        System.out.println("Application terminating...");
    }
}